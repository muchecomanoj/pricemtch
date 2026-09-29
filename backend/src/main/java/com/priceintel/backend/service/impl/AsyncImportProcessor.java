package com.priceintel.backend.service.impl;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.ImportFileType;
import com.priceintel.backend.constants.ImportStatus;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.entity.ImportError;
import com.priceintel.backend.entity.ImportJob;
import com.priceintel.backend.repository.ImportErrorRepository;
import com.priceintel.backend.repository.ImportJobRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.ProductService;
import com.priceintel.backend.utils.ImportRowConverter;
import com.priceintel.backend.utils.ParsedSheet;
import com.priceintel.backend.utils.SpreadsheetParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a bulk import in the background. Each row is processed independently:
 * validation and duplicate checks produce row-level errors rather than aborting
 * the whole job. Progress and counts are persisted so clients can poll status.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncImportProcessor {

    private final ImportJobRepository jobRepository;
    private final ImportErrorRepository errorRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ObjectMapper objectMapper;

    private static final int PROGRESS_FLUSH_EVERY = 5;

    @Async("importTaskExecutor")
    public void process(Long jobId, byte[] content, ImportFileType fileType, Map<String, String> mapping) {
        ImportJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Import job {} vanished before processing", jobId);
            return;
        }

        // Re-establish the uploader's tenant on this worker thread. It is held
        // in a ThreadLocal, which does not follow the work across the async
        // boundary — without this every imported product is created with no
        // owner and is then invisible to the client who uploaded the file.
        try {
            processJob(job, content, fileType, mapping);
        } finally {
            TenantContext.clear();
        }
    }

    private void processJob(ImportJob job, byte[] content, ImportFileType fileType,
                            Map<String, String> mapping) {
        if (job.getTenantId() != null) {
            TenantContext.set(job.getTenantId(), false);
        }

        job.setStatus(ImportStatus.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);

        try {
            ParsedSheet sheet = SpreadsheetParser.parse(content, fileType);
            List<Map<String, String>> rows = sheet.getRows();
            job.setTotalRows(rows.size());
            jobRepository.save(job);

            Set<String> seenSkus = new HashSet<>();

            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> row = rows.get(i);
                int rowNumber = i + 2; // +1 for zero-index, +1 for header row
                processRow(job, row, rowNumber, mapping, seenSkus);

                job.setProcessedRows(i + 1);
                if ((i + 1) % PROGRESS_FLUSH_EVERY == 0) {
                    jobRepository.save(job);
                }
            }

            job.setStatus((job.getErrorCount() > 0 || job.getDuplicateCount() > 0)
                    ? ImportStatus.COMPLETED_WITH_ERRORS : ImportStatus.COMPLETED);
            job.setMessage(String.format("Imported %d of %d rows (%d duplicates, %d errors)",
                    job.getSuccessCount(), job.getTotalRows(), job.getDuplicateCount(), job.getErrorCount()));
        } catch (Exception e) {
            log.error("Import job {} failed", job.getId(), e);
            job.setStatus(ImportStatus.FAILED);
            job.setMessage("Import failed: " + e.getMessage());
        } finally {
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
            log.info("Import job {} finished: {}", job.getId(), job.getStatus());
        }
    }

    private void processRow(ImportJob job, Map<String, String> row, int rowNumber,
                            Map<String, String> mapping, Set<String> seenSkus) {
        ImportRowConverter.Result result = ImportRowConverter.convert(row, mapping);

        if (result.hasErrors()) {
            for (ImportRowConverter.FieldError fe : result.getErrors()) {
                saveError(job, rowNumber, null, fe.getField(), fe.getMessage(), row);
            }
            job.setErrorCount(job.getErrorCount() + 1);
            return;
        }

        CreateProductRequest request = result.getRequest();
        String skuKey = request.getSku().toUpperCase();

        // Duplicate detection: within the file, and against existing products.
        if (seenSkus.contains(skuKey)
                || productRepository.existsByTenantIdAndSkuIgnoreCase(job.getTenantId(), request.getSku())) {
            saveError(job, rowNumber, request.getSku(), "sku",
                    "Duplicate SKU (already in file or database)", row);
            job.setDuplicateCount(job.getDuplicateCount() + 1);
            return;
        }

        try {
            productService.createProduct(request);
            seenSkus.add(skuKey);
            job.setSuccessCount(job.getSuccessCount() + 1);
        } catch (Exception e) {
            saveError(job, rowNumber, request.getSku(), null, e.getMessage(), row);
            job.setErrorCount(job.getErrorCount() + 1);
        }
    }

    private void saveError(ImportJob job, int rowNumber, String sku, String field, String message,
                           Map<String, String> row) {
        errorRepository.save(ImportError.builder()
                .importJob(job)
                .rowNumber(rowNumber)
                .sku(sku)
                .field(field)
                .errorMessage(truncate(message, 500))
                .rawData(toJson(row))
                .build());
    }

    private String toJson(Map<String, String> row) {
        try {
            return truncate(objectMapper.writeValueAsString(row), 4000);
        } catch (JsonProcessingException e) {
            return row.toString();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
