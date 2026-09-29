package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** { "enabled": true|false } */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorStatusResponse {
    private boolean enabled;
}
