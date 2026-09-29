package com.priceintel.backend.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.priceintel.backend.constants.ProductCondition;
import com.priceintel.backend.constants.ProductStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The product master record. Aggregates identifiers, images, and attributes as
 * child collections (cascade + orphan removal, so they live and die with the
 * product).
 */
@Entity
// SKU is unique per company, case-insensitively: index uk_products_tenant_sku
// on (tenant_id, lower(sku)), created in V19. JPA cannot express lower(), so
// it lives in the migration only.
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 150)
    private String brand;

    @Column(length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;

    @Builder.Default
    @Column(name = "pack_quantity", nullable = false)
    private Integer packQuantity = 1;

    /**
     * Condition we sell in — NEW unless stated, which is the common case.
     *
     * <p>Mapped to {@code product_condition}: "condition" is a SQL reserved
     * word, and an unquoted column of that name is not created reliably.</p>
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "product_condition", nullable = false, length = 20)
    private ProductCondition condition = ProductCondition.NEW;

    /** Our current selling price (the profitability/recommendation anchor). */
    @Column(name = "our_price", precision = 12, scale = 2)
    private BigDecimal ourPrice;

    @Column(precision = 12, scale = 3)
    private BigDecimal weight;

    @Column(precision = 12, scale = 3)
    private BigDecimal height;

    @Column(precision = 12, scale = 3)
    private BigDecimal width;

    @Column(precision = 12, scale = 3)
    private BigDecimal length;

    /** Optional owning tenant (multi-tenant SaaS); isolation not yet enforced. */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductIdentifier> identifiers = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductAttribute> attributes = new ArrayList<>();

    // ---- relationship helpers keep both sides of the link in sync ----

    public void addIdentifier(ProductIdentifier identifier) {
        identifier.setProduct(this);
        this.identifiers.add(identifier);
    }

    public void addImage(ProductImage image) {
        image.setProduct(this);
        this.images.add(image);
    }

    public void addAttribute(ProductAttribute attribute) {
        attribute.setProduct(this);
        this.attributes.add(attribute);
    }

    public void clearIdentifiers() {
        this.identifiers.clear();
    }

    public void clearImages() {
        this.images.clear();
    }

    public void clearAttributes() {
        this.attributes.clear();
    }
}
