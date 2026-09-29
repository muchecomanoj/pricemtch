package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.priceintel.backend.constants.AlertCondition;
import com.priceintel.backend.dto.request.BulkAlertRuleRequest;
import com.priceintel.backend.dto.response.BulkAlertRuleResponse;
import com.priceintel.backend.entity.AlertRule;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.AlertRuleRepository;
import com.priceintel.backend.repository.CompetitorListingRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.impl.AlertRuleService;

/**
 * Creating one alert across many products.
 *
 * <p>The cases that matter are the partial ones. A batch that half-succeeds is
 * the normal outcome — some products already have the alert, some were never
 * the caller's — and the user has to be able to see which, or the feature is
 * worse than creating them one at a time.</p>
 */
class BulkAlertRuleTest {

    private static final Long TENANT = 1L;
    private static final Long OTHER_TENANT = 2L;

    private AlertRuleRepository ruleRepo;
    private ProductRepository productRepo;
    private CompetitorListingRepository listingRepo;
    private AlertRuleService service;

    @BeforeEach
    void setUp() {
        ruleRepo = mock(AlertRuleRepository.class);
        productRepo = mock(ProductRepository.class);
        listingRepo = mock(CompetitorListingRepository.class);
        service = new AlertRuleService(ruleRepo, productRepo, listingRepo);

        TenantContext.set(TENANT, false);
        // Nothing exists until a test says otherwise.
        when(ruleRepo.findByProductIdInAndCondition(any(), any())).thenReturn(List.of());
        when(listingRepo.countByProductIdAndMatchStatusNotAndLastPriceIsNotNull(anyLong(), any()))
                .thenReturn(3L);
        // saveAll echoes back what it was given, with ids.
        when(ruleRepo.saveAll(any())).thenAnswer(inv -> {
            List<AlertRule> rules = inv.getArgument(0);
            long id = 100;
            for (AlertRule rule : rules) {
                rule.setId(id++);
            }
            return rules;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Product product(Long id, Long tenantId, String title) {
        Product p = new Product();
        p.setId(id);
        p.setTenantId(tenantId);
        p.setTitle(title);
        return p;
    }

    private void catalogue(Product... products) {
        when(productRepo.findAllById(any())).thenReturn(List.of(products));
    }

    private BulkAlertRuleRequest request(Long... productIds) {
        return BulkAlertRuleRequest.builder()
                .productIds(List.of(productIds))
                .condition(AlertCondition.PRICE_DROP)
                .thresholdType("PERCENT")
                .thresholdValue(new java.math.BigDecimal("5"))
                .build();
    }

    @Test
    @DisplayName("creates one rule per product and reports the totals")
    void createsForEveryProduct() {
        catalogue(product(1L, TENANT, "AirPods 4"), product(2L, TENANT, "Dyson V8"));

        BulkAlertRuleResponse result = service.createBulk(request(1L, 2L));

        assertThat(result.getRequested()).isEqualTo(2);
        assertThat(result.getCreated()).isEqualTo(2);
        assertThat(result.getSkipped()).isZero();
        assertThat(result.getResults()).extracting(BulkAlertRuleResponse.Row::getOutcome)
                .containsExactly("CREATED", "CREATED");
        // The new rule's id comes back, so the UI can link straight to it.
        assertThat(result.getResults()).allSatisfy(r -> assertThat(r.getRuleId()).isNotNull());
    }

    @Test
    @DisplayName("a product that already has this alert is skipped, not duplicated")
    void skipsDuplicates() {
        catalogue(product(1L, TENANT, "AirPods 4"), product(2L, TENANT, "Dyson V8"));
        AlertRule existing = AlertRule.builder()
                .productId(1L).condition(AlertCondition.PRICE_DROP).build();
        existing.setId(55L);
        when(ruleRepo.findByProductIdInAndCondition(any(), any())).thenReturn(List.of(existing));

        BulkAlertRuleResponse result = service.createBulk(request(1L, 2L));

        assertThat(result.getCreated()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(1);

        BulkAlertRuleResponse.Row skipped = result.getResults().get(0);
        assertThat(skipped.getOutcome()).isEqualTo("SKIPPED_DUPLICATE");
        assertThat(skipped.getExistingRuleId()).isEqualTo(55L);
        assertThat(skipped.getMessage()).contains("price drop");
    }

    @Test
    @DisplayName("another client's product is reported as not found, and no rule is written")
    void ignoresOtherTenantsProducts() {
        catalogue(product(1L, TENANT, "Mine"), product(2L, OTHER_TENANT, "Theirs"));

        BulkAlertRuleResponse result = service.createBulk(request(1L, 2L));

        assertThat(result.getCreated()).isEqualTo(1);
        assertThat(result.getResults().get(1).getOutcome()).isEqualTo("NOT_FOUND");
        assertThat(result.getResults().get(1).getProductTitle()).isNull();
    }

    @Test
    @DisplayName("a product with no competitors still gets a rule, flagged as unable to fire")
    void flagsProductsWithoutCompetitors() {
        catalogue(product(1L, TENANT, "Lonely product"));
        when(listingRepo.countByProductIdAndMatchStatusNotAndLastPriceIsNotNull(eq(1L), any()))
                .thenReturn(0L);

        BulkAlertRuleResponse result = service.createBulk(request(1L));

        assertThat(result.getCreated()).isEqualTo(1);
        assertThat(result.getCreatedWithoutCompetitors()).isEqualTo(1);
        assertThat(result.getResults().get(0).isCannotFireYet()).isTrue();
        assertThat(result.getResults().get(0).getMessage()).contains("cannot fire");
    }

    @Test
    @DisplayName("asked to, it leaves products without competitors out entirely")
    void canSkipProductsWithoutCompetitors() {
        catalogue(product(1L, TENANT, "Lonely product"));
        when(listingRepo.countByProductIdAndMatchStatusNotAndLastPriceIsNotNull(eq(1L), any()))
                .thenReturn(0L);
        BulkAlertRuleRequest r = request(1L);
        r.setSkipProductsWithoutCompetitors(true);

        BulkAlertRuleResponse result = service.createBulk(r);

        assertThat(result.getCreated()).isZero();
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getResults().get(0).getOutcome()).isEqualTo("SKIPPED_NO_COMPETITORS");
    }

    @Test
    @DisplayName("a product whose listings were all rejected counts as having none")
    void rejectedListingsDoNotCount() {
        catalogue(product(1L, TENANT, "Everything rejected"));
        // Rows exist, but the alert engine skips rejected listings, so there is
        // nothing left for a rule to compare against.
        when(listingRepo.countByProductIdAndMatchStatusNotAndLastPriceIsNotNull(eq(1L), any()))
                .thenReturn(0L);

        BulkAlertRuleResponse result = service.createBulk(request(1L));

        assertThat(result.getCreatedWithoutCompetitors()).isEqualTo(1);
        assertThat(result.getResults().get(0).isCannotFireYet()).isTrue();
    }

    @Test
    @DisplayName("listings with no price do not count, however many there are")
    void unpricedListingsDoNotCount() {
        // The case that reached the client: 17 candidates and a confirmed match,
        // not one of them carrying a price. Twenty rows, nothing to compare.
        catalogue(product(1L, TENANT, "TP-Link Archer AX73 Router"));
        when(listingRepo.countByProductIdAndMatchStatusNotAndLastPriceIsNotNull(eq(1L), any()))
                .thenReturn(0L);

        BulkAlertRuleResponse result = service.createBulk(request(1L));

        assertThat(result.getCreatedWithoutCompetitors()).isEqualTo(1);
        assertThat(result.getResults().get(0).isCannotFireYet()).isTrue();
    }

    @Test
    @DisplayName("unreviewed candidates do count when priced — the engine evaluates them")
    void unreviewedCandidatesCount() {
        catalogue(product(1L, TENANT, "Has candidates"));
        when(listingRepo.countByProductIdAndMatchStatusNotAndLastPriceIsNotNull(eq(1L), any()))
                .thenReturn(4L);

        BulkAlertRuleResponse result = service.createBulk(request(1L));

        assertThat(result.getCreatedWithoutCompetitors()).isZero();
        assertThat(result.getResults().get(0).isCannotFireYet()).isFalse();
    }

    @Test
    @DisplayName("the same product listed twice produces one rule, not two")
    void deduplicatesTheRequestItself() {
        catalogue(product(1L, TENANT, "AirPods 4"));

        BulkAlertRuleResponse result = service.createBulk(request(1L, 1L, 1L));

        assertThat(result.getRequested()).isEqualTo(1);
        assertThat(result.getCreated()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unknown channel fails the whole request, not each product separately")
    void badChannelFailsTheRequest() {
        catalogue(product(1L, TENANT, "AirPods 4"));
        BulkAlertRuleRequest r = request(1L);
        r.setChannels(List.of("IN_APP", "CARRIER_PIGEON"));

        assertThatThrownBy(() -> service.createBulk(r))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CARRIER_PIGEON");

        verify(ruleRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("an oversized batch is refused with the limit stated")
    void refusesOversizedBatch() {
        List<Long> tooMany = LongStream.rangeClosed(1, 501).boxed().toList();
        BulkAlertRuleRequest r = BulkAlertRuleRequest.builder()
                .productIds(tooMany).condition(AlertCondition.PRICE_DROP).build();

        assertThatThrownBy(() -> service.createBulk(r))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("an empty selection is refused rather than reported as zero created")
    void refusesEmptySelection() {
        BulkAlertRuleRequest r = BulkAlertRuleRequest.builder()
                .productIds(List.of()).condition(AlertCondition.PRICE_DROP).build();

        assertThatThrownBy(() -> service.createBulk(r))
                .isInstanceOf(BadRequestException.class);
    }

    // ---------- bulk delete / pause ----------

    @Test
    @DisplayName("bulk delete removes only the rules this client owns")
    void bulkDeleteIsTenantScoped() {
        AlertRule mine = AlertRule.builder().productId(1L).tenantId(TENANT).build();
        when(ruleRepo.findByIdInAndTenantId(List.of(10L, 11L), TENANT)).thenReturn(List.of(mine));

        assertThat(service.deleteBulk(List.of(10L, 11L))).isEqualTo(1);
        verify(ruleRepo).deleteAll(List.of(mine));
    }

    @Test
    @DisplayName("pausing keeps the rule and only clears its active flag")
    void bulkPause() {
        AlertRule rule = AlertRule.builder().productId(1L).tenantId(TENANT).active(true).build();
        when(ruleRepo.findByIdInAndTenantId(any(), any())).thenReturn(List.of(rule));

        assertThat(service.setActiveBulk(List.of(10L), false)).isEqualTo(1);

        assertThat(rule.isActive()).isFalse();
        verify(ruleRepo).saveAll(List.of(rule));
        verify(ruleRepo, never()).deleteAll(any());
    }
}
