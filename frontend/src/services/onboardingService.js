import axios from 'axios'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'
import { mockInvitation } from '../mock/onboarding'

// ---------------------------------------------------------------------------
// Client onboarding via a secure invitation link. Aligned to the live contract:
//   POST /public/onboarding/validate         { token }
//        -> { valid, email, companyName, companyCode, contactPerson, expiresAt }
//   POST /public/onboarding/verify-code       { token, code }
//   POST /public/onboarding/set-password      { token, code, password }
//   POST /public/onboarding/complete-profile  { token, companyName, ...gstin, taxId }
//   POST /public/onboarding/choose-plan       { token, planCode, billingCycle:MONTHLY|YEARLY }
//   POST /public/payment/checkout             { token, successUrl, cancelUrl } -> { mode, checkoutUrl }
//   POST /public/payment/mock-confirm         -> ActivationCompleteResponse { activated, loginUrl, ... }
//
// Set ONBOARDING_LIVE = false to run the whole flow against local mocks
// (demo OTP: 123456) without a real invitation token.
// ---------------------------------------------------------------------------
const ONBOARDING_LIVE = true
const DEMO_OTP = '123456'

const publicApi = axios.create({
  baseURL: import.meta.env.VITE_PUBLIC_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
})
publicApi.interceptors.response.use(
  (res) => {
    const b = res.data
    if (b && typeof b === 'object' && 'success' in b && 'data' in b) res.data = b.data
    return res
  },
  (error) => {
    const msg = error.response?.data?.message
    if (msg) error.message = msg
    return Promise.reject(error)
  }
)

export const onboardingService = {
  // Resolve the invitation token -> organization + assigned plan.
  async validate(token) {
    if (!ONBOARDING_LIVE) {
      if (!token) throw new Error('Missing invitation token')
      return (await mockResponse(mockInvitation, 500)).data
    }
    const { data } = await publicApi.post(ENDPOINTS.onboarding.validate, { token })
    return data
  },

  // Step 5 — verify the emailed OTP.
  async verifyCode(token, code) {
    if (!ONBOARDING_LIVE) {
      if (code !== DEMO_OTP) throw new Error('Invalid code. Use 123456 in demo mode.')
      return (await mockResponse({ valid: true }, 600)).data
    }
    const { data } = await publicApi.post(ENDPOINTS.onboarding.verifyCode, { token, code })
    return data
  },

  // Step 6 — set the account password (backend re-checks the OTP code).
  async setPassword(token, code, password) {
    if (!ONBOARDING_LIVE) return (await mockResponse({ activated: false, nextStep: 'PROFILE' }, 600)).data
    const { data } = await publicApi.post(ENDPOINTS.onboarding.setPassword, { token, code, password })
    return data
  },

  // Step 7 — save the organization profile (only backend-accepted fields).
  async completeProfile(token, p) {
    const body = {
      token,
      companyName: p.companyName,
      companyAddress: p.companyAddress || null,
      companyPhone: p.companyPhone || null,
      website: p.website || null,
      city: p.city || null,
      state: p.state || null,
      country: p.country || null,
      postalCode: p.postalCode || null,
      timezone: p.timezone || null,
      currency: p.currency || null,
      gstin: p.gstin || null,
      taxId: p.taxId || null,
    }
    if (!ONBOARDING_LIVE) return (await mockResponse({ activated: false, nextStep: 'PLAN' }, 500)).data
    const { data } = await publicApi.post(ENDPOINTS.onboarding.completeProfile, body)
    return data
  },

  // Step 8/9 — confirm plan + billing cycle (MONTHLY | YEARLY only).
  async choosePlan(token, planCode, billingCycle) {
    if (!ONBOARDING_LIVE) return (await mockResponse({ activated: false, nextStep: 'PAYMENT', subscriptionPlan: planCode, billingCycle }, 400)).data
    const { data } = await publicApi.post(ENDPOINTS.onboarding.choosePlan, { token, planCode, billingCycle })
    return data
  },

  // Step 10 — create a hosted-checkout session.
  async checkout(token, { successUrl, cancelUrl } = {}) {
    if (!ONBOARDING_LIVE) {
      return (await mockResponse({ mode: 'MOCK', checkoutUrl: null, sessionId: 'mock-session', note: 'Demo checkout — no real charge' }, 900)).data
    }
    const origin = window.location.origin
    const { data } = await publicApi.post(ENDPOINTS.onboarding.checkout, {
      token,
      successUrl: successUrl || `${origin}/activate?token=${token}&paid=1`,
      cancelUrl: cancelUrl || `${origin}/activate?token=${token}`,
    })
    return data
  },

  // Step 11 — simulate the payment provider confirming (mock mode).
  // Returns ActivationCompleteResponse { activated, loginUrl, subscription* }.
  async mockConfirm(token) {
    if (!ONBOARDING_LIVE) {
      const renews = new Date(); renews.setMonth(renews.getMonth() + 12)
      return (await mockResponse({
        activated: true, nextStep: 'DONE', loginUrl: '/login',
        subscriptionStatus: 'ACTIVE', subscriptionEndDate: renews.toLocaleDateString(),
      }, 1400)).data
    }
    const { data } = await publicApi.post(ENDPOINTS.onboarding.mockConfirm, { token })
    return data
  },
}
