package com.priceintel.backend.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.priceintel.backend.constants.ImportFileType;

/**
 * Parses CSV and XLSX byte content into a common {@link ParsedSheet} form
 * (header row + data rows). The first row is always treated as the header.
 */
public final class SpreadsheetParser {

    private SpreadsheetParser() {
    }

    public static ParsedSheet parse(byte[] content, ImportFileType type) throws IOException {
        return type == ImportFileType.CSV ? parseCsv(content) : parseXlsx(content);
    }

    private static ParsedSheet parseCsv(byte[] content) throws IOException {
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader,
                     CSVFormat.DEFAULT.builder()
                             .setHeader()
                             .setSkipHeaderRecord(true)
                             .setIgnoreEmptyLines(true)
                             .setTrim(true)
                             .build())) {

            List<String> headers = parser.getHeaderNames();
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> row = new LinkedHashMap<>();
                for (String header : headers) {
                    row.put(header, record.isMapped(header) ? safe(record.get(header)) : "");
                }
                if (isRowEmpty(row)) {
                    continue;
                }
                rows.add(row);
            }
            return new ParsedSheet(headers, rows);
        }
    }

    private static ParsedSheet parseXlsx(byte[] content) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            List<String> headers = new ArrayList<>();
            List<Map<String, String>> rows = new ArrayList<>();

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return new ParsedSheet(headers, rows);
            }
            int lastCol = headerRow.getLastCellNum();
            for (int c = 0; c < lastCol; c++) {
                Cell cell = headerRow.getCell(c);
                headers.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
            }

            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row dataRow = sheet.getRow(r);
                if (dataRow == null) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = dataRow.getCell(c);
                    row.put(headers.get(c), cell == null ? "" : formatter.formatCellValue(cell).trim());
                }
                if (isRowEmpty(row)) {
                    continue;
                }
                rows.add(row);
            }
            return new ParsedSheet(headers, rows);
        }
    }

    private static boolean isRowEmpty(Map<String, String> row) {
        return row.values().stream().allMatch(v -> v == null || v.isBlank());
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
