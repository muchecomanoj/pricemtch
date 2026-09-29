import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { toCostPayload } from '../components/forms/CostFields'

// Tenant-wide cost defaults — FR-COST-001 "global defaults, channel defaults,
// product overrides". This is the GLOBAL layer; productIntel.costProfile is the
// per-product override that wins over it.
//
// api.js unwraps { success, message, data }, so `data` is already the payload.

const EMPTY = { currency: 'USD', configured: false }

export const costModelService = {
  async get() {
    if (USE_MOCK) return EMPTY
    return (await api.get(ENDPOINTS.costModel)).data || EMPTY
  },

  // Payload is built by toCostPayload so this stays identical to the per-product
  // cost profile — that field-for-field match is what makes the cascade work.
  async save(values) {
    const body = toCostPayload(values)
    if (USE_MOCK) return { ...body, configured: true }
    return (await api.put(ENDPOINTS.costModel, body)).data
  },
}
