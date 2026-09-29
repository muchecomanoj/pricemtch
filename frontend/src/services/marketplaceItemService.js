import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'

// Tracked marketplace items and their price movements.
//
// Every listing the judge accepts as a real product is followed from then on:
// searching the same term again updates the row rather than adding a second
// one, and writes an event when something actually moved. That is the whole
// point — no marketplace will sell yesterday's price back, so a price not
// captured on the day it was seen is gone.
//
// Two counts do the work on screen and only together: `observationCount` says
// how often we looked, `changeCount` how often it moved. "Checked 12 times,
// moved twice" means the price is stable. Twelve and twelve means it is
// volatile. One and zero means nothing is known yet — and without both numbers
// all three look identical.

const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }

export const marketplaceItemService = {
  // GET /marketplace-items -> Page<MarketplaceItem>
  async list({ marketplace, search, page = 0, size = 20 } = {}) {
    if (USE_MOCK) return EMPTY_PAGE
    const { data } = await api.get(ENDPOINTS.marketplaceItems.list, {
      params: {
        marketplace: marketplace || undefined,
        search: search?.trim() || undefined,
        page,
        size,
      },
    })
    return data || EMPTY_PAGE
  },

  // One storefront's copy of the listing. The same ASIN in two storefronts is
  // two rows now, so `storefront` says which one is wanted; omitted, the
  // backend returns the most recently seen and names it in the response.
  async detail(marketplace, itemId, { storefront } = {}) {
    if (USE_MOCK) return null
    const { data } = await api.get(ENDPOINTS.marketplaceItems.detail(marketplace, itemId),
      { params: { storefront: storefront || undefined } })
    return data
  },

  // Plain array, NOT a page — the only route here that isn't wrapped.
  //
  // Consecutive points are guaranteed to differ: repeats are no longer stored,
  // so a flat stretch means the price held rather than that the same number was
  // written six times. Plot it stepped for the same reason — a price sits at a
  // value and jumps; it never glides between two.
  async priceHistory(marketplace, itemId, { from, to, storefront } = {}) {
    if (USE_MOCK) return []
    const { data } = await api.get(
      ENDPOINTS.marketplaceItems.priceHistory(marketplace, itemId),
      { params: { from, to, storefront: storefront || undefined } },
    )
    return Array.isArray(data) ? data : []
  },

  // GET /marketplace-items/{mp}/{id}/changes -> Page<PriceChangeEvent>
  async itemChanges(marketplace, itemId, { page = 0, size = 20, storefront } = {}) {
    if (USE_MOCK) return EMPTY_PAGE
    const { data } = await api.get(
      ENDPOINTS.marketplaceItems.itemChanges(marketplace, itemId),
      { params: { page, size, storefront: storefront || undefined } },
    )
    return data || EMPTY_PAGE
  },

  // GET /marketplace-items/changes -> Page<PriceChangeEvent> across every item.
  // `from` is an ISO instant; `field` is PRICE | SHIPPING | LANDED_PRICE | AVAILABILITY.
  // `search` matches the listing's title, any identifier it carries and the
  // item id itself, applied in the database before paging — so the counts and
  // page 2 are right, which a filter applied to the fetched page never is.
  async changes({ marketplace, field, from, search, page = 0, size = 20 } = {}) {
    if (USE_MOCK) return EMPTY_PAGE
    const { data } = await api.get(ENDPOINTS.marketplaceItems.changes, {
      params: {
        marketplace: marketplace || undefined,
        field: field || undefined,
        from: from || undefined,
        search: search?.trim() || undefined,
        page,
        size,
      },
    })
    return data || EMPTY_PAGE
  },
}
