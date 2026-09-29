import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse, paginate } from '../mock/mockHelper'
import { mapPage } from './mappers'

const E = ENDPOINTS.adminClients

// Mock tenants for offline mode.
const mockClients = Array.from({ length: 6 }).map((_, i) => ({
  id: i + 1,
  companyName: ['Maharana Retail', 'Acme Goods', 'PrimeSellers', 'MegaMart'][i % 4] + ` ${i + 1}`,
  companyCode: `TEN-${100 + i}`,
  companyEmail: `admin${i + 1}@example.com`,
  country: 'US', currency: 'USD',
  subscriptionPlan: ['STARTER', 'PRO', 'ENTERPRISE'][i % 3],
  subscriptionStatus: ['ACTIVE', 'TRIAL', 'CANCELLED'][i % 3],
  maxUsers: [5, 20, 100][i % 3],
  status: ['ACTIVE', 'SUSPENDED', 'INACTIVE'][i % 3],
}))

export const clientService = {
  async list(params = {}) {
    if (USE_MOCK) return (await mockResponse(paginate(mockClients, params))).data
    const { page = 1, size = 10, search } = params
    const { data } = await api.get(E.list, { params: { page: page - 1, size, q: search || undefined } })
    const mapped = mapPage(data)
    mapped.page = page
    return mapped
  },
  async get(id) {
    if (USE_MOCK) return (await mockResponse(mockClients.find((c) => c.id === Number(id)))).data
    return (await api.get(E.byId(id))).data
  },
  async create(payload) {
    if (USE_MOCK) return (await mockResponse({ id: Date.now(), ...payload })).data
    return (await api.post(E.list, payload)).data
  },
  async update(id, payload) {
    if (USE_MOCK) return (await mockResponse({ id, ...payload })).data
    return (await api.put(E.byId(id), payload)).data
  },
  async remove(id) {
    if (USE_MOCK) return (await mockResponse({ ok: true })).data
    return (await api.delete(E.byId(id))).data
  },

  // Re-send the activation/invitation email (the backend regenerates the token).
  async resendActivation(id) {
    if (USE_MOCK) return (await mockResponse({ activationEmailSent: true })).data
    return (await api.post(E.resendActivation(id))).data
  },

  // Status transitions (all PATCH).
  async setStatus(id, action) {
    // action: 'activate' | 'deactivate' | 'suspend' | 'resume'
    if (USE_MOCK) return (await mockResponse({ id, action })).data
    return (await api.patch(E[action](id))).data
  },

  async stats(id) {
    if (USE_MOCK) return (await mockResponse({ tenantId: id, totalUsers: 8, activeUsers: 6, maxUsers: 20 })).data
    return (await api.get(E.stats(id))).data
  },
  async usage(id) {
    if (USE_MOCK) return (await mockResponse({ promptTokens: 12000, completionTokens: 8000, totalTokens: 20000 })).data
    return (await api.get(E.usage(id))).data
  },

  // Subscription management. planCode/billingCycle are QUERY params; body is empty.
  // Assign is instant (no payment, no email); billingCycle is optional (omit = keep current).
  async assignPlan(tenantId, planCode, billingCycle) {
    if (USE_MOCK) return (await mockResponse({ tenantId, planCode, billingCycle })).data
    return (await api.post(E.sub.assign(tenantId), null, { params: { planCode, billingCycle: billingCycle || undefined } })).data
  },
  // Upgrade returns a PlanChangeResponse: the plan does NOT change yet — the
  // backend emails a Stripe pay link and the plan flips only after the client
  // pays. { tenant, outcome, checkoutUrl, payLinkSent, message }
  // billingCycle (MONTHLY | YEARLY) is optional — omit to keep the current cycle.
  async upgrade(tenantId, planCode, billingCycle) {
    if (USE_MOCK) {
      return (await mockResponse({
        tenant: { id: tenantId }, outcome: 'UPGRADE_PENDING_PAYMENT', payLinkSent: true,
        checkoutUrl: 'https://checkout.stripe.com/c/pay/cs_test_mock',
        message: 'Upgrade pay link emailed to the client. The plan activates once they pay.',
      })).data
    }
    return (await api.post(E.sub.upgrade(tenantId), null, { params: { planCode, billingCycle: billingCycle || undefined } })).data
  },
  // Downgrade is scheduled for period-end (no charge now).
  // { tenant, outcome: 'DOWNGRADE_SCHEDULED', payLinkSent, message }
  async downgrade(tenantId, planCode, billingCycle) {
    if (USE_MOCK) {
      return (await mockResponse({
        tenant: { id: tenantId }, outcome: 'DOWNGRADE_SCHEDULED', payLinkSent: false,
        message: 'Downgrade will take effect at the end of the billing period.',
      })).data
    }
    return (await api.post(E.sub.downgrade(tenantId), null, { params: { planCode, billingCycle: billingCycle || undefined } })).data
  },
  async extend(tenantId, days) {
    if (USE_MOCK) return (await mockResponse({ tenantId, days })).data
    return (await api.post(E.sub.extend(tenantId), { days })).data
  },
  async renew(tenantId) {
    if (USE_MOCK) return (await mockResponse({ tenantId, renewed: true })).data
    return (await api.post(E.sub.renew(tenantId))).data
  },
  async cancel(tenantId) {
    if (USE_MOCK) return (await mockResponse({ tenantId, cancelled: true })).data
    return (await api.post(E.sub.cancel(tenantId))).data
  },
  async changeStatus(tenantId, status) {
    if (USE_MOCK) return (await mockResponse({ tenantId, status })).data
    return (await api.patch(E.sub.status(tenantId), { status })).data
  },
  async history(tenantId) {
    if (USE_MOCK) return (await mockResponse([])).data
    return (await api.get(E.sub.history(tenantId))).data
  },

  // Payment records (Stripe). Paged: { content, totalElements, totalPages, page, size }.
  async payments(tenantId, { page = 1, size = 20 } = {}) {
    if (USE_MOCK) return (await mockResponse({ content: [], totalElements: 0, totalPages: 0, page: 0, size })).data
    const { data } = await api.get(E.payments(tenantId), { params: { page: page - 1, size } })
    return data
  },
}
