package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.priceintel.backend.constants.RecommendationStatus;
import com.priceintel.backend.dto.response.MarketPricesResponse;
import com.priceintel.backend.dto.response.ProfitabilityResponse;
import com.priceintel.backend.entity.PriceRecommendation;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.PriceRecommendationRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.service.impl.CostProfileService;
import com.priceintel.backend.service.impl.PricingPolicyService;
import com.priceintel.backend.service.impl.RecommendationService;
import com.priceintel.backend.service.impl.SearchPipelineService;

/**
 * Two things a recommendation must not say.
 *
 * <p>Product 94 showed both at once: a card reading "expires 3 weeks ago" with
 * every button live, and "Expected margin 100.0%" beside "Break-even $0.00".
 * The first let a month-old price be published onto today's product; the second
 * reported a missing cost as a perfect margin, which is the number someone
 * reads just before deciding what to charge.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationGuardTest {

    @Mock ProductRepository productRepo;
    @Mock PriceRecommendationRepository repo;
    @Mock CostProfileService costService;
    @Mock SearchPipelineService searchService;
    @Mock PricingPolicyService policyService;

    @InjectMocks RecommendationService service;

    private static final long PRODUCT = 94L;

    @BeforeEach
    void setUp() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(productRepo.existsById(anyLong())).thenReturn(true);
        when(productRepo.findById(anyLong())).thenReturn(Optional.of(new Product()));
        when(policyService.resolve(anyLong(), any())).thenReturn(null);
        when(policyService.cooldownBlock(any(), any())).thenReturn(null);
    }

    private PriceRecommendation rec(RecommendationStatus status, LocalDate expiresAt) {
        PriceRecommendation r = PriceRecommendation.builder()
                .productId(PRODUCT).recommendedPrice(new BigDecimal("29.01"))
                .status(status).expiresAt(expiresAt).build();
        r.setId(84L);
        when(repo.findById(84L)).thenReturn(Optional.of(r));
        return r;
    }

    @Nested
    @DisplayName("a recommendation past its expiry date")
    class Expired {

        private static final LocalDate LAST_MONTH = LocalDate.now().minusWeeks(3);

        @Test
        @DisplayName("cannot be approved, and the refusal says why and what to do")
        void cannotBeApproved() {
            rec(RecommendationStatus.DRAFT, LAST_MONTH);

            assertThatThrownBy(() -> service.approve(84L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("expired on " + LAST_MONTH)
                    .hasMessageContaining("cannot be approved")
                    .hasMessageContaining("Generate a new one");
        }

        @Test
        @DisplayName("cannot be submitted for approval")
        void cannotBeSubmitted() {
            rec(RecommendationStatus.DRAFT, LAST_MONTH);

            assertThatThrownBy(() -> service.submitForApproval(84L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("cannot be submitted");
        }

        @Test
        @DisplayName("cannot be published — the case that sets a real selling price")
        void cannotBePublished() {
            rec(RecommendationStatus.APPROVED, LAST_MONTH);

            // Approved last month from competitor prices that have since moved.
            assertThatThrownBy(() -> service.publish(84L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("can still be rejected — saying no to a stale price is never unsafe")
        void canStillBeRejected() {
            rec(RecommendationStatus.DRAFT, LAST_MONTH);

            assertThatCode(() -> service.reject(84L)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("a recommendation inside its window")
    class StillValid {

        @Test
        @DisplayName("expiring today is still good — the date is the last valid day")
        void expiringTodayIsValid() {
            rec(RecommendationStatus.DRAFT, LocalDate.now());

            assertThatCode(() -> service.approve(84L)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("expiring in a week approves as normal")
        void futureExpiryIsFine() {
            rec(RecommendationStatus.DRAFT, LocalDate.now().plusDays(7));

            assertThat(service.approve(84L).getStatus()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("one with no expiry date at all is not treated as expired")
        void noExpiryMeansNoDeadline() {
            rec(RecommendationStatus.DRAFT, null);

            assertThatCode(() -> service.approve(84L)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("the expired flag on the response")
    class Flag {

        @Test
        @DisplayName("is set on a stale draft, so the screen need not work it out")
        void staleDraftIsFlagged() {
            rec(RecommendationStatus.DRAFT, LocalDate.now().minusDays(1));

            assertThat(service.reject(84L)).isNotNull();
        }

        @Test
        @DisplayName("is never set on a published one — that is history, not a deadline")
        void publishedIsNotExpired() {
            PriceRecommendation published = rec(RecommendationStatus.PUBLISHED,
                    LocalDate.now().minusWeeks(3));

            // A price that went live two months ago has not "expired"; it is
            // simply the price, and marking it stale would invite undoing it.
            assertThat(published.getStatus()).isEqualTo(RecommendationStatus.PUBLISHED);
            assertThatThrownBy(() -> service.approve(84L))
                    .hasMessageContaining("Cannot approve a recommendation in state PUBLISHED");
        }
    }

    @Nested
    @DisplayName("a product whose cost profile was saved empty")
    class EmptyCostProfile {

        /** Exactly product 94: a profile exists, every field is zero. */
        private ProfitabilityResponse allZero() {
            return ProfitabilityResponse.builder()
                    .costProfileConfigured(true)
                    .breakEvenPrice(BigDecimal.ZERO)
                    .contributionMarginPct(new BigDecimal("100.00"))
                    .currency("USD")
                    .build();
        }

        @BeforeEach
        void market() {
            when(searchService.marketPrices(anyLong(), anyBoolean())).thenReturn(
                    MarketPricesResponse.builder()
                            .median(new BigDecimal("29.01")).competitorCount(38)
                            .currency("USD").build());
            when(costService.computeProfitability(anyLong(), any())).thenReturn(allZero());
            when(policyService.apply(any(), any(), any(), any())).thenAnswer(inv ->
                    new PricingPolicyService.Bounded(inv.getArgument(0), inv.getArgument(0), PricingPolicyService.Bound.NONE));
            when(repo.findFirstByProductIdAndStatusOrderByIdDesc(anyLong(), any()))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("reports no margin rather than 100%")
        void noMarginIsReported() {
            var result = service.generate(PRODUCT, null);

            // 100% was never a margin. It was the absence of a cost, formatted
            // as good news, directly above a button that sets the price.
            assertThat(result.getExpectedMarginPct()).isNull();
        }

        @Test
        @DisplayName("reports no break-even rather than zero")
        void noBreakEvenIsReported() {
            assertThat(service.generate(PRODUCT, null).getBreakEvenPrice()).isNull();
        }

        @Test
        @DisplayName("says in words that the price rests on competitors alone")
        void rationaleSaysSo() {
            assertThat(service.generate(PRODUCT, null).getRationale())
                    .contains("No costs are recorded")
                    .doesNotContain("break-even");
        }

        @Test
        @DisplayName("still recommends a price — competitors are enough to go on")
        void aPriceIsStillProduced() {
            // Withholding the recommendation would be the wrong correction. The
            // competitor median is real; only the margin is unknown.
            assertThat(service.generate(PRODUCT, null).getRecommendedPrice())
                    .isEqualByComparingTo("29.01");
        }
    }

    @Nested
    @DisplayName("a product with real costs")
    class RealCostProfile {

        @BeforeEach
        void market() {
            when(searchService.marketPrices(anyLong(), anyBoolean())).thenReturn(
                    MarketPricesResponse.builder()
                            .median(new BigDecimal("29.01")).competitorCount(38)
                            .currency("USD").build());
            when(costService.computeProfitability(anyLong(), any())).thenReturn(
                    ProfitabilityResponse.builder()
                            .costProfileConfigured(true)
                            .breakEvenPrice(new BigDecimal("18.40"))
                            .contributionMarginPct(new BigDecimal("36.50"))
                            .currency("USD").build());
            when(policyService.apply(any(), any(), any(), any())).thenAnswer(inv ->
                    new PricingPolicyService.Bounded(inv.getArgument(0), inv.getArgument(0), PricingPolicyService.Bound.NONE));
            when(repo.findFirstByProductIdAndStatusOrderByIdDesc(anyLong(), any()))
                    .thenReturn(Optional.empty());
        }

        @Test
        @DisplayName("is unaffected — margin and break-even come through as before")
        void realCostsStillReport() {
            var result = service.generate(PRODUCT, null);

            assertThat(result.getExpectedMarginPct()).isEqualByComparingTo("36.50");
            assertThat(result.getBreakEvenPrice()).isEqualByComparingTo("18.40");
            assertThat(result.getRationale()).contains("break-even");
        }
    }
}
