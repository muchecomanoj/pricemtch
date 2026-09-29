package com.priceintel.backend.dto.request;

import com.priceintel.backend.constants.ImageStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageRequest {

    /** A URL or a base64 data: URI (direct upload) — no length cap. */
    @NotBlank(message = "Image URL is required")
    private String url;

    @Size(max = 128)
    private String hash;

    /** Optional; defaults to PENDING when omitted. */
    private ImageStatus status;
}
