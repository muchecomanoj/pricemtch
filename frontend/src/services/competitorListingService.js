import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'

// Tenant-wide competitor listings. This page only READS what search jobs have
// already stored — it never calls a marketplace itself, so a tenant that has
// never run "Find competitors" on a product sees an empty list. That is
// expected, not an error.
//
// api.js unwraps { success, message, data }, so `data` here is the page object.
// Paging is 0-based on the wire and the current page comes back as `number`;
// the UI's Pagination component is 1-based, so the page converts.

const EMPTY = { content: [], totalElements: 0, totalPages: 1, number: 0, size: 20 }

export const competitorListingService = {
  // Review queue: pending candidates only, already scored and ranked.
  //   sort=score  (default) best-first   sort=lowest  riskiest-first
  //   sort=recent newest-first
  async reviewQueue({ page = 0, size = 20, sort = 'score', productId } = {}) {
    if (USE_MOCK) return EMPTY
    const params = { page, size, sort }
    if (productId) params.productId = productId
    const { data } = await api.get(ENDPOINTS.matchReview, { params })
    if (!data) return EMPTY
    return {
      content: data.content || [],
      totalElements: data.totalElements ?? 0,
      totalPages: data.totalPages ?? 1,
      number: data.number ?? page,
      size: data.size ?? size,
    }
  },

  async list({ page = 0, size = 20, search, marketplace, condition, matchStatus } = {}) {
    if (USE_MOCK) return EMPTY
    // Only send filters that actually have a value — empty strings would
    // otherwise be treated as a filter for "".
    const params = { page, size }
    if (search) params.search = search
    if (marketplace) params.marketplace = marketplace
    if (condition) params.condition = condition
    if (matchStatus) params.matchStatus = matchStatus

    const { data } = await api.get(ENDPOINTS.competitorListings, { params })
    if (!data) return EMPTY
    return {
      content: data.content || [],
      totalElements: data.totalElements ?? 0,
      totalPages: data.totalPages ?? 1,
      number: data.number ?? page,
      size: data.size ?? size,
    }
  },

  // Attach a marketplace listing to one of our products as a candidate.
  //
  // Everything travels as query params — the body is deliberately null. Only
  // the item id is taken from the request; title, price and availability are
  // fetched live from the marketplace, because a hand-typed price would be
  // indistinguishable from an observed one afterwards and every downstream
  // statistic assumes observation.
  //
  // `decision` is optional — MATCHED, EQUIVALENT or REJECTED. Omit it and the
  // listing lands as a CANDIDATE for later review, which is still the default
  // and still the right answer for anything a human has not confirmed.
  //
  // Passing it used to be unsafe, because saving as matched would have skipped
  // the pack-size and condition rules. The backend now runs those rules on this
  // call and returns 400 with a reason when they block, so a verdict the model
  // reached can carry through in one request instead of attach-then-review.
  // `region` is the storefront the listing was FOUND in — pass the search
  // result's `storefront` straight through. Without it the backend re-fetches
  // from whichever storefront answers first, which is how a CA$140 listing
  // came back as a US$144.29 one. Optional on the API for compatibility; there
  // is no good reason to omit it when it is known.
  async attach(productId, { marketplace, marketplaceItemId, decision, region }) {
    if (USE_MOCK) return { id: 0, matchStatus: decision || 'CANDIDATE' }
    const { data } = await api.post(
      ENDPOINTS.productIntel.attachListing(productId),
      null,
      // Uppercase is what the API documents, though it now matches either way.
      {
        params: {
          marketplace: String(marketplace).toUpperCase(),
          marketplaceItemId,
          region: region || undefined,
          decision: decision || undefined,
        },
      },
    )
    return data
  },
}
