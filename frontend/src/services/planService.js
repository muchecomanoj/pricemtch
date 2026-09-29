import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'

const mockPlans = [
  { id: 1, code: 'STARTER', name: 'Starter', price: 49, currency: 'USD', billingCycleDays: 30, maxUsers: 5, features: 'Basic', active: true },
  { id: 2, code: 'PRO', name: 'Pro', price: 199, currency: 'USD', billingCycleDays: 30, maxUsers: 20, features: 'Advanced', active: true },
  { id: 3, code: 'ENTERPRISE', name: 'Enterprise', price: 799, currency: 'USD', billingCycleDays: 30, maxUsers: 100, features: 'All', active: true },
]

export const planService = {
  async list() {
    if (USE_MOCK) return (await mockResponse(mockPlans)).data
    return (await api.get(ENDPOINTS.adminPlans.list)).data
  },
  async create(payload) {
    if (USE_MOCK) return (await mockResponse({ id: Date.now(), ...payload })).data
    return (await api.post(ENDPOINTS.adminPlans.create, payload)).data
  },
  async update(id, payload) {
    if (USE_MOCK) return (await mockResponse({ id, ...payload })).data
    return (await api.put(ENDPOINTS.adminPlans.byId(id), payload)).data
  },
}
