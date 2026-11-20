package com.priceintel.backend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.constants.PaymentStatus;
import com.priceintel.backend.constants.PaymentType;
import com.priceintel.backend.entity.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Payment> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    /** Payments in a status that have a Stripe session — for reconciliation. */
    List<Payment> findByStatusAndStripeSessionIdIsNotNull(PaymentStatus status);

    /**
     * Payment history with the filters the screen offers. Any null is ignored.
     *
     * <p>Filtered and ordered on {@code createdAt} — the date the row shows.
     * {@code paidAt} is when the money arrived, which for a pending payment is
     * never, and filtering on it would drop exactly the unpaid rows someone
     * opens this page to find.</p>
     *
     * <p>{@code to} is exclusive, so a month is expressed as the first instant
     * of that month up to the first instant of the next. An inclusive end would
     * have to name the last microsecond of the month to avoid losing a payment
     * made at 23:59.</p>
     *
     * <p>The null checks are cast deliberately. A parameter PostgreSQL only
     * ever sees inside {@code ? IS NULL} has no type it can infer, and the
     * whole query fails with "could not determine data type of parameter" —
     * at run time, on the first unfiltered request, not at startup. The cast
     * gives it one.</p>
     *
     * @param tenantId null for the platform owner, who sees every client
     */
    @Query("SELECT p FROM Payment p WHERE "
            + "(CAST(:tenantId AS Long) IS NULL OR p.tenantId = :tenantId) AND "
            + "(:status IS NULL OR p.status = :status) AND "
            + "(:type IS NULL OR p.type = :type) AND "
            + "(CAST(:from AS LocalDateTime) IS NULL OR p.createdAt >= :from) AND "
            + "(CAST(:to AS LocalDateTime) IS NULL OR p.createdAt < :to) "
            + "ORDER BY p.createdAt DESC")
    Page<Payment> search(
            @Param("tenantId") Long tenantId,
            @Param("status") PaymentStatus status,
            @Param("type") PaymentType type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /**
     * Status, currency, count and total for the same filters — the figures above
     * the table.
     *
     * <p>Grouped by currency as well as status because summing two currencies
     * into one number would state something false. One row per pair; the service
     * decides what can honestly be shown.</p>
     */
    @Query("SELECT p.status, p.currency, COUNT(p), COALESCE(SUM(p.amount), 0) FROM Payment p WHERE "
            + "(CAST(:tenantId AS Long) IS NULL OR p.tenantId = :tenantId) AND "
            + "(:status IS NULL OR p.status = :status) AND "
            + "(:type IS NULL OR p.type = :type) AND "
            + "(CAST(:from AS LocalDateTime) IS NULL OR p.createdAt >= :from) AND "
            + "(CAST(:to AS LocalDateTime) IS NULL OR p.createdAt < :to) "
            + "GROUP BY p.status, p.currency")
    List<Object[]> summarise(
            @Param("tenantId") Long tenantId,
            @Param("status") PaymentStatus status,
            @Param("type") PaymentType type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * The months that actually hold a payment, newest first, as {@code YYYY-MM}.
     *
     * <p>So the month picker offers the months there is something to see rather
     * than every month since the account opened. Native because month
     * truncation is not JPQL, and deliberately unfiltered by status or type: a
     * month must not vanish from the list because of a filter the user is about
     * to change.</p>
     */
    @Query(value = "SELECT DISTINCT to_char(created_at, 'YYYY-MM') AS m FROM payments "
            + "WHERE (CAST(:tenantId AS bigint) IS NULL OR tenant_id = CAST(:tenantId AS bigint)) "
            + "AND created_at IS NOT NULL "
            + "ORDER BY m DESC", nativeQuery = true)
    List<String> monthsWithPayments(@Param("tenantId") Long tenantId);
}
