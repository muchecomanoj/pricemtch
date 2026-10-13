package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.priceintel.backend.dto.request.BulkMonitorRequest;
import com.priceintel.backend.entity.Monitor;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.SubscriptionPlan;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.MonitorRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.impl.MonitorService;

/**
 * Monitoring several products at once.
 *
 * <p>Two rules carry the weight here. Settings that are wrong for the whole
 * request must write nothing, so a failure never leaves half the monitors to be
 * undone by hand. And a product that cannot be monitored must not fail the
 * others, because "monitor these twelve" when three are already covered is an
 * ordinary thing to ask.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkMonitorTest {

    @Mock MonitorRepository monitorRepo;
    @Mock ProductRepository productRepo;
    @Mock TenantRepository tenantRepo;
    @Mock SubscriptionPlanRepository planRepo;

    @InjectMocks MonitorService service;

    private static final long TENANT = 3L;
    private final AtomicLong nextId = new AtomicLong(1);

    @org.junit.jupiter.api.AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT, false);

        Tenant tenant = new Tenant();
        tenant.setId(TENANT);
        tenant.setSubscriptionPlan("PRO");
        when(tenantRepo.findById(TENANT)).thenReturn(Optional.of(tenant));

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode("PRO");
        plan.setMinIntervalMinutes(360);          // the plan's floor
        when(planRepo.findByCodeIgnoreCase("PRO")).thenReturn(Optional.of(plan));

        when(monitorRepo.findByProductId(any())).thenReturn(Optional.empty());
        when(monitorRepo.saveAll(any())).thenAnswer(i -> {
            List<Monitor> monitors = i.getArgument(0);
            if (monitors == null) {
                return List.of();
            }
            // Only new rows get an id, so a bulk pause or resume keeps the ones
            // it was given rather than being renumbered.
            monitors.stream().filter(m -> m.getId() == null)
                    .forEach(m -> m.setId(nextId.getAndIncrement()));
            return monitors;
        });
    }

    private void productsExist(Long... ids) {
        for (Long id : ids) {
            Product p = new Product();
            p.setId(id);
            p.setTenantId(TENANT);
            p.setTitle("Product " + id);
            when(productRepo.findById(id)).thenReturn(Optional.of(p));
        }
    }

    private static BulkMonitorRequest request(List<Long> ids) {
        return BulkMonitorRequest.builder().productIds(ids).intervalMinutes(360).build();
    }

    @Test
    @DisplayName("every eligible product gets a monitor")
    void createsOnePerProduct() {
        productsExist(1L, 2L, 3L);

        var result = service.createBulk(request(List.of(1L, 2L, 3L)));

        assertThat(result.getCreated()).hasSize(3);
        assertThat(result.getSkipped()).isEmpty();
    }

    @Test
    @DisplayName("an already-monitored product is skipped, and the rest are still created")
    void alreadyMonitoredIsSkippedNotFatal() {
        productsExist(1L, 2L, 3L);
        when(monitorRepo.findByProductId(2L)).thenReturn(Optional.of(new Monitor()));

        var result = service.createBulk(request(List.of(1L, 2L, 3L)));

        assertThat(result.getCreated()).hasSize(2);
        assertThat(result.getSkipped()).singleElement()
                .satisfies(s -> {
                    assertThat(s.getProductId()).isEqualTo(2L);
                    assertThat(s.getReason()).contains("Already monitored");
                });
    }

    @Test
    @DisplayName("a product from another company is skipped without saying it exists")
    void foreignProductIsSkipped() {
        productsExist(1L);
        Product theirs = new Product();
        theirs.setId(9L);
        theirs.setTenantId(99L);
        when(productRepo.findById(9L)).thenReturn(Optional.of(theirs));

        var result = service.createBulk(request(List.of(1L, 9L)));

        assertThat(result.getCreated()).hasSize(1);
        assertThat(result.getSkipped()).singleElement()
                .satisfies(s -> assertThat(s.getReason()).isEqualTo("No such product in your catalogue."));
    }

    @Test
    @DisplayName("an interval below the plan's floor creates nothing at all")
    void badIntervalWritesNothing() {
        productsExist(1L, 2L);

        assertThatThrownBy(() -> service.createBulk(BulkMonitorRequest.builder()
                .productIds(List.of(1L, 2L)).intervalMinutes(5).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("360");

        // The point of checking shared settings first: a half-done request
        // leaves the user to find and delete whatever did get created.
        verify(monitorRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("an unknown marketplace creates nothing at all")
    void badMarketplaceWritesNothing() {
        productsExist(1L);

        assertThatThrownBy(() -> service.createBulk(BulkMonitorRequest.builder()
                .productIds(List.of(1L)).intervalMinutes(360)
                .markets(List.of("AMAZON", "FLIPKART")).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("FLIPKART");

        verify(monitorRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("maxResults outside 1-50 creates nothing at all")
    void badMaxResultsWritesNothing() {
        productsExist(1L);

        assertThatThrownBy(() -> service.createBulk(BulkMonitorRequest.builder()
                .productIds(List.of(1L)).intervalMinutes(360).maxResults(500).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("between 1 and 50");

        verify(monitorRepo, never()).saveAll(any());
    }

    @Test
    @DisplayName("first runs are spread across one interval instead of all firing together")
    void firstRunsAreSpread() {
        Long[] ids = IntStream.rangeClosed(1, 10).mapToObj(Long::valueOf).toArray(Long[]::new);
        productsExist(ids);
        Instant before = Instant.now();

        var result = service.createBulk(request(List.of(ids)));

        List<Instant> runs = result.getCreated().stream()
                .map(m -> m.getNextRunAt()).sorted().toList();

        // The first is still due now, so the user sees it working.
        assertThat(runs.get(0)).isBetween(before.minusSeconds(2), before.plusSeconds(5));
        // The last is inside the interval, not beyond it: spreading must not
        // delay anything by more than one cycle.
        assertThat(runs.get(runs.size() - 1))
                .isBefore(before.plus(Duration.ofMinutes(360)));
        // And they are genuinely apart, which is the whole point.
        assertThat(runs.get(runs.size() - 1)).isAfter(runs.get(0).plusSeconds(60));
    }

    @Test
    @DisplayName("a single monitor is due immediately — there is nothing to spread")
    void oneMonitorIsDueNow() {
        productsExist(1L);
        Instant before = Instant.now();

        var result = service.createBulk(request(List.of(1L)));

        assertThat(result.getCreated().get(0).getNextRunAt())
                .isBetween(before.minusSeconds(2), before.plusSeconds(5));
    }

    @Test
    @DisplayName("the same product listed twice produces one monitor")
    void duplicatesAreCollapsed() {
        productsExist(1L);

        var result = service.createBulk(request(List.of(1L, 1L, 1L)));

        assertThat(result.getCreated()).hasSize(1);
    }

    @Test
    @DisplayName("the settings given are applied to every monitor")
    void settingsAreShared() {
        productsExist(1L, 2L);

        var result = service.createBulk(BulkMonitorRequest.builder()
                .productIds(List.of(1L, 2L)).intervalMinutes(720)
                .markets(List.of("amazon")).maxResults(12).enabled(false).build());

        assertThat(result.getCreated()).allSatisfy(m -> {
            assertThat(m.getIntervalMinutes()).isEqualTo(720);
            assertThat(m.getMaxResults()).isEqualTo(12);
            assertThat(m.isEnabled()).isFalse();
            assertThat(m.getMarkets()).containsExactly("AMAZON");
        });
    }

    @Test
    @DisplayName("a list of nothing but unusable products is a 200 with everything skipped")
    void allSkippedIsStillSuccess() {
        Product theirs = new Product();
        theirs.setId(9L);
        theirs.setTenantId(99L);
        when(productRepo.findById(9L)).thenReturn(Optional.of(theirs));

        var result = service.createBulk(request(List.of(9L)));

        // Nothing went wrong with the request itself, so it is not an error —
        // the answer is simply that nothing could be monitored, and why.
        assertThat(result.getCreated()).isEmpty();
        assertThat(result.getSkipped()).hasSize(1);
    }

    // ---------- pausing, resuming and deleting several at once ----------

    private Monitor monitor(long id, boolean enabled) {
        Monitor m = Monitor.builder().tenantId(TENANT).productId(id)
                .intervalMinutes(360).maxResults(5).enabled(enabled).build();
        m.setId(id);
        return m;
    }

    /** Only the caller's own ids come back, exactly as the repository would. */
    private void monitorsExist(Monitor... monitors) {
        when(monitorRepo.findByIdInAndTenantId(any(), org.mockito.ArgumentMatchers.eq(TENANT)))
                .thenAnswer(i -> {
                    List<Long> asked = i.getArgument(0);
                    return java.util.Arrays.stream(monitors)
                            .filter(m -> asked.contains(m.getId())).toList();
                });
    }

    @Test
    @DisplayName("several monitors are paused at once")
    void pauseMany() {
        Monitor a = monitor(1L, true);
        Monitor b = monitor(2L, true);
        monitorsExist(a, b);

        assertThat(service.setEnabledBulk(List.of(1L, 2L), false)).isEqualTo(2);
        assertThat(a.isEnabled()).isFalse();
        assertThat(b.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("several monitors are resumed at once, with their next runs spread out")
    void resumeManySpreadsTheirRuns() {
        Monitor a = monitor(1L, false);
        Monitor b = monitor(2L, false);
        Monitor c = monitor(3L, false);
        monitorsExist(a, b, c);
        Instant before = Instant.now();

        assertThat(service.setEnabledBulk(List.of(1L, 2L, 3L), true)).isEqualTo(3);

        assertThat(a.isEnabled()).isTrue();
        // The first resumes immediately so the user sees it working again.
        assertThat(a.getNextRunAt()).isBetween(before.minusSeconds(2), before.plusSeconds(5));
        // A mass resume must not put them all on the same schedule for ever.
        assertThat(b.getNextRunAt()).isAfter(a.getNextRunAt());
        assertThat(c.getNextRunAt()).isAfter(b.getNextRunAt());
        assertThat(c.getNextRunAt()).isBefore(before.plus(Duration.ofMinutes(360)));
    }

    @Test
    @DisplayName("pausing does not touch the schedule — it just stops them running")
    void pauseLeavesTheScheduleAlone() {
        Monitor a = monitor(1L, true);
        Instant scheduled = Instant.now().plusSeconds(1234);
        a.setNextRunAt(scheduled);
        monitorsExist(a);

        service.setEnabledBulk(List.of(1L), false);

        assertThat(a.getNextRunAt()).isEqualTo(scheduled);
    }

    @Test
    @DisplayName("several monitors are deleted at once")
    void deleteMany() {
        Monitor a = monitor(1L, true);
        Monitor b = monitor(2L, true);
        monitorsExist(a, b);

        assertThat(service.deleteBulk(List.of(1L, 2L))).isEqualTo(2);
        verify(monitorRepo).deleteAll(List.of(a, b));
    }

    @Test
    @DisplayName("a list mixing real and missing ids reports only the real count")
    void missingIdsAreIgnored() {
        monitorsExist(monitor(1L, true));

        // A selection can go stale between loading the page and clicking, and
        // failing the whole action over one dead id helps nobody.
        assertThat(service.setEnabledBulk(List.of(1L, 404L, 405L), false)).isEqualTo(1);
        assertThat(service.deleteBulk(List.of(1L, 404L))).isEqualTo(1);
    }

    @Test
    @DisplayName("another company's monitors are never touched")
    void otherCompaniesAreUntouched() {
        Monitor theirs = Monitor.builder().tenantId(99L).productId(9L)
                .intervalMinutes(360).enabled(true).build();
        theirs.setId(9L);
        // Scoped in the query, so their id simply does not come back.
        monitorsExist(monitor(1L, true));

        assertThat(service.setEnabledBulk(List.of(1L, 9L), false)).isEqualTo(1);
        assertThat(theirs.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("more than 200 ids is refused before anything is changed")
    void tooManyIsRefused() {
        List<Long> tooMany = IntStream.rangeClosed(1, 201).mapToObj(Long::valueOf).toList();

        assertThatThrownBy(() -> service.setEnabledBulk(tooMany, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("200");
        assertThatThrownBy(() -> service.deleteBulk(tooMany))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("200");

        verify(monitorRepo, never()).saveAll(any());
        verify(monitorRepo, never()).deleteAll(any());
    }

    @Test
    @DisplayName("an empty selection is refused rather than quietly doing nothing")
    void emptySelectionIsRefused() {
        assertThatThrownBy(() -> service.deleteBulk(List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one");
    }
}
