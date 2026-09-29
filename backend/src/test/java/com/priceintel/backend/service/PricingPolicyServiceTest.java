package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.priceintel.backend.entity.PricingPolicy;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.service.impl.PricingPolicyService;
import com.priceintel.backend.service.impl.PricingPolicyService.Bound;

/**
 * The bounds that stand between a recommendation and a live price
 * (FR-REC-002). These are the last check before money changes hands, so the
 * cases here are the ones that would cost something if they were wrong.
 */
class PricingPolicyServiceTest {

    private final PricingPolicyService service = new PricingPolicyService(null);

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("no policy leaves the price exactly as the engine produced it")
    void noPolicyPassesThrough() {
        var result = service.apply(bd("99.99"), null, bd("120.00"), bd("50.00"));

        assertThat(result.price()).isEqualByComparingTo("99.99");
        assertThat(result.wasCapped()).isFalse();
    }

    @Test
    @DisplayName("a price below the floor is lifted to the floor")
    void floorLifts() {
        PricingPolicy policy = PricingPolicy.builder().floorPrice(bd("80.00")).build();

        var result = service.apply(bd("60.00"), policy, null, null);

        assertThat(result.price()).isEqualByComparingTo("80.00");
        assertThat(result.unbounded()).isEqualByComparingTo("60.00");
        assertThat(result.bound()).isEqualTo(Bound.FLOOR_PRICE);
    }

    @Test
    @DisplayName("the margin floor is break-even lifted by the required margin")
    void marginFloorLifts() {
        PricingPolicy policy = PricingPolicy.builder().floorMarginPct(bd("20")).build();

        // Break-even 50 with 20% required => 60.00
        var result = service.apply(bd("55.00"), policy, null, bd("50.00"));

        assertThat(result.price()).isEqualByComparingTo("60.00");
        assertThat(result.bound()).isEqualTo(Bound.FLOOR_MARGIN);
    }

    @Test
    @DisplayName("with both floors set the higher one wins")
    void higherFloorWins() {
        PricingPolicy policy = PricingPolicy.builder()
                .floorPrice(bd("70.00")).floorMarginPct(bd("20")).build();

        // Fixed floor 70 beats the margin floor of 60.
        var result = service.apply(bd("50.00"), policy, null, bd("50.00"));

        assertThat(result.price()).isEqualByComparingTo("70.00");
        assertThat(result.bound()).isEqualTo(Bound.FLOOR_PRICE);
    }

    @Test
    @DisplayName("a price above the ceiling is brought down to it")
    void ceilingCaps() {
        PricingPolicy policy = PricingPolicy.builder().ceilingPrice(bd("100.00")).build();

        var result = service.apply(bd("140.00"), policy, null, null);

        assertThat(result.price()).isEqualByComparingTo("100.00");
        assertThat(result.bound()).isEqualTo(Bound.CEILING);
    }

    @Test
    @DisplayName("the per-cycle limit caps how far one move may go")
    void maxChangePctCaps() {
        PricingPolicy policy = PricingPolicy.builder().maxChangePct(bd("10")).build();

        // From 100, at most 10 in either direction.
        assertThat(service.apply(bd("140.00"), policy, bd("100.00"), null).price())
                .isEqualByComparingTo("110.00");
        assertThat(service.apply(bd("60.00"), policy, bd("100.00"), null).price())
                .isEqualByComparingTo("90.00");
    }

    @Test
    @DisplayName("with both change limits set the tighter one applies")
    void tighterChangeLimitApplies() {
        PricingPolicy policy = PricingPolicy.builder()
                .maxChangePct(bd("10")).maxChangeAmount(bd("3.00")).build();

        // 10% of 100 is 10; the flat 3.00 is tighter.
        var result = service.apply(bd("140.00"), policy, bd("100.00"), null);

        assertThat(result.price()).isEqualByComparingTo("103.00");
        assertThat(result.bound()).isEqualTo(Bound.MAX_CHANGE);
    }

    @Test
    @DisplayName("a move inside the limit is not treated as capped")
    void smallMoveIsNotCapped() {
        PricingPolicy policy = PricingPolicy.builder().maxChangePct(bd("10")).build();

        var result = service.apply(bd("104.00"), policy, bd("100.00"), null);

        assertThat(result.price()).isEqualByComparingTo("104.00");
        assertThat(result.wasCapped()).isFalse();
    }

    @Test
    @DisplayName("the floor still wins when a change limit would leave the price below cost")
    void floorBeatsChangeLimit() {
        // Selling at 50, floor 80, at most 10 of movement per cycle. Honouring
        // the step limit would publish 60 — below the floor that exists to stop
        // exactly that. Losing money on every sale is worse than a large step.
        PricingPolicy policy = PricingPolicy.builder()
                .floorPrice(bd("80.00")).maxChangePct(bd("20")).build();

        var result = service.apply(bd("55.00"), policy, bd("50.00"), null);

        assertThat(result.price()).isEqualByComparingTo("80.00");
        assertThat(result.bound()).isEqualTo(Bound.FLOOR_PRICE);
    }

    @Test
    @DisplayName("a null price stays null rather than becoming a number")
    void nullPriceStaysNull() {
        var result = service.apply(null, PricingPolicy.builder().floorPrice(bd("10")).build(),
                bd("50"), bd("20"));

        assertThat(result.price()).isNull();
        assertThat(result.wasCapped()).isFalse();
    }

    // ---------- cooldown ----------

    @Test
    @DisplayName("a recent publish blocks the next one, an old one does not")
    void cooldown() {
        PricingPolicy policy = PricingPolicy.builder().cooldownHours(24).build();

        assertThat(service.cooldownBlock(policy, Instant.now().minus(Duration.ofHours(1))))
                .contains("cooldown");
        assertThat(service.cooldownBlock(policy, Instant.now().minus(Duration.ofHours(48))))
                .isNull();
        // Never published: nothing to wait for.
        assertThat(service.cooldownBlock(policy, null)).isNull();
        assertThat(service.cooldownBlock(null, Instant.now())).isNull();
    }

    // ---------- validation ----------

    @Test
    @DisplayName("automation without any bound is refused, not merely warned about")
    void autoPublishNeedsBounds() {
        PricingPolicy policy = PricingPolicy.builder().autoPublish(true).build();

        assertThatThrownBy(() -> service.save(policy))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one bound");
    }

    @Test
    @DisplayName("a floor above the ceiling is refused — no price could satisfy both")
    void impossibleBoundsRefused() {
        PricingPolicy policy = PricingPolicy.builder()
                .floorPrice(bd("100.00")).ceilingPrice(bd("50.00")).build();

        assertThatThrownBy(() -> service.save(policy))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("floor is above the ceiling");
    }

    @Test
    @DisplayName("specificity orders the scope chain: product+channel beats product beats global")
    void specificityOrdering() {
        PricingPolicy global = PricingPolicy.builder().build();
        PricingPolicy channel = PricingPolicy.builder().marketplace("AMAZON").build();
        PricingPolicy product = PricingPolicy.builder().productId(1L).build();
        PricingPolicy both = PricingPolicy.builder().productId(1L).marketplace("AMAZON").build();

        assertThat(both.specificity())
                .isGreaterThan(product.specificity());
        assertThat(product.specificity()).isGreaterThan(channel.specificity());
        assertThat(channel.specificity()).isGreaterThan(global.specificity());
    }
}
