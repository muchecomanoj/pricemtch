import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { normalizeAsinLookup, EMPTY_ASIN_LOOKUP } from './productService'
import { mockResponse } from '../mock/mockHelper'
import { MARKETPLACES } from '../constants'

// What the search is allowed to cost in wall-clock, from the backend's measured
// figures. All three exceed the global 20s, so the call always carries its own.
//
//   plain           60s   channel calls only
//   judged          90s   + an AI pass over every candidate
//   with an image  120s   + a vision model describing the photo (~15s)
//
// Sized per request rather than pinned at the ceiling: a plain search that is
// going to fail should not sit for two minutes first.
//
// All raised by 30s for the Amazon page reader: an ASIN search on a storefront
// SP-API is not authorised for (GB, DE, IN) is read from the page instead, and
// that alone takes about twenty seconds. The old 60s judged ceiling cut those
// off just as they were answering.
const searchTimeout = ({ judge, image }) => (image ? 120000 : judge ? 90000 : 60000)


// Amazon money often arrives as { amount, currencyCode } rather than a number.
const money = (v) => {
  if (v == null) return null
  if (typeof v === 'number') return v
  if (typeof v === 'string') return Number.isNaN(+v) ? null : +v
  const n = v.amount ?? v.Amount ?? v.value
  return typeof n === 'number' ? n : (n != null && !Number.isNaN(+n) ? +n : null)
}
const currencyOf = (v) => (v && typeof v === 'object' ? v.currencyCode || v.CurrencyCode : null)
const truthy = (v) => (typeof v === 'string' ? /^(true|yes|amazon|afn)$/i.test(v) : !!v)

function normalizeOffer(o) {
  const pick = (...keys) => keys.map((k) => o?.[k]).find((v) => v != null)
  const price = pick('listingPrice', 'ListingPrice', 'price', 'Price', 'amount')
  const shipping = pick('shipping', 'shippingPrice', 'Shipping', 'ShippingPrice')
  return {
    sellerId: pick('sellerId', 'SellerId', 'merchantId', 'MerchantId') ?? null,
    sellerName: pick('sellerName', 'SellerName', 'seller', 'merchantName', 'Seller') ?? null,
    price: money(price),
    shipping: money(shipping),
    currency: currencyOf(price) || pick('currency', 'currencyCode', 'CurrencyCode') || 'USD',
    condition: pick('condition', 'Condition', 'subCondition', 'SubCondition') ?? null,
    // The one that matters: whoever holds this is the price a shopper sees.
    buyBox: truthy(pick('isBuyBoxWinner', 'IsBuyBoxWinner', 'buyBoxWinner', 'isFeaturedMerchant')),
    fba: truthy(pick('isFulfilledByAmazon', 'IsFulfilledByAmazon', 'fulfilledByAmazon',
      'isFba', 'fulfillmentChannel', 'FulfillmentChannel')),
    feedbackRating: money(pick('sellerPositiveFeedbackRating', 'positiveFeedbackRating', 'feedbackRating')),
    feedbackCount: money(pick('sellerFeedbackCount', 'feedbackCount', 'FeedbackCount')),
    raw: o,
  }
}

