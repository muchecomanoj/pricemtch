package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.priceintel.backend.repository.PriceChangeEventRepository;

/**
 * How a Price Changes search term reaches the query.
 *
 * <p>Every parameter carries a sentinel rather than a null: Postgres cannot
 * infer the type of a bare null bound into a comparison and rejects the
 * statement outright. These pin the conversion, which is easy to break and
 * fails only at runtime.</p>
 */
class PriceChangeSearchTest {

    private final PriceChangeEventRepository repo =
            mock(PriceChangeEventRepository.class, withSettings().defaultAnswer(
                    org.mockito.Mockito.CALLS_REAL_METHODS));

    private String captureSearch(String term) {
        repo.recent(72L, "AMAZON", null, Instant.EPOCH, term, PageRequest.of(0, 20));
        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        verify(repo).feed(anyLong(), anyString(), anyString(), any(), search.capture(), any(Pageable.class));
        return search.getValue();
    }

    @Test
    @DisplayName("a term becomes a case-insensitive contains pattern")
    void termBecomesPattern() {
        assertThat(captureSearch("AirPods")).isEqualTo("%airpods%");
    }

    @Test
    @DisplayName("surrounding spaces are trimmed, so a pasted ASIN still matches")
    void termIsTrimmed() {
        assertThat(captureSearch("  B0DJMFS75S \n")).isEqualTo("%b0djmfs75s%");
    }

    @Test
    @DisplayName("no term sends the sentinel, which switches the filter off")
    void noTermSendsSentinel() {
        assertThat(captureSearch(null)).isEmpty();
    }

    @Test
    @DisplayName("a blank term is the same as no term, not a search for nothing")
    void blankTermIsNoFilter() {
        assertThat(captureSearch("   ")).isEmpty();
    }

    @Test
    @DisplayName("the older four-argument call still works and searches for nothing")
    void legacyCallKeepsWorking() {
        repo.recent(72L, "AMAZON", "PRICE", Instant.EPOCH, PageRequest.of(0, 20));

        ArgumentCaptor<String> search = ArgumentCaptor.forClass(String.class);
        verify(repo).feed(anyLong(), anyString(), anyString(), any(), search.capture(), any(Pageable.class));
        assertThat(search.getValue()).isEmpty();
    }

    @Test
    @DisplayName("the company reaches the query: a feed is never built without one")
    void companyScopeReachesQuery() {
        // Before, the feed had no company at all, and a brand-new client saw
        // 55 changes on products other clients had researched.
        repo.recent(72L, null, null, null, null, PageRequest.of(0, 20));
        verify(repo).feed(org.mockito.ArgumentMatchers.eq(72L), anyString(), anyString(), any(), anyString(),
                any(Pageable.class));
    }

    @Test
    @DisplayName("the other filters keep their own sentinels")
    void otherFiltersUnchanged() {
        repo.recent(72L, null, null, null, "airpods", PageRequest.of(0, 20));

        ArgumentCaptor<String> marketplace = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> field = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        verify(repo).feed(anyLong(), marketplace.capture(), field.capture(), from.capture(),
                anyString(), any(Pageable.class));

        assertThat(marketplace.getValue()).isEmpty();
        assertThat(field.getValue()).isEmpty();
        assertThat(from.getValue()).isEqualTo(Instant.EPOCH);
    }
}
