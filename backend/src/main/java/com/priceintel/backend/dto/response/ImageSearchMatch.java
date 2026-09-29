package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One product matched by image similarity. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageSearchMatch {
    private Long productId;
    private String sku;
    private String title;
    private String brand;
    private String category;
    private String imageUrl;
    private int similarityPct;   // 0-100
    private int distance;        // Hamming distance (lower = closer)
}
