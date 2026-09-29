package com.priceintel.backend.entity;

import com.priceintel.backend.constants.ImageStatus;

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
 * An image associated with a product, with a hash (for de-duplication /
 * evidence) and a review status.
 */
@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** URL or base64 data: URI (direct upload) — TEXT, no length cap. */
    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(length = 128)
    private String hash;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ImageStatus status = ImageStatus.PENDING;
}
