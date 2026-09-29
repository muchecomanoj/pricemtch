package com.priceintel.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A company has seen this marketplace listing in one of its searches.
 *
 * <p>The listing and its price history are shared platform-wide; this row is
 * what makes a company's Price Changes feed its own. Written by
 * {@link com.priceintel.backend.repository.TenantTrackedItemRepository#touch},
 * never through JPA save, so repeat sightings update one row instead of racing
 * to insert two.</p>
 */
@Entity
@Table(name = "tenant_tracked_items")
@Getter
@Setter
@NoArgsConstructor
public class TenantTrackedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 20)
    private String marketplace;

    @Column(nullable = false, length = 2)
    private String storefront;

    @Column(name = "marketplace_item_id", nullable = false, length = 100)
    private String marketplaceItemId;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
