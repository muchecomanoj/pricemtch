import axios from 'axios'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'
import { appUrl } from '../utils/appPath'

// ---------------------------------------------------------------------------
// Public self-signup — aligned to the live contract:
//   POST /public/register             { planCode, billingCycle, companyName, email,
//                                        contactPerson, ...profile } -> { token, nextStep, ... }
//                                       (also emails the verification code)
//   POST /public/register/verify-code  { token, code }
//   POST /public/register/set-password { token, code, password }
//   POST /public/register/checkout     { token, successUrl, cancelUrl } -> { mode, checkoutUrl, amount }
//   POST /public/register/mock-confirm { token } -> { completed, loginUrl, subscription* }
//
// Set SIGNUP_LIVE = false to run the whole flow on mocks (demo OTP: 123456).
// ---------------------------------------------------------------------------
const SIGNUP_LIVE = true
const DEMO_OTP = '123456'
const E = ENDPOINTS.register

// Public endpoints live under /api/public (no /api/v1, no auth).
const publicApi = axios.create({
  baseURL: import.meta.env.VITE_PUBLIC_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
  // /public/register sends the verification email inline, so it is the slowest
  // call in the flow — but never let a stalled server spin the button forever.
  timeout: 30000,
})
publicApi.interceptors.response.use(
  (res) => {
    const b = res.data
    if (b && typeof b === 'object' && 'success' in b && 'data' in b) res.data = b.data
    return res
  },
  (error) => {
    if (error.code === 'ECONNABORTED') {
      error.message = 'The server took too long to respond. Please try again in a moment.'
    } else if (!error.response) {
      error.message = 'Could not reach the server. Check your connection and try again.'
    } else {
      const msg = error.response.data?.message
      if (msg) error.message = msg
    }
    return Promise.reject(error)
  }
)

export const signupService = {
  // Step 2 — create the pending registration (plan + all profile fields).
  // Returns a token the rest of the flow carries; also emails the OTP code.
  async register(payload) {
    if (!SIGNUP_LIVE) {
      return (await mockResponse({ token: 'mock-signup-token', email: payload.email, companyCode: 'MOCK-1042', nextStep: 'VERIFY' }, 900)).data
    }
    const { data } = await publicApi.post(E.start, payload)
    return data
  },

  // Step 3 — verify the emailed code.
  async verifyCode(token, code) {
    if (!SIGNUP_LIVE) {
      if (code !== DEMO_OTP) throw new Error('Invalid verification code. Use 123456 in demo mode.')
      return (await mockResponse({ token, nextStep: 'SET_PASSWORD' }, 600)).data
    }
    const { data } = await publicApi.post(E.verifyCode, { token, code })
    return data
  },

  // Step 4 — set the admin password (backend re-checks the code).
  async setPassword(token, code, password) {
    if (!SIGNUP_LIVE) return (await mockResponse({ token, nextStep: 'PAYMENT' }, 600)).data
    const { data } = await publicApi.post(E.setPassword, { token, code, password })
    return data
  },

  // Step 5 — create a hosted-checkout session.
  async checkout(token, { successUrl, cancelUrl } = {}) {
    if (!SIGNUP_LIVE) {
      return (await mockResponse({ mode: 'MOCK', checkoutUrl: null, sessionId: 'mock', note: 'Demo checkout — no real charge' }, 900)).data
    }
    const { data } = await publicApi.post(E.checkout, {
      token,
      successUrl: successUrl || appUrl('/signup?paid=1'),
      cancelUrl: cancelUrl || appUrl('/signup'),
    })
    return data
  },

  // Step 6 — simulate the provider confirming (mock mode). Returns the final
  // SelfRegisterResponse with { completed, loginUrl, subscription* }.
  async mockConfirm(token) {
    if (!SIGNUP_LIVE) {
      const renews = new Date(); renews.setMonth(renews.getMonth() + 12)
      return (await mockResponse({ completed: true, loginUrl: '/login', subscriptionStatus: 'TRIALING', subscriptionEndDate: renews.toLocaleDateString() }, 1200)).data
    }
    const { data } = await publicApi.post(E.mockConfirm, { token })
    return data
  },
}
