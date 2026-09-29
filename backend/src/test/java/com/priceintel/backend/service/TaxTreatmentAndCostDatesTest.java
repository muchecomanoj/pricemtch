package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.priceintel.backend.constants.TaxTreatment;
import com.priceintel.backend.dto.response.ProfitabilityResponse;
import com.priceintel.backend.entity.CostProfile;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.repository.CostProfileRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.service.impl.CostModelService;
import com.priceintel.backend.service.impl.CostProfileService;

/**
 * Two Phase 2 changes to the profitability engine: costs are dated, and the
 * price may already contain the tax.
 *
 * <p>The tax case is the expensive one. A £120 VAT-inclusive price earns £100,
 * and treating it as £120 overstated the margin by the whole VAT rate while the
 * arithmetic stayed internally consistent — nothing looked wrong.</p>
 */
class TaxTreatmentAndCostDatesTest {

    private static final Long PRODUCT_ID = 7L;

    private CostProfileRepository costRepo;
    private CostProfileService service;

    @BeforeEach
    void setUp() {
        costRepo = mock(CostProfileRepository.class);
        ProductRepository productRepo = mock(ProductRepository.class);
        Product product = new Product();
        product.setId(PRODUCT_ID);
        when(productRepo.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepo.existsById(PRODUCT_ID)).thenReturn(true);
        service = new CostProfileService(costRepo, productRepo, mock(CostModelService.class));
    }

    /** A profile with one flat cost and no percentage fees, so the tax effect is isolated. */
    private CostProfile profile(TaxTreatment treatment, String taxPct) {
        return CostProfile.builder()
                .productId(PRODUCT_ID)
                .currency("GBP")
                .cogs(new BigDecimal("40.00"))
                .taxRatePct(new BigDecimal(taxPct))
                .taxTreatment(treatment)
                .validFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    private void inForce(CostProfile profile) {
        when(costRepo.findEffectiveOn(eq(PRODUCT_ID), any(), any())).thenReturn(List.of(profile));
    }

    @Test
    @DisplayName("pass-through tax leaves net revenue equal to the listed price")
    void passThroughUnchanged() {
        inForce(profile(TaxTreatment.PASS_THROUGH, "20"));

        ProfitabilityResponse r = service.computeProfitability(
                PRODUCT_ID, new BigDecimal("120.00"));

        assertThat(r.getNetRevenue()).isEqualByComparingTo("120.00");
        assertThat(r.getContributionProfit()).isEqualByComparingTo("80.00");
        // Reported for information, not deducted.
        assertThat(r.getTax()).isEqualByComparingTo("24.00");
        assertThat(r.getTaxTreatment()).isEqualTo("PASS_THROUGH");
    }

    @Test
    @DisplayName("an inclusive price earns the price less the tax, not the price")
    void inclusiveTaxReducesNetRevenue() {
        inForce(profile(TaxTreatment.INCLUSIVE, "20"));

        ProfitabilityResponse r = service.computeProfitability(
                PRODUCT_ID, new BigDecimal("120.00"));

        // 120 / 1.20 = 100.00 earned; 20.00 remitted.
        assertThat(r.getNetRevenue()).isEqualByComparingTo("100.00");
        assertThat(r.getTax()).isEqualByComparingTo("20.00");
        assertThat(r.getContributionProfit()).isEqualByComparingTo("60.00");
        // 60 / 100, not 80 / 120 — the whole point of the change.
        assertThat(r.getContributionMarginPct()).isEqualByComparingTo("60.00");
        assertThat(r.getTaxTreatment()).isEqualTo("INCLUSIVE");
    }

    @Test
    @DisplayName("break-even is grossed up for an inclusive price")
    void inclusiveBreakEvenIsGrossedUp() {
        inForce(profile(TaxTreatment.INCLUSIVE, "20"));

        ProfitabilityResponse r = service.computeProfitability(
                PRODUCT_ID, new BigDecimal("120.00"));

        // £40 of cost has to survive the VAT: 40 * 1.20 = 48.00 listed.
        assertThat(r.getBreakEvenPrice()).isEqualByComparingTo("48.00");
    }

    @Test
    @DisplayName("max affordable COGS comes off net revenue, not the listed price")
    void inclusiveMaxCogsUsesNetRevenue() {
        inForce(profile(TaxTreatment.INCLUSIVE, "20"));

        ProfitabilityResponse r = service.computeProfitability(
                PRODUCT_ID, new BigDecimal("120.00"));

        assertThat(r.getMaxAllowableAcquisitionCost()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("NONE reports no tax at all, distinct from a rate nobody filled in")
    void noTax() {
        inForce(profile(TaxTreatment.NONE, "20"));

        ProfitabilityResponse r = service.computeProfitability(
                PRODUCT_ID, new BigDecimal("120.00"));

        assertThat(r.getTax()).isEqualByComparingTo("0.00");
        assertThat(r.getNetRevenue()).isEqualByComparingTo("120.00");
        assertThat(r.getTaxTreatment()).isEqualTo("NONE");
    }

    // ---------- effective dates ----------

    @Test
    @DisplayName("profitability asks for the costs in force on the date it was given")
    void usesTheProfileInForceOnTheDate() {
        CostProfile june = profile(TaxTreatment.PASS_THROUGH, "0");
        june.setCogs(new BigDecimal("30.00"));
        when(costRepo.findEffectiveOn(eq(PRODUCT_ID), eq(LocalDate.of(2026, 6, 15)), any()))
                .thenReturn(List.of(june));
        // Today's costs are higher; June's figure must not use them.
        CostProfile today = profile(TaxTreatment.PASS_THROUGH, "0");
        when(costRepo.findEffectiveOn(eq(PRODUCT_ID), eq(LocalDate.now()), any()))
                .thenReturn(List.of(today));

        ProfitabilityResponse inJune = service.computeProfitability(
                PRODUCT_ID, new BigDecimal("100.00"), LocalDate.of(2026, 6, 15));
        ProfitabilityResponse now = service.computeProfitability(
                PRODUCT_ID, new BigDecimal("100.00"));

        assertThat(inJune.getContributionProfit()).isEqualByComparingTo("70.00");
        assertThat(now.getContributionProfit()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("no profile in force on the date reads as unconfigured, not as zero costs")
    void noProfileInForce() {
        when(costRepo.findEffectiveOn(any(), any(), any())).thenReturn(List.of());

        ProfitabilityResponse r = service.computeProfitability(
                PRODUCT_ID, new BigDecimal("100.00"), LocalDate.of(2020, 1, 1));

        assertThat(r.isCostProfileConfigured()).isFalse();
        assertThat(r.getContributionProfit()).isNull();
    }

    @Test
    @DisplayName("the engine version says which formula produced a stored figure")
    void formulaVersionIsStamped() {
        inForce(profile(TaxTreatment.PASS_THROUGH, "0"));

        assertThat(service.computeProfitability(PRODUCT_ID, new BigDecimal("100.00"))
                .getFormulaVersion()).isEqualTo("profit-v2");
    }
}
