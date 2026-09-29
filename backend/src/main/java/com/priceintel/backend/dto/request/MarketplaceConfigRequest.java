package com.priceintel.backend.dto.request;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Save a marketplace's configuration. {@code credentials} is a key -> value map
 * matching the provider's fields (e.g. lwaClientId, lwaClientSecret, region).
 * Secret values left blank/absent keep the previously stored value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceConfigRequest {

    private boolean enabled;

    private Map<String, String> credentials;
}
