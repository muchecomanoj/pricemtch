import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'

// Stored exchange rates. FR-PROFIT-001 requires the rate a converted figure
// used to be recoverable, and until these exist a median across a C$95 and a
// $70 listing is arithmetic on incompatible numbers wearing one currency label.
export const fxRateService = {
  async list() {
    if (USE_MOCK) return []
    const { data } = await api.get(ENDPOINTS.fxRates.root)
    return Array.isArray(data) ? data : []
  },

  // Create or correct. The pair plus asOf is the identity, so re-posting the
  // same base/quote/asOf fixes that rate rather than failing as a duplicate.
  async save(rate) {
    if (USE_MOCK) return rate
    const { data } = await api.post(ENDPOINTS.fxRates.root, rate)
    return data
  },

  async remove(id) {
    if (USE_MOCK) return true
    await api.delete(ENDPOINTS.fxRates.byId(id))
    return true
  },

  // The currency every statistic is expressed in. Null when unset — the
  // backend then uses whichever currency most listings are already in.
  async reportingCurrency() {
    if (USE_MOCK) return null
    const { data } = await api.get(ENDPOINTS.fxRates.reportingCurrency)
    return typeof data === 'string' ? data : data?.currency ?? null
  },

  async convert({ amount, from, to }) {
    if (USE_MOCK) return null
    const { data } = await api.get(ENDPOINTS.fxRates.convert, { params: { amount, from, to } })
    return data
  },
}

// A rate is directional: how many units of `quote` one unit of `base` buys.
// Storing one direction is enough — the inverse is the same fact backwards —
// so the form says so rather than inviting two rows per pair.
export const rateExample = (base, quote, rate) =>
  (base && quote && rate ? `1 ${base} = ${rate} ${quote}` : null)
