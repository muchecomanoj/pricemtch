package com.priceintel.backend.dto.response;

import com.priceintel.backend.constants.ImageStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductImageResponse {

    private Long id;
    private String url;
    private String hash;
    private ImageStatus status;
}
