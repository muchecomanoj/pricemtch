import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'

// Per-product intelligence: competitor discovery, price stats, history, costs,
// profitability, sales and recommendations. All LIVE (verified against the
// springdoc contract on the backend).
//
// The api.js interceptor unwraps { success, message, data }, so `data` here is
// already the payload.
//
// Under USE_MOCK these return EMPTY/unconfigured shapes rather than invented
// figures — the FRD forbids presenting sample data as real, and a fabricated
// competitor price is exactly what "AI must never invent a price" rules out.

const E = ENDPOINTS.productIntel

const emptyMarketPrices = { competitorCount: 0, lowest: null, highest: null, average: null, median: null, currency: 'USD' }
const emptySales = { ownedClassification: 'UNAVAILABLE', estimatedClassification: 'UNAVAILABLE' }

// A forced refresh bypasses the cache and goes to the marketplaces live: up to
// three marketplaces, a walk of up to four Amazon regions, and up to five
// per-item offer lookups against a 2 req/s limit, with exponential-backoff
// retries on timeout. Backend-measured worst case is ~30s and the theoretical
// ceiling approaches 60s, so this ONE call overrides the global 20s axios
// timeout. Raising the global would slow every failure in the app instead.
const REFRESH_TIMEOUT_MS = 90000

// The free tier judges roughly 15 pairs a minute, so limit=50 (the cap) runs
// about three and a half minutes. The global 20s timeout would abort even a
// limit=20 batch at a quarter of the way through — and the backend would keep
// working and bill for it, so the client must wait rather than give up.
const AI_REVIEW_TIMEOUT_MS = 300000

