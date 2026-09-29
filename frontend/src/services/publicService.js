import axios from 'axios'
import { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'

// Public endpoints live under /api/public (NOT /api/v1), and need no auth token.
const publicApi = axios.create({
  baseURL: import.meta.env.VITE_PUBLIC_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
})
publicApi.interceptors.response.use((res) => {
  const b = res.data
  if (b && typeof b === 'object' && 'success' in b && 'data' in b) res.data = b.data
  return res
})

const get = async (path, mock) => {
  if (USE_MOCK) return (await mockResponse(mock)).data
  return (await publicApi.get(path)).data
}
const post = async (path, body) => {
  if (USE_MOCK) return (await mockResponse({ ok: true })).data
  return (await publicApi.post(path, body)).data
}

const mockPlans = [
  { code: 'STARTER', name: 'Starter', price: 49, currency: 'USD', maxUsers: 5, features: 'Basic monitoring' },
  { code: 'PRO', name: 'Pro', price: 199, currency: 'USD', maxUsers: 20, features: 'Advanced + AI' },
  { code: 'ENTERPRISE', name: 'Enterprise', price: 799, currency: 'USD', maxUsers: 100, features: 'Everything' },
]

export const publicService = {
  pricing: () => get(ENDPOINTS.publicSite.pricing, mockPlans),
  plans: () => get(ENDPOINTS.publicSite.plans, mockPlans),
  faqs: () => get(ENDPOINTS.publicSite.faqs, [{ question: 'What is this?', answer: 'A price intelligence platform.' }]),
  features: () => get(ENDPOINTS.publicSite.features, [{ title: 'Price monitoring', description: 'Track competitors.' }]),
  testimonials: () => get(ENDPOINTS.publicSite.testimonials, [{ name: 'Jane', quote: 'Boosted our margins.' }]),
  requestDemo: (body) => post(ENDPOINTS.publicSite.requestDemo, body),
  contact: (body) => post(ENDPOINTS.publicSite.contact, body),
  newsletter: (email) => post(ENDPOINTS.publicSite.newsletter, { email }),
}
