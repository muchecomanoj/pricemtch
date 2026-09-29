package com.priceintel.backend.marketplace.model;

import java.time.LocalDateTime;

import com.priceintel.backend.marketplace.Marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Health/status of a marketplace connector. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorHealth {
    private Marketplace marketplace;
    private boolean healthy;
    private String status;   // UP / DOWN / DEGRADED
    private String message;
    private LocalDateTime checkedAt;
    private boolean mocked;
}
