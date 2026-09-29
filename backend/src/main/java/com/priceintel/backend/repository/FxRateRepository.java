package com.priceintel.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.priceintel.backend.entity.FxRate;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    /**
     * The newest rate for a pair that was already true at {@code at}.
     *
     * <p>Deliberately "at or before" rather than "nearest": converting a figure
     * at a rate that did not exist yet would let a historical report change
     * shape as later rates arrive.</p>
     */
    @Query("""
            SELECT f FROM FxRate f
            WHERE f.baseCurrency = :base AND f.quoteCurrency = :quote AND f.asOf <= :at
            ORDER BY f.asOf DESC
            """)
    List<FxRate> findLatestAtOrBefore(@Param("base") String base,
                                      @Param("quote") String quote,
                                      @Param("at") Instant at,
                                      Pageable pageable);

    Optional<FxRate> findByBaseCurrencyAndQuoteCurrencyAndAsOf(
            String baseCurrency, String quoteCurrency, Instant asOf);

    List<FxRate> findAllByOrderByAsOfDesc(Pageable pageable);
}