export const productIntelService = {
  // ── Discovery ───────────────────────────────────────────────
  // Populates candidates/market-prices/sales. markets omitted => all enabled.
  //
  // Same endpoint serves both actions; `refresh` is the only difference:
  //   refresh=false — cached research, free, sub-second
  //   refresh=true  — live marketplace calls, consumes quota, seconds to a minute
  // Synchronous either way: the response comes back COMPLETED, nothing to poll.
  async startSearch(id, { markets, maxResults = 5, refresh = false, destination } = {}) {
    if (USE_MOCK) return { id: 0, status: 'COMPLETED', resultCount: 0 }
    const body = { maxResults }
    if (markets?.length) body.markets = markets
    // Same rule as channel search: omitted unless a valid ISO country is set,
    // because an empty country fails validation rather than meaning "none".
    if (destination) body.destination = destination
    // The query param alone is sufficient; the body flag is sent too so the
    // request is unambiguous if the param is ever dropped by a proxy.
    if (refresh) body.forceRefresh = true
    const config = refresh
      ? { params: { refresh: true }, timeout: REFRESH_TIMEOUT_MS }
      : undefined
    return (await api.post(E.searchJobs(id), body, config)).data
  },
  async searchJob(jobId) {
    if (USE_MOCK) return { id: jobId, status: 'COMPLETED', resultCount: 0 }
    return (await api.get(E.searchJob(jobId))).data
  },

  // ── Reads ───────────────────────────────────────────────────
  async marketPrices(id, { matchedOnly = false } = {}) {
    if (USE_MOCK) return emptyMarketPrices
    const { data } = await api.get(E.marketPrices(id), { params: { matchedOnly } })
    return data || emptyMarketPrices
  },
  async candidates(id) {
    if (USE_MOCK) return []
    return (await api.get(E.candidates(id))).data || []
  },
  // Judges this product's unjudged candidates and returns the whole candidate
  // list with verdicts attached. Already-judged pairs come from cache free.
  //
  // NEVER call this on mount, in an effect, or on tab focus. Each call spends a
  // metered token budget, and the free tier judges roughly 15 pairs a minute —
  // rendering a table must not cost money or take 80 seconds.
  async aiReview(id, { limit = 20 } = {}) {
    if (USE_MOCK) return []
    const { data } = await api.post(E.aiReview(id), null, {
      params: { limit },
      timeout: AI_REVIEW_TIMEOUT_MS,
    })
    return Array.isArray(data) ? data : []
  },

  // from/to are ISO datetimes; omit for the full series.
  async priceHistory(listingId, { from, to } = {}) {
    if (USE_MOCK) return []
    const { data } = await api.get(E.priceHistory(listingId), { params: { from, to } })
    return data || []
  },

  // Per-product price trend, aggregated across all its competitor listings.
  // One point per bucket: our price plus the low/median/high of the market.
  // Points only exist for days a search actually ran, so a product searched
  // once returns a single point — that is expected, not an error.
  async priceHistoryRollup(id, { bucket = 'day', from, to } = {}) {
    if (USE_MOCK) return []
    const params = { bucket }
    if (from) params.from = from
    if (to) params.to = to
    const { data } = await api.get(E.priceHistoryRollup(id), { params })
    return Array.isArray(data) ? data : []
  },
  async sales(id) {
    if (USE_MOCK) return emptySales
    return (await api.get(E.sales(id))).data || emptySales
  },
  // `asOf` picks the cost version in force on that date, defaulting to today.
  // Without it a margin approved last quarter is recomputed with this
  // quarter's freight and can no longer be explained.
  async costProfile(id, { asOf } = {}) {
    if (USE_MOCK) return { configured: false }
    const { data } = await api.get(E.costProfile(id), { params: { asOf } })
    return data
  },

  // Every version, newest effective date first — the answer to "when did this
  // change, and what was it before?", which a moved margin always raises.
  async costProfileHistory(id) {
    if (USE_MOCK) return []
    const { data } = await api.get(E.costProfileHistory(id))
    return Array.isArray(data) ? data : []
  },

  // Omit `price` to get the real margin at the product's own ourPrice; pass one
  // to model a scenario. With neither an ourPrice nor a price the API 400s, so
  // callers must gate on one being available.
  // `asOf` selects the COSTS, not the price — the same selling price against
  // last quarter's costs is exactly the comparison an audit asks for.
  async profitability(id, price, { asOf } = {}) {
    if (USE_MOCK) return { costProfileConfigured: false }
    // An empty price string is "use the product's own price", not a price of
    // nothing — it must be dropped rather than sent as ''.
    const params = {}
    if (price != null && price !== '') params.price = price
    if (asOf) params.asOf = asOf
    const { data } = await api.get(E.profitability(id), { params })
    return data || { costProfileConfigured: false }
  },

  // Daily profitability snapshots (captured 01:30). Distinct from
  // profitability() above, which recalculates live — do not mix the two.
  // Returns [] when the product has no price or no costs.
  // from/to are plain dates (YYYY-MM-DD), not ISO datetimes.
  async profitabilityTrend(id, { from, to } = {}) {
    if (USE_MOCK) return []
    const params = {}
    if (from) params.from = from
    if (to) params.to = to
    const { data } = await api.get(E.profitabilityTrend(id), { params })
    return Array.isArray(data) ? data : []
  },

  async recommendations(id) {
    if (USE_MOCK) return []
    return (await api.get(E.recommendations(id))).data || []
  },

  // ── Writes (ADMIN / MANAGER) ────────────────────────────────
  async review(listingId, decision) {
    if (USE_MOCK) return { id: listingId, matchStatus: decision }
    return (await api.post(E.review(listingId), { decision })).data
  },
  async saveCostProfile(id, payload) {
    if (USE_MOCK) return { ...payload, configured: true }
    return (await api.put(E.costProfile(id), payload)).data
  },
  async generateRecommendation(id, payload = {}) {
    if (USE_MOCK) return null
    return (await api.post(E.recommendations(id), payload)).data
  },
  async submitRecommendation(recId) {
    if (USE_MOCK) return { id: recId, status: 'PENDING_APPROVAL' }
    return (await api.post(E.submit(recId))).data
  },
  async approveRecommendation(recId) {
    if (USE_MOCK) return { id: recId, status: 'APPROVED' }
    return (await api.post(E.approve(recId))).data
  },
  // NOTE: publish marks the recommendation PUBLISHED — it does NOT push the
  // price to the marketplace. Automatic publishing is out of scope (FRD §2.3).
  async publishRecommendation(recId) {
    if (USE_MOCK) return { id: recId, status: 'PUBLISHED' }
    return (await api.post(E.publish(recId))).data
  },
  async rejectRecommendation(recId) {
    if (USE_MOCK) return { id: recId, status: 'REJECTED' }
    return (await api.post(E.reject(recId))).data
  },

  // Undoes a published price. Ends REJECTED with rolledBackAt set rather than
  // returning to draft — the audit needs to keep the fact that it went live.
  async rollbackRecommendation(recId) {
    if (USE_MOCK) return { id: recId, status: 'REJECTED', rolledBackAt: new Date().toISOString() }
    return (await api.post(E.rollback(recId))).data
  },

  // ── Alert rules (FR-ALERT-001) ──────────────────────────────
  // Evaluated hourly by the backend; a fired rule creates an ALERT_TRIGGERED
  // notification, which surfaces in the header bell.
  // Omit productId to list every rule in the tenant.
  async alertRules(productId) {
    if (USE_MOCK) return []
    const params = productId ? { productId } : {}
    const { data } = await api.get(ENDPOINTS.alertRules.list, { params })
    return data || []
  },

  // Runs the engine immediately instead of waiting for the hourly job.
  async evaluateAlertRules() {
    if (USE_MOCK) return { fired: 0 }
    return (await api.post(ENDPOINTS.alertRules.evaluate)).data || {}
  },
  async createAlertRule(payload) {
    if (USE_MOCK) return { id: 0, ...payload, active: true }
    return (await api.post(ENDPOINTS.alertRules.list, payload)).data
  },
  async deleteAlertRule(ruleId) {
    if (USE_MOCK) return { ok: true }
    return (await api.delete(ENDPOINTS.alertRules.byId(ruleId))).data
  },

  // One rule across many products, in one call.
  //
  // The backend caps a request at 500 ids, so a larger selection is split and
  // the replies added together — the caller sees one result whatever the size.
  // Every product comes back with its own outcome: CREATED, SKIPPED_DUPLICATE,
  // SKIPPED_NO_COMPETITORS, NOT_FOUND or FAILED.
  async bulkCreateAlertRules({ productIds, ...rule }) {
    const ids = [...new Set((productIds || []).map(Number).filter(Boolean))]
    if (!ids.length) return { requested: 0, created: 0, skipped: 0, failed: 0, createdWithoutCompetitors: 0, results: [] }
    if (USE_MOCK) {
      return {
        requested: ids.length, created: ids.length, skipped: 0, failed: 0,
        createdWithoutCompetitors: 0,
        results: ids.map((productId) => ({ productId, outcome: 'CREATED' })),
      }
    }

    const CHUNK = 500
    const empty = { requested: 0, created: 0, skipped: 0, failed: 0, createdWithoutCompetitors: 0, results: [] }
    const totals = { ...empty }
    for (let i = 0; i < ids.length; i += CHUNK) {
      const { data } = await api.post(ENDPOINTS.alertRules.bulk, {
        ...rule, productIds: ids.slice(i, i + CHUNK),
      })
      for (const k of ['requested', 'created', 'skipped', 'failed', 'createdWithoutCompetitors']) {
        totals[k] += data?.[k] ?? 0
      }
      totals.results = totals.results.concat(data?.results ?? [])
    }
    return totals
  },

  // Rules belonging to another tenant are ignored rather than rejected, so
  // `deleted` and `changed` can come back lower than the ids sent. That means
  // the list on screen is stale, not that the call failed.
  async bulkDeleteAlertRules(ruleIds) {
    if (USE_MOCK) return { deleted: ruleIds.length }
    return (await api.post(ENDPOINTS.alertRules.bulkDelete, ruleIds)).data || {}
  },
  async bulkSetAlertRuleActive(ruleIds, active) {
    if (USE_MOCK) return { changed: ruleIds.length }
    return (await api.post(ENDPOINTS.alertRules.bulkActive, ruleIds, { params: { active } })).data || {}
  },

  // Pause or resume one rule. A paused rule keeps its firing history and
  // simply never fires — which is what "stop telling me about this" usually
  // means, and unlike deleting, it can be undone.
  async setAlertRuleActive(ruleId, active) {
    if (USE_MOCK) return { id: ruleId, active }
    return (await api.patch(ENDPOINTS.alertRules.setActive(ruleId), null, { params: { active } })).data
  },
}
