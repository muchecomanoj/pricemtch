package com.priceintel.backend.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/**
 * Writes a small CSV for a download button.
 *
 * <p>Same conventions as the product export, so every file the product opens
 * the same way: UTF-8 with the byte-order mark Excel needs, or accented brand
 * and product names arrive mangled.</p>
 */
public final class CsvExport {

    private CsvExport() {
    }

    /** A row writer that may throw, so callers can print straight from a loop. */
    @FunctionalInterface
    public interface Rows {
        void write(CSVPrinter printer) throws IOException;
    }

    public static byte[] write(String[] headers, Rows rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            try (CSVPrinter printer = new CSVPrinter(
                    new OutputStreamWriter(out, StandardCharsets.UTF_8),
                    CSVFormat.DEFAULT.builder().setHeader(headers).build())) {
                rows.write(printer);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * A download's filename, dated so a folder of them stays sortable and a
     * second download does not silently overwrite yesterday's.
     */
    public static String filename(String name) {
        return name + "-" + java.time.LocalDate.now() + ".csv";
    }

    /** The headers a browser needs to save the response as a file. */
    public static org.springframework.http.HttpHeaders headers(String filename) {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"));
        h.setContentDisposition(org.springframework.http.ContentDisposition.attachment()
                .filename(filename).build());
        // Without this the browser's fetch cannot read the filename we chose.
        h.setAccessControlExposeHeaders(java.util.List.of("Content-Disposition"));
        return h;
    }

}
