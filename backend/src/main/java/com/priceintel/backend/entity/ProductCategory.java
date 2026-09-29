package com.priceintel.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A product category (e.g. "Electronics", "Home & Kitchen"). Kept as its own
 * table so categories can be reused and reported on.
 */
@Entity
@Table(name = "product_categories", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_categories_name", columnNames = "name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCategory extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;
}
