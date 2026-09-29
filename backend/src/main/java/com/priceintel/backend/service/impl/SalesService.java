package com.priceintel.backend.service.impl;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.ValueStatus;
import com.priceintel.backend.constants.MatchStatus;
import com.priceintel.backend.dto.response.SalesResponse;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.model.SalesMetrics;
import com.priceintel.backend.repository.CompetitorListingRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.service.MarketplaceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Product sales (FR-SALES). Owned-account sales require an authorised seller
 * feed (SP-API Sales) which isn't connected per-product yet, so they report
 * UNAVAILABLE (never fabricated). Competitor figures are aggregated from the
 * matched listings and clearly labelled ESTIMATED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesService {

    private final ProductRepository productRepo;
    private final CompetitorListingRepository listingRepo;
    private final MarketplaceService marketplaceService;

    @Transactional(readOnly = true)
    public SalesResponse getSales(Long productId) {
        if (!productRepo.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }

        int units = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        int sources = 0;
        int listingsChecked = 0;
        String currency = null;
        // Why each marketplace had nothing to give, in its own words, so the
        // screen can say what is missing instead of showing a bare zero.
        java.util.Set<String> reasons = new java.util.LinkedHashSet<>();

        for (var l : listingRepo.findByProductIdOrderByCreatedAtDesc(productId)) {
            if (l.getMatchStatus() != MatchStatus.MATCHED && l.getMatchStatus() != MatchStatus.EQUIVALENT) {
                continue;
            }
            listingsChecked++;
            try {
                Marketplace mp = Marketplace.valueOf(l.getMarketplace());
                SalesMetrics m = marketplaceService.getSales(mp, l.getMarketplaceItemId());

                // A source counts only when it actually carried a number.
                // Counting every listing that answered made six "sources" out of
                // six marketplaces that each said they have no sales data, and
                // the response then read ESTIMATED with zero units — a zero
                // presented as a finding rather than as an absence.
                boolean contributed = m.getEstimatedUnits() != null || m.getEstimatedRevenue() != null;
                if (!contributed) {
                    if (m.getNote() != null && !m.getNote().isBlank()) {
                        reasons.add(m.getNote());
                    }
                    continue;
                }
                if (m.getEstimatedUnits() != null) {
                    units += m.getEstimatedUnits();
                }
                if (m.getEstimatedRevenue() != null) {
                    revenue = revenue.add(m.getEstimatedRevenue());
                }
                if (m.getCurrency() != null) {
                    currency = m.getCurrency();
                }
                sources++;
            } catch (Exception e) {
                log.debug("Sales estimate skipped for listing {}: {}", l.getId(), e.getMessage());
            }
        }

        boolean hasEstimate = sources > 0;
        String unavailableNote = listingsChecked == 0
                ? "No matched competitor listings to estimate from yet."
                : "Checked " + listingsChecked + " matched listing(s); none of the connected marketplaces "
                        + "reports competitor unit sales"
                        + (reasons.isEmpty() ? "." : " — " + String.join("; ", reasons) + ".");
        return SalesResponse.builder()
                .productId(productId).currency(currency)
                .ownedClassification("UNAVAILABLE")
                .ownedNote("Owned-account sales require a connected seller feed (e.g. Amazon SP-API Sales). "
                        + "Not available for this product yet.")
                .estimatedClassification(hasEstimate ? "ESTIMATED" : "UNAVAILABLE")
                .estimatedUnits(hasEstimate ? units : null)
                .estimatedRevenue(hasEstimate ? revenue : null)
                .estimateSources(sources)
                .estimatedNote(hasEstimate
                        ? "Model-derived estimate across matched competitor listings — not actual sales."
                        : unavailableNote)
                // The same vocabulary the rest of the API uses, so a client can
                // read provenance one way everywhere instead of special-casing
                // this response's own classification fields (FR-REPORT-001).
                .valueStatus(java.util.Map.of(
                        "ownedUnits", ValueStatus.UNAVAILABLE.name(),
                        "ownedRevenue", ValueStatus.UNAVAILABLE.name(),
                        "estimatedUnits",
                            (hasEstimate ? ValueStatus.ESTIMATED : ValueStatus.UNAVAILABLE).name(),
                        "estimatedRevenue",
                            (hasEstimate ? ValueStatus.ESTIMATED : ValueStatus.UNAVAILABLE).name()))
                .build();
    }
}
