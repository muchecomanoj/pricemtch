package com.priceintel.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.DashboardActivities;
import com.priceintel.backend.dto.response.DashboardCharts;
import com.priceintel.backend.dto.response.DashboardSummaryItem;
import com.priceintel.backend.service.impl.ChartExportService;
import com.priceintel.backend.service.impl.DashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * The Dashboard's three panels. Everything is scoped to the caller's own
 * company; a caller with no company gets empty panels rather than the platform's
 * totals.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Summary tiles, charts and recent activity for the signed-in company")
public class DashboardController {

    private final DashboardService dashboardService;
    private final ChartExportService chartExportService;

    @GetMapping("/summary")
    @Operation(summary = "KPI tiles",
            description = "Products, matched products, competitor listings, searches today, open price "
                    + "suggestions, average margin, price changes in the last 7 days, and active monitors. "
                    + "Each carries the percentage change against the previous period, or no change value "
                    + "at all when there is no earlier period to compare with.")
    public ResponseEntity<ApiResponse<List<DashboardSummaryItem>>> summary() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.summary(), "Dashboard summary"));
    }

    @GetMapping("/charts")
    @Operation(summary = "The six dashboard charts",
            description = "Our price against the market and profit per unit over six months, listings by "
                    + "marketplace, top sellers, products by category, and searches per day over the last "
                    + "week. A null inside a series means no data for that period, drawn as a gap.")
    public ResponseEntity<ApiResponse<DashboardCharts>> charts() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.charts(), "Dashboard charts"));
    }

    @GetMapping("/summary/export")
    @Operation(summary = "The KPI tiles as a CSV file",
            description = "The figures behind the tiles, for a spreadsheet. A blank change column means "
                    + "there was no earlier period to compare with.")
    public ResponseEntity<byte[]> exportSummary() {
        byte[] csv = chartExportService.dashboardSummary();
        return ResponseEntity.ok()
                .headers(com.priceintel.backend.utils.CsvExport.headers(
                        com.priceintel.backend.utils.CsvExport.filename("dashboard-summary")))
                .body(csv);
    }

    @GetMapping("/charts/{chart}/export")
    @Operation(summary = "One chart's figures as a CSV file",
            description = "chart is one of: price-trend, marketplace-comparison, profit-analysis, "
                    + "top-competitors, category-distribution, search-activity. The file holds the "
                    + "numbers the chart was drawn from, so it can be checked or charted again; an "
                    + "empty cell means no data for that period, never a zero.")
    public ResponseEntity<byte[]> exportChart(@PathVariable String chart) {
        ChartExportService.Chart which = ChartExportService.Chart.of(chart);
        byte[] csv = chartExportService.dashboardChart(which);
        return ResponseEntity.ok()
                .headers(com.priceintel.backend.utils.CsvExport.headers(
                        chartExportService.dashboardFilename(which)))
                .body(csv);
    }

    @GetMapping("/activities")
    @Operation(summary = "Recent searches, fired alerts and open price suggestions")
    public ResponseEntity<ApiResponse<DashboardActivities>> activities() {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.activities(), "Dashboard activity"));
    }
}
