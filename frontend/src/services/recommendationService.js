import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'

// Tenant-wide price recommendations. Lifecycle: DRAFT → APPROVED → PUBLISHED,
// or REJECTED (FR-REC-002). The Recommendations page lists DRAFT.
//
// NOTE: publish records the decision — it does NOT push a price to the
// marketplace. Automatic publishing is out of scope (FRD §2.3).

const EMPTY = { content: [], totalElements: 0, totalPages: 1, number: 0, size: 20 }

export const recommendationService = {
  async list({ status = 'DRAFT', page = 0, size = 20 } = {}) {
    if (USE_MOCK) return EMPTY
    const { data } = await api.get(ENDPOINTS.recommendations.list, { params: { status, page, size } })
    if (!data) return EMPTY
    return {
      content: data.content || [],
      totalElements: data.totalElements ?? 0,
      totalPages: data.totalPages ?? 1,
      number: data.number ?? page,
      size: data.size ?? size,
    }
  },

  // Returns a { generated: n }-shaped map of what the engine produced.
  async generate() {
    if (USE_MOCK) return { generated: 0 }
    return (await api.post(ENDPOINTS.recommendations.generate)).data || {}
  },

  async approve(id) {
    if (USE_MOCK) return { id, status: 'APPROVED' }
    return (await api.post(ENDPOINTS.productIntel.approve(id))).data
  },
  async reject(id) {
    if (USE_MOCK) return { id, status: 'REJECTED' }
    return (await api.post(ENDPOINTS.productIntel.reject(id))).data
  },
  // May be refused with a 400 carrying a cooldown message. That refusal does
  // NOT consume the recommendation — it stays approved and publishable once the
  // cooldown expires, so the button must not be disabled afterwards.
  async publish(id) {
    if (USE_MOCK) return { id, status: 'PUBLISHED' }
    return (await api.post(ENDPOINTS.productIntel.publish(id))).data
  },

  // Undoes a published price. The record ends REJECTED with rolledBackAt set,
  // never back at draft: an audit has to keep the fact that it went live.
  async rollback(id) {
    if (USE_MOCK) return { id, status: 'REJECTED', rolledBackAt: new Date().toISOString() }
    return (await api.post(ENDPOINTS.productIntel.rollback(id))).data
  },
}
