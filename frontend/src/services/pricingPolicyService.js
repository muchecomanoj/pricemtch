import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'

// The bounds a recommendation is allowed to land inside.
//
// Scope is (productId, marketplace), and the most specific match wins:
//   product + channel  →  product  →  company + channel  →  company
// Both null is the company-wide default.
export const pricingPolicyService = {
  async list() {
    if (USE_MOCK) return []
    const { data } = await api.get(ENDPOINTS.pricingPolicies.root)
    return Array.isArray(data) ? data : []
  },

  // One endpoint creates and updates — the scope pair is the identity, so
  // re-posting the same productId/marketplace corrects that policy.
  async save(policy) {
    if (USE_MOCK) return policy
    const { data } = await api.post(ENDPOINTS.pricingPolicies.root, policy)
    return data
  },

  async remove(id) {
    if (USE_MOCK) return true
    await api.delete(ENDPOINTS.pricingPolicies.byId(id))
    return true
  },

  // What actually applies to one product on one channel, after the fallback.
  async effective({ productId, marketplace } = {}) {
    if (USE_MOCK) return null
    const { data } = await api.get(ENDPOINTS.pricingPolicies.effective, {
      params: { productId, marketplace: marketplace || undefined },
    })
    return data
  },
}

// Mirrors the backend's two rejections so they surface before a failed save.
//
// Both are about a policy that cannot be satisfied rather than a typo: the
// first would let automation publish with nothing limiting it, the second
// describes a window no price fits inside.
export function validatePolicy(p) {
  const has = (v) => v != null && v !== ''
  const anyBound = ['floorPrice', 'floorMarginPct', 'ceilingPrice', 'maxChangePct', 'maxChangeAmount']
    .some((k) => has(p[k]))

  const errors = {}
  if (p.autoPublish && !anyBound) {
    errors.autoPublish = 'Set at least one limit before turning this on.'
  }
  if (has(p.floorPrice) && has(p.ceilingPrice) && Number(p.floorPrice) > Number(p.ceilingPrice)) {
    errors.floorPrice = 'The minimum cannot be above the maximum.'
    errors.ceilingPrice = 'The maximum cannot be below the minimum.'
  }
  return { errors, anyBound, ok: Object.keys(errors).length === 0 }
}
