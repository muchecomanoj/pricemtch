package com.priceintel.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.entity.FxRate;
import com.priceintel.backend.service.impl.FxRateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Stored exchange rates, used to compare prices across marketplaces. */
@RestController
@RequestMapping("/api/v1/fx-rates")
@RequiredArgsConstructor
@Tag(name = "Exchange Rates",
        description = "Rates used to show prices from several countries in one currency")
public class FxRateController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'SUPER_ADMIN')";

    private final FxRateService service;

    @GetMapping
    @Operation(summary = "Stored rates, newest first")
    public ResponseEntity<ApiResponse<List<FxRate>>> list(
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.success(service.list(limit), "Exchange rates"));
    }

    @GetMapping("/reporting-currency")
    @Operation(summary = "The currency this client's figures are shown in",
            description = "Comes from the client profile. Null means none is set, and "
                    + "cross-currency figures will be reported as unavailable rather than "
                    + "converted at a guess.")
    public ResponseEntity<ApiResponse<String>> reportingCurrency() {
        return ResponseEntity.ok(ApiResponse.success(
                service.reportingCurrency(), "Reporting currency"));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Store an exchange rate",
            description = "`rate` is how many units of `quoteCurrency` one unit of "
                    + "`baseCurrency` buys. Storing USD→CAD is enough to convert both ways; "
                    + "the inverse is the same fact written backwards.\n\n"
                    + "`asOf` is when the rate was *true*, not when it was entered — a figure "
                    + "from last month is converted at last month's rate, so reopening an "
                    + "approved recommendation does not restate its margin.\n\n"
                    + "Re-posting the same pair and `asOf` corrects that rate rather than "
                    + "failing.")
    public ResponseEntity<ApiResponse<FxRate>> save(@RequestBody FxRate rate) {
        return ResponseEntity.ok(ApiResponse.success(service.save(rate), "Rate saved"));
    }

    @GetMapping("/convert")
    @Operation(summary = "Convert an amount, showing the rate used",
            description = "A null `amount` in the reply means no rate was available. The "
                    + "original figure is deliberately not returned in its place — it would be "
                    + "wrong by the whole exchange rate while looking entirely normal.")
    public ResponseEntity<ApiResponse<FxRateService.Converted>> convert(
            @RequestParam java.math.BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(ApiResponse.success(
                service.convert(amount, from, to), "Converted"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Delete a stored rate")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Rate deleted"));
    }
}
