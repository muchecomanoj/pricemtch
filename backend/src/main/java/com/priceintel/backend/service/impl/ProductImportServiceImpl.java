package com.priceintel.backend.service.impl;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.ImportFieldCatalog;
import com.priceintel.backend.constants.ImportFileType;
import com.priceintel.backend.constants.ImportStatus;
import com.priceintel.backend.dto.response.ImportJobResponse;
import com.priceintel.backend.dto.response.ImportPreviewResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.entity.ImportError;
import com.priceintel.backend.entity.ImportJob;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.mapper.ImportMapper;
import com.priceintel.backend.repository.ImportErrorRepository;
import com.priceintel.backend.repository.ImportJobRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.ProductImportService;
import com.priceintel.backend.utils.ColumnMappingResolver;
import com.priceintel.backend.utils.ParsedSheet;
import com.priceintel.backend.utils.SpreadsheetParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImportServiceImpl implements ProductImportService {

    private final ImportJobRepository jobRepository;
    private final ImportErrorRepository errorRepository;
    private final ImportMapper importMapper;
    private final AsyncImportProcessor asyncImportProcessor;
    private final ObjectMapper objectMapper;

    private static final int SAMPLE_ROWS = 10;

    @Override
    public ImportPreviewResponse preview(MultipartFile file) {
        ImportFileType type = detectFileType(file);
        byte[] content = readBytes(file);
        ParsedSheet sheet = parse(content, type);

        List<String> headers = sheet.getHeaders();
        Map<String, String> suggested = ColumnMappingResolver.suggest(headers);

        List<Map<String, String>> sample = sheet.getRows().stream().limit(SAMPLE_ROWS).toList();

        List<String> warnings = new ArrayList<>();
        for (String required : ImportFieldCatalog.REQUIRED_FIELDS) {
            if (!suggested.containsKey(required)) {
                warnings.add("No column detected for required field '" + required
                        + "'. Provide a mapping override before importing.");
            }
        }
        if (sheet.getRows().isEmpty()) {
            warnings.add("The file contains no data rows.");
        }

        return ImportPreviewResponse.builder()
                .fileName(file.getOriginalFilename())
                .fileType(type.name())
                .totalRows(sheet.getRows().size())
                .detectedColumns(headers)
                .suggestedMapping(suggested)
                .sampleRows(sample)
                .warnings(warnings)
                .build();
    }

    @Override
    public ImportJobResponse startImport(MultipartFile file, String mappingJson) {
        ImportFileType type = detectFileType(file);
        byte[] content = readBytes(file);
        ParsedSheet sheet = parse(content, type);

        if (sheet.getRows().isEmpty()) {
            throw new BadRequestException("The file contains no data rows to import.");
        }

        Map<String, String> overrides = parseMappingOverrides(mappingJson);
        Map<String, String> mapping = ColumnMappingResolver.resolve(sheet.getHeaders(), overrides);

        // Required columns must be mapped, otherwise nothing can be imported.
        for (String required : ImportFieldCatalog.REQUIRED_FIELDS) {
            if (!mapping.containsKey(required)) {
                throw new BadRequestException("Required field '" + required
                        + "' is not mapped to any column. Include it in the mapping.");
            }
        }

        ImportJob job = jobRepository.save(ImportJob.builder()
                .fileName(file.getOriginalFilename())
                .fileType(type)
                .status(ImportStatus.PENDING)
                .mappingJson(writeJson(mapping))
                // Captured here, on the request thread, because the worker
                // thread has no tenant context of its own.
                .tenantId(TenantContext.getTenantId())
                .build());

        // Fire-and-forget: runs on the import thread pool.
        asyncImportProcessor.process(job.getId(), content, type, mapping);

        log.info("Queued import job {} for file {}", job.getId(), file.getOriginalFilename());
        return importMapper.toJobResponse(job);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportJobResponse getStatus(Long jobId) {
        return importMapper.toJobResponse(findJobOrThrow(jobId));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ImportJobResponse> getHistory(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid pagination parameters");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ImportJobResponse> result = jobRepository.findForList(TenantContext.scopeOrAllForSuperAdmin(), pageable)
                .map(importMapper::toJobResponse);
        return PagedResponse.from(result);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportErrorReport(Long jobId) {
        findJobOrThrow(jobId);
        List<ImportError> errors = errorRepository.findByImportJobIdOrderByRowNumberAsc(jobId);

        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                .setHeader("RowNumber", "SKU", "Field", "Error", "RawData").build())) {
            for (ImportError e : errors) {
                printer.printRecord(e.getRowNumber(), e.getSku(), e.getField(),
                        e.getErrorMessage(), e.getRawData());
            }
            printer.flush();
        } catch (IOException e) {
            throw new BadRequestException("Failed to build error report: " + e.getMessage());
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ---------- helpers ----------

    private ImportJob findJobOrThrow(Long jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found with id: " + jobId));
    }

    private ImportFileType detectFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("No file was uploaded, or the file is empty.");
        }
        String name = file.getOriginalFilename();
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".csv")) {
                return ImportFileType.CSV;
            }
            if (lower.endsWith(".xlsx")) {
                return ImportFileType.XLSX;
            }
        }
        throw new BadRequestException("Unsupported file type. Please upload a .csv or .xlsx file.");
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file: " + e.getMessage());
        }
    }

    private ParsedSheet parse(byte[] content, ImportFileType type) {
        try {
            return SpreadsheetParser.parse(content, type);
        } catch (Exception e) {
            throw new BadRequestException("Could not parse the file. Is it a valid " + type + "? " + e.getMessage());
        }
    }

    private Map<String, String> parseMappingOverrides(String mappingJson) {
        if (mappingJson == null || mappingJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(mappingJson, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            throw new BadRequestException("Invalid mapping JSON: " + e.getMessage());
        }
    }

    private String writeJson(Map<String, String> mapping) {
        try {
            return objectMapper.writeValueAsString(mapping);
        } catch (Exception e) {
            return mapping.toString();
        }
    }
}
