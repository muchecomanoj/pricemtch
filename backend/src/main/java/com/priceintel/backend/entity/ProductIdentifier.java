package com.priceintel.backend.entity;

import com.priceintel.backend.constants.IdentifierType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An external identifier for a product (ASIN, UPC, EAN, GTIN, MPN, or a
 * marketplace id). We keep BOTH the original value (as supplied) and a
 * normalized value (trimmed/upper-cased/stripped) for reliable matching.
 */
@Entity
@Table(name = "product_identifiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductIdentifier extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdentifierType type;

    @Column(name = "original_value", nullable = false, length = 200)
    private String originalValue;

    @Column(name = "normalized_value", length = 200)
    private String normalizedValue;

    /** Which marketplace this id belongs to (used mainly for MARKETPLACE_ID). */
    @Column(length = 100)
    private String marketplace;

    /**
     * The owning tenant, copied from the product.
     *
     * <p>Denormalised so the database can enforce "one identifier, one product
     * per tenant" with a unique index. Postgres cannot apply uniqueness across a
     * join, and the application-level check it replaces was a check-then-insert
     * race that a concurrent import could slip through.</p>
     */
    @Column(name = "tenant_id")
    private Long tenantId;
}
