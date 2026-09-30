import axios from 'axios'
import { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'

// Public endpoints live under /api/public (NOT /api/v1), and need no auth token.
const baseURL = import.meta.env.VITE_PUBLIC_BASE_URL || '/api'
const publicApi = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
})
publicApi.interceptors.response.use((res) => {
  const b = res.data
  if (b && typeof b === 'object' && 'success' in b && 'data' in b) res.data = b.data
  return res
})

// The lead forms need the envelope itself, not its data: the success sentence
// is the envelope's `message` (tuned per form on the backend), and a refusal
// carries per-field `errors`. So they get a client that doesn't unwrap.
const formApi = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 20000,
})
formApi.interceptors.response.use(
  (res) => res,
  (error) => {
    const body = error.response?.data
    if (error.code === 'ECONNABORTED') {
      error.message = 'The server took too long to respond. Please try again in a moment.'
    } else if (!error.response) {
      error.message = 'Could not reach the server. Check your connection and try again.'
    } else if (body?.message) {
      // Validation ("Validation failed") and the hourly throttle both arrive
      // here; only validation also carries field errors.
      error.message = body.message
    }
    error.fieldErrors = body?.errors && typeof body.errors === 'object' ? body.errors : null
    return Promise.reject(error)
  },
)

const get = async (path, mock) => {
  if (USE_MOCK) return (await mockResponse(mock)).data
  return (await publicApi.get(path)).data
}

const MOCK_THANKS = 'Thanks — we have your request and will be in touch shortly.'

// Resolves to { message } — the backend's own sentence, shown as sent.
const send = async (path, body, params) => {
  if (USE_MOCK) return (await mockResponse({ message: MOCK_THANKS })).data
  const { data } = await formApi.post(path, body, { params })
  return { message: data?.message || MOCK_THANKS }
}

const mockPlans = [
  { code: 'STARTER', name: 'Starter', price: 49, currency: 'USD', maxUsers: 5, features: 'Basic monitoring' },
  { code: 'PRO', name: 'Pro', price: 199, currency: 'USD', maxUsers: 20, features: 'Advanced + AI' },
  { code: 'ENTERPRISE', name: 'Enterprise', price: 799, currency: 'USD', maxUsers: 100, features: 'Everything' },
]

export const publicService = {
  pricing: () => get(ENDPOINTS.publicSite.pricing, mockPlans),
  plans: () => get(ENDPOINTS.publicSite.plans, mockPlans),
  // Each of these is one landing section: { eyebrow?, title?, subtitle?, items: [...] }.
  faqs: () => get(ENDPOINTS.publicSite.faqs, { items: [{ question: 'What is this?', answer: 'A price intelligence platform.' }] }),
  features: () => get(ENDPOINTS.publicSite.features, { items: [{ title: 'Price monitoring', description: 'Track competitors.' }] }),
  testimonials: () => get(ENDPOINTS.publicSite.testimonials, { items: [{ name: 'Jane', quote: 'Boosted our margins.' }] }),
  // Every visible section keyed by name (HERO, STATS, …). A hidden or broken
  // section is simply absent. Mock mode returns null so the page's own
  // fallback copy is used.
  landing: () => get(ENDPOINTS.publicSite.landing, null),
  requestDemo: (body) => send(ENDPOINTS.publicSite.requestDemo, body),
  contact: (body) => send(ENDPOINTS.publicSite.contact, body),
  newsletter: (email) => send(ENDPOINTS.publicSite.newsletter, { email }),
  // Always succeeds, even for a made-up token, so tokens can't be probed.
  unsubscribe: (token) => send(ENDPOINTS.publicSite.unsubscribe, null, { token }),
}
