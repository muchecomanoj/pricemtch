package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Search products by image (base64 data: URI). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageSearchRequest {
    @NotBlank(message = "Image is required")
    private String image;
}