// LIVE marketplace endpoints (Phase 1 backend).
export const marketplaceService = {
  // GET /marketplaces -> configured channels
  async list() {
    if (USE_MOCK) {
      return (await mockResponse(
        MARKETPLACES.map((name) => ({ name, code: name.toUpperCase(), enabled: true, region: 'US' }))
      )).data
    }
    return (await api.get(ENDPOINTS.marketplaces.list)).data
  },

  // GET /marketplaces/health (all) or /marketplaces/{mp}/health
  async health(marketplace) {
    if (USE_MOCK) {
      return (await mockResponse(
        MARKETPLACES.map((name) => ({ marketplace: name, status: 'UP', latencyMs: 120 }))
      )).data
    }
    const url = marketplace ? ENDPOINTS.marketplaces.mpHealth(marketplace) : ENDPOINTS.marketplaces.health
    return (await api.get(url)).data
  },

  // POST /marketplaces/{mp}/search — MarketplaceSearchRequest { query, maxResults }
  // `identifierType` is optional and additive: omitted (or KEYWORD) behaves
  // exactly as before. Set it and the marketplace resolves the value in its own
  // catalogue instead of matching it against title text — which is why pasting
  // a barcode as free text usually finds nothing.
  //
  // undefined is dropped from the JSON body, so leaving it unset IS the keyword
  // path rather than an explicit one.
  //
  // `region` picks the national storefront (US, CA, …), as every listing read
  // now does.
  async search(marketplace, { query, maxResults = 40, identifierType, requirePrice = false, region }) {
    if (USE_MOCK) return (await mockResponse({ marketplace, query, results: [] }, 800)).data
    return (await api.post(ENDPOINTS.marketplaces.search(marketplace), {
      query,
      maxResults,
      identifierType: identifierType && identifierType !== 'KEYWORD' ? identifierType : undefined,
      requirePrice,
      region: region || undefined,
    }, { timeout: 90000 })).data
  },

  // FR-SRCH-001, server-side. Send whatever of the product you have; the
  // planner decides the order (ASIN -> UPC -> EAN -> GTIN -> MPN -> title) and
  // stops at the first channel that returns results.
  //
  // SKU may be included: the backend accepts it and never sends it to a
  // marketplace, so a whole product record can be passed without filtering.
  async channelSearch({
    identifiers, title, brand, url, image, markets, region, condition,
    maxResults = 40, requirePrice = false, forceRefresh = false, judge = false,
  } = {}) {
    if (USE_MOCK) return { items: [], totalResults: 0, steps: [], note: null }
    const clean = (v) => (typeof v === 'string' ? v.trim() || undefined : v || undefined)
    // Drop blank identifier values — an empty ASIN is not "search for nothing",
    // it is a step the planner would waste a call on.
    const ids = Object.fromEntries(
      Object.entries(identifiers || {}).filter(([, v]) => v && String(v).trim()))
    return (await api.post(ENDPOINTS.marketplaces.channelSearch, {
      identifiers: Object.keys(ids).length ? ids : undefined,
      title: clean(title),
      brand: clean(brand),
      url: clean(url),
      image: image || undefined,
      markets: markets?.length ? markets : undefined,
      region: clean(region),
      condition: clean(condition),
      maxResults,
      requirePrice,
      forceRefresh,
      judge,
    }, { timeout: searchTimeout({ judge, image }) })).data
  },

  // GET /marketplaces/{mp}/listings/{itemId}/offers
  //
  // The OpenAPI spec types this as a bare Object, so the field names are not
  // knowable ahead of a real response. Rather than guess one spelling and
  // render a blank panel when it is wrong, every plausible spelling is tried
  // and the raw payload is kept alongside — a panel that shows what actually
  // came back beats one that silently shows nothing.
  async listingOffers(marketplace, itemId, { region } = {}) {
    if (USE_MOCK) return { offers: [], raw: null, recognised: true }
    const { data } = await api.get(ENDPOINTS.marketplaces.listingOffers(marketplace, itemId),
      { params: { region: region || undefined } })
    // The array may be the payload itself or sit under a key.
    const list = Array.isArray(data) ? data
      : [data?.offers, data?.Offers, data?.items, data?.content].find(Array.isArray)

    // Whether an offers ARRAY was found at all, which is not the same as
    // whether it had anything in it. A listing with no Buy Box legitimately
    // returns an empty array, and reporting that as an unreadable response
    // accuses the backend of a fault that is really an empty shelf.
    return {
      offers: (list || []).map(normalizeOffer),
      raw: data,
      recognised: Array.isArray(list),
    }
  },

  // GET /marketplaces/EBAY/listings/{itemId}/other-sellers
  //
  // Two eBay calls behind one request, so it is slower than the offers panel
  // and carries its own timeout. `deliveryCountry` matters more than it looks:
  // without it many sellers quote no shipping at all, and a row with unknown
  // shipping has no comparable total.
  async otherSellers(marketplace, itemId, { region, deliveryCountry, deliveryPostalCode, limit } = {}) {
    if (USE_MOCK) return { sellers: [], listingsFound: 0, note: null }
    const { data } = await api.get(ENDPOINTS.marketplaces.otherSellers(marketplace, itemId), {
      params: {
        region: region || undefined,
        deliveryCountry: deliveryCountry || undefined,
        deliveryPostalCode: deliveryPostalCode || undefined,
        limit: limit || undefined,
      },
      timeout: 30000,
    })
    return data || { sellers: [], listingsFound: 0 }
  },

  // Currently uncalled. Search no longer offers it — match-search returns the
  // ASIN as marketplaceItemId, so a separate lookup was a second button to the
  // same destination. Kept for the Add Product form, which cannot use
  // suggest-asin: that one is keyed on a product that already exists.
  //
  // Same ASIN lookup as suggest-asin, but from a typed value rather than a
  // saved product. identifierType tells the model whether it is holding a
  // barcode or a name, which changes how it searches — so pass whatever the
  // dropdown says rather than leaving it to guess.
  async findAsin(query, identifierType) {
    if (USE_MOCK) return EMPTY_ASIN_LOOKUP
    const { data } = await api.post(ENDPOINTS.marketplaces.findAsin, null, {
      params: { query, identifierType: identifierType || undefined },
      timeout: 60000,
    })
    return normalizeAsinLookup(data)
  },


  // Extracts the item id and country from a marketplace link. Returns null data
  // for a search or category page — there is no single item on one.
  async parseUrl(url) {
    if (USE_MOCK) return null
    return (await api.post(ENDPOINTS.marketplaces.parseUrl, null, { params: { url } })).data
  },

  // POST /marketplaces/{mp}/fees — FeeEstimateRequest { marketplaceItemId, price, currency }
  async fees(marketplace, payload) {
    if (USE_MOCK) return (await mockResponse({ marketplace, ...payload, fee: +(payload.price * 0.15).toFixed(2) })).data
    return (await api.post(ENDPOINTS.marketplaces.fees(marketplace), payload)).data
  },

  // GET listing detail / price / sales
  // All three take the storefront to read from. Omitted, the backend answers
  // from whichever storefront has the listing — fine for a bare link, wrong for
  // anything opened from a row that already knows where it came from.
  async listing(marketplace, itemId, { region } = {}) {
    if (USE_MOCK) return (await mockResponse({ marketplace, itemId })).data
    return (await api.get(ENDPOINTS.marketplaces.listing(marketplace, itemId),
      { params: { region: region || undefined } })).data
  },
  async listingPrice(marketplace, itemId, { region } = {}) {
    if (USE_MOCK) return (await mockResponse({ marketplace, itemId, price: 44.99 })).data
    return (await api.get(ENDPOINTS.marketplaces.listingPrice(marketplace, itemId),
      { params: { region: region || undefined } })).data
  },
  async listingSales(marketplace, itemId) {
    if (USE_MOCK) return (await mockResponse({ marketplace, itemId, units: 0 })).data
    return (await api.get(ENDPOINTS.marketplaces.listingSales(marketplace, itemId))).data
  },

  // Ingestion — pull a product/listing into the platform.
  async ingestAmazon(asin) {
    if (USE_MOCK) return (await mockResponse({ asin, ingested: true }, 900)).data
    return (await api.post(ENDPOINTS.marketplaces.amazonIngest(asin))).data
  },
  async ingestEbay(itemId) {
    if (USE_MOCK) return (await mockResponse({ itemId, ingested: true }, 900)).data
    return (await api.post(ENDPOINTS.marketplaces.ebayIngest(itemId))).data
  },

  // Raw source records (audit / debugging)
  async rawAmazon(asin) {
    if (USE_MOCK) return (await mockResponse([])).data
    return (await api.get(ENDPOINTS.marketplaces.amazonRaw(asin))).data
  },
  async rawEbay(itemId) {
    if (USE_MOCK) return (await mockResponse([])).data
    return (await api.get(ENDPOINTS.marketplaces.ebayRaw(itemId))).data
  },
}
