package com.priceintel.backend.utils;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The result of parsing a CSV/XLSX file: the header list plus each data row as
 * an ordered header-&gt;value map.
 */
@Getter
@AllArgsConstructor
public class ParsedSheet {
    private final List<String> headers;
    private final List<Map<String, String>> rows;
}
