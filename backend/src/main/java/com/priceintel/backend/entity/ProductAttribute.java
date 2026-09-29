package com.priceintel.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Descriptive attributes for a product variant (color, size, material, etc.).
 * A product can have several attribute rows (e.g. one per variant).
 */
@Entity
@Table(name = "product_attributes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductAttribute extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 100)
    private String color;

    @Column(length = 100)
    private String size;

    @Column(length = 150)
    private String material;

    @Column(length = 150)
    private String model;

    @Column(length = 150)
    private String variant;

    @Column(length = 100)
    private String country;

    @Column(length = 150)
    private String category;
}
