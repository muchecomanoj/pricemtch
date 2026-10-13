package com.priceintel.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.BulkMonitorRequest;
import com.priceintel.backend.dto.request.MonitorRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.BulkMonitorResponse;
import com.priceintel.backend.dto.response.MonitorResponse;
import com.priceintel.backend.service.impl.MonitorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Scheduled competitor checks. A monitor re-runs a product's search on a
 * cadence, so price history, trends and alerts have data without someone
 * clicking Find competitors on every product.
 */
@RestController
@RequestMapping("/api/v1/monitors")
@RequiredArgsConstructor
@Tag(name = "Monitors", description = "Scheduled competitor discovery per product")
public class MonitorController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')";

    private final MonitorService monitorService;

    @GetMapping
    @Operation(summary = "List monitors",
            description = "Optionally filter by product or enabled state. Each row carries its last "
                    + "run, next run, last result count and last status, plus the plan's fastest "
                    + "allowed cadence.")
    public ResponseEntity<ApiResponse<List<MonitorResponse>>> list(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Boolean enabled) {
        return ResponseEntity.ok(ApiResponse.success(
                monitorService.list(productId, enabled), "Monitors"));
    }

    @GetMapping("/{id}/last-run-listings")
    @Operation(summary = "The listings this monitor's most recent run returned",
            description = "The rows behind the \"N listings last run\" badge. Distinct from the "
                    + "product's Competitors tab, which shows everything ever found. Empty when the "
                    + "monitor has not run yet, or when the last run found nothing.")
    public ResponseEntity<ApiResponse<List<com.priceintel.backend.dto.response.CompetitorListingResponse>>>
            lastRunListings(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                monitorService.lastRunListings(id), "Listings from the last run"));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Start monitoring a product",
            description = "One monitor per product. intervalMinutes must not be below the plan's "
                    + "minIntervalMinutes. The first run happens on the next sweep.")
    public ResponseEntity<ApiResponse<MonitorResponse>> create(@Valid @RequestBody MonitorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(monitorService.create(request), "Monitor created"));
    }

    @PostMapping("/bulk")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Start monitoring several products on the same settings",
            description = "Up to 200 products per request.\n\n"
                    + "**The shared settings are checked first.** A bad `intervalMinutes`, an "
                    + "unknown marketplace or a `maxResults` outside 1–50 returns 400 and creates "
                    + "nothing, rather than writing some monitors before failing.\n\n"
                    + "**Partial success is normal and returns 200.** A product that is already "
                    + "monitored, or is not in your catalogue, appears in `skipped` with a reason; "
                    + "the rest are created. Only settings that are wrong for the whole request "
                    + "are a 400.\n\n"
                    + "First runs are spread across one interval. Creating fifty monitors due at "
                    + "the same instant would run them together against the marketplace and AI "
                    + "rate limits, and repeat that burst at every interval.")
    public ResponseEntity<ApiResponse<BulkMonitorResponse>> createBulk(
            @Valid @RequestBody BulkMonitorRequest request) {
        BulkMonitorResponse result = monitorService.createBulk(request);
        return ResponseEntity.ok(ApiResponse.success(result,
                result.getCreated().size() + " monitor(s) created, "
                        + result.getSkipped().size() + " skipped"));
    }

    @PostMapping("/bulk-active")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Pause or resume many monitors",
            description = "`enabled=false` pauses: the monitor and its history are kept and it "
                    + "simply stops running. Use this rather than delete for \"stop checking this "
                    + "for now\" — deleting throws the history away too.\n\n"
                    + "Ids that do not exist, or belong to another client, are ignored rather than "
                    + "refused; `changed` says how many really changed. Up to 200 per request.\n\n"
                    + "Resuming spreads the next runs across one interval, so a mass resume does "
                    + "not put every monitor on the same schedule.")
    public ResponseEntity<ApiResponse<java.util.Map<String, Integer>>> setEnabledBulk(
            @RequestParam boolean enabled,
            @RequestBody List<Long> monitorIds) {
        int changed = monitorService.setEnabledBulk(monitorIds, enabled);
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("changed", changed),
                changed + " monitor(s) " + (enabled ? "resumed" : "paused")));
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Delete many monitors",
            description = "POST rather than DELETE because the ids travel in a body, which DELETE "
                    + "does not reliably carry. Monitors belonging to another client are ignored; "
                    + "the count says how many were actually removed. Up to 200 per request.")
    public ResponseEntity<ApiResponse<java.util.Map<String, Integer>>> deleteBulk(
            @RequestBody List<Long> monitorIds) {
        int deleted = monitorService.deleteBulk(monitorIds);
        return ResponseEntity.ok(ApiResponse.success(
                java.util.Map.of("deleted", deleted), deleted + " monitor(s) deleted"));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Update a monitor (cadence, markets, maxResults, enabled)")
    public ResponseEntity<ApiResponse<MonitorResponse>> update(
            @PathVariable Long id, @Valid @RequestBody MonitorRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                monitorService.update(id, request), "Monitor updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Stop monitoring a product")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        monitorService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Monitor deleted"));
    }
}
