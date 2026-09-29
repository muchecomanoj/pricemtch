package com.priceintel.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An inline candidate to match a product against (when the candidate is not a
 * stored product). All fields optional; missing ones become "missing evidence".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchCandidate {
    private String brand;
    private String title;
    private String model;
    private String color;
    private String variant;
    private Integer packQuantity;
    private String imageUrl;
}
