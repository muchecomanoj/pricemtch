package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.response.ProfitabilityResponse;
import com.priceintel.backend.dto.response.ProfitabilityTrendPoint;
import com.priceintel.backend.entity.ProfitabilitySnapshot;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.repository.ProfitabilitySnapshotRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Captures a daily profitability snapshot per product so the Profitability page
 * can chart a Net Profit trend over time (profit itself is computed live).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfitabilitySnapshotService {

    private final ProductRepository productRepo;
    private final ProfitabilitySnapshotRepository repo;
    private final CostProfileService costService;


    // ---------- capture ----------

    /** Snapshot every priced product's profitability for today (idempotent per day). */
    @Transactional
    public int captureToday() {
        LocalDate today = LocalDate.now();
        int n = 0;
        for (Product p : productRepo.findAll()) {
            if (p.getOurPrice() == null || p.getOurPrice().signum() <= 0) {
                continue;
            }
            if (repo.existsByProductIdAndSnapshotDate(p.getId(), today)) {
                continue;
            }
            try {
                ProfitabilityResponse pr = costService.computeProfitability(p.getId(), null);
                if (!pr.isCostProfileConfigured()) {
                    continue;
                }
                repo.save(build(p.getId(), today, pr));
                n++;
            } catch (RuntimeException e) {
                log.debug("Skip profit snapshot for product {}: {}", p.getId(), e.getMessage());
            }
        }
        if (n > 0) {
            log.info("Captured {} profitability snapshots for {}", n, today);
        }
        return n;
    }

    /** Runs daily at 01:30. */
    @Scheduled(cron = "0 30 1 * * *")
    public void scheduledCapture() {
        captureToday();
    }

    /**
     * At startup: capture today's real profitability.
     *
     * <p>This deliberately does not back-fill history. An empty trend chart is
     * the truthful answer for a product we have not observed yet; a fabricated
     * line is worse than no line, because it invites a pricing decision from
     * data that was generated rather than measured.</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        captureToday();
    }

    // ---------- read ----------

    @Transactional(readOnly = true)
    public List<ProfitabilityTrendPoint> trend(Long productId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        List<ProfitabilityTrendPoint> out = new ArrayList<>();
        for (ProfitabilitySnapshot s : repo
                .findByProductIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(productId, start, end)) {
            out.add(ProfitabilityTrendPoint.builder()
                    .period(s.getSnapshotDate())
                    .netProfit(s.getNetProfit())
                    .marginPct(s.getContributionMarginPct())
                    .roiPct(s.getRoiPct())
                    .sellingPrice(s.getSellingPrice())
                    .build());
        }
        return out;
    }

    // ---------- helpers ----------

    private ProfitabilitySnapshot build(Long productId, LocalDate date, ProfitabilityResponse pr) {
        return ProfitabilitySnapshot.builder()
                .productId(productId).snapshotDate(date)
                .sellingPrice(pr.getSellingPrice())
                .netProfit(pr.getNetProfitEstimate())
                .contributionMarginPct(pr.getContributionMarginPct())
                .roiPct(pr.getRoiPct())
                .breakEvenPrice(pr.getBreakEvenPrice())
                .currency(pr.getCurrency())
                .build();
    }

}
