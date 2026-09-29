import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'

// Tenant self-service (the logged-in company managing its own account).
export const tenantService = {
  async company() {
    if (USE_MOCK) return (await mockResponse({ companyName: 'Maharana Retail', companyEmail: 'admin@priceintel.com', country: 'US', currency: 'USD' })).data
    return (await api.get(ENDPOINTS.client.company)).data
  },
  async updateCompany(payload) {
    if (USE_MOCK) return (await mockResponse(payload)).data
    return (await api.put(ENDPOINTS.client.company, payload)).data
  },
  async dashboard() {
    if (USE_MOCK) return (await mockResponse({ companyName: 'Maharana Retail', totalUsers: 8, activeUsers: 6, maxUsers: 20, subscriptionPlan: 'PRO', subscriptionStatus: 'ACTIVE', tenantStatus: 'ACTIVE' })).data
    return (await api.get(ENDPOINTS.client.dashboard)).data
  },
  async subscription() {
    if (USE_MOCK) return (await mockResponse({ subscriptionPlan: 'PRO', subscriptionStatus: 'ACTIVE', subscriptionStartDate: '2026-01-01', subscriptionEndDate: '2026-12-31', maxUsers: 20 })).data
    return (await api.get(ENDPOINTS.client.subscription)).data
  },

  // Plans the client can choose from (active only).
  async plans() {
    if (USE_MOCK) return (await mockResponse([])).data
    return (await api.get(ENDPOINTS.client.plans)).data
  },

  // Upgrade — backend emails a Stripe pay link and returns a PlanChangeResponse:
  // { tenant, outcome, checkoutUrl, payLinkSent, message }. Plan flips only on payment.
  // billingCycle (MONTHLY | YEARLY) is optional — omit to keep the current cycle.
  async upgrade(planCode, billingCycle) {
    if (USE_MOCK) {
      return (await mockResponse({
        tenant: {}, outcome: 'UPGRADE_PENDING_PAYMENT', payLinkSent: true,
        checkoutUrl: 'https://checkout.stripe.com/c/pay/cs_test_mock',
        message: 'Upgrade pay link created. Complete payment to activate your new plan.',
      })).data
    }
    return (await api.post(ENDPOINTS.client.subUpgrade, null, { params: { planCode, billingCycle: billingCycle || undefined } })).data
  },

  // Renew the current plan for one more period — the way back from read-only.
  // Same PlanChangeResponse as upgrade: RENEWAL_PENDING_PAYMENT carries a
  // Stripe checkoutUrl; APPLIED means it went through without payment (a free
  // plan, or Stripe not configured). billingCycle defaults to the current one.
  async renew(billingCycle) {
    if (USE_MOCK) {
      return (await mockResponse({
        tenant: {}, outcome: 'RENEWAL_PENDING_PAYMENT', payLinkSent: false,
        checkoutUrl: 'https://checkout.stripe.com/c/pay/cs_test_mock',
        message: 'Redirect the client to the checkout URL to complete the renewal.',
      })).data
    }
    return (await api.post(ENDPOINTS.client.subRenew, null,
      { params: { billingCycle: billingCycle || undefined } })).data
  },

  // Downgrade — scheduled for period-end (no charge now).
  async downgrade(planCode, billingCycle) {
    if (USE_MOCK) {
      return (await mockResponse({
        tenant: {}, outcome: 'DOWNGRADE_SCHEDULED', payLinkSent: false,
        message: 'Downgrade will take effect at the end of your billing period.',
      })).data
    }
    return (await api.post(ENDPOINTS.client.subDowngrade, null, { params: { planCode, billingCycle: billingCycle || undefined } })).data
  },

  // The client's own payment history. Paged: { content, totalElements, ... }.
  async payments({ page = 1, size = 20 } = {}) {
    if (USE_MOCK) return (await mockResponse({ content: [], totalElements: 0, page: 0, size })).data
    const { data } = await api.get(ENDPOINTS.client.payments, { params: { page: page - 1, size } })
    return data
  },
}
