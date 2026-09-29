package com.priceintel.backend.marketplace;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.priceintel.backend.exception.BadRequestException;

/**
 * Factory that returns the right {@link MarketplaceAdapter} for a marketplace
 * (Factory + Strategy patterns). Spring injects every adapter bean, and the
 * factory indexes them by the marketplace each one declares — so adding a new
 * marketplace is just adding a new adapter, no change here.
 */
@Component
public class MarketplaceAdapterFactory {

    private final Map<Marketplace, MarketplaceAdapter> adapters = new EnumMap<>(Marketplace.class);

    public MarketplaceAdapterFactory(List<MarketplaceAdapter> adapterBeans) {
        for (MarketplaceAdapter adapter : adapterBeans) {
            adapters.put(adapter.getMarketplace(), adapter);
        }
    }

    /** Returns the adapter for the marketplace, or 400 if none is registered. */
    public MarketplaceAdapter getAdapter(Marketplace marketplace) {
        MarketplaceAdapter adapter = adapters.get(marketplace);
        if (adapter == null) {
            throw new BadRequestException("No adapter registered for marketplace: " + marketplace);
        }
        return adapter;
    }

    public Set<Marketplace> getSupportedMarketplaces() {
        return adapters.keySet();
    }
}
