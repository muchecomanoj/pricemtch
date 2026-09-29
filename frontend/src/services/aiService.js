import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'

export const aiService = {
  // LIVE: POST /ai/match — AI candidate matching for a product.
  async match(payload) {
    if (USE_MOCK) {
      return (await mockResponse({
        score: 88, decision: 'MATCH',
        matchedAttributes: ['brand', 'model'], conflicts: [],
        reason: 'Brand and model align; identifiers consistent.',
      }, 900)).data
    }
    return (await api.post(ENDPOINTS.ai.match, payload)).data
  },

  // LIVE: GET /ai/executions — audit log of AI calls (provider, tokens, latency).
  async executions(params = {}) {
    if (USE_MOCK) return (await mockResponse({ content: [], totalElements: 0, totalPages: 1 })).data
    return (await api.get(ENDPOINTS.ai.executions, { params })).data
  },

  // LIVE: GET /ai/analyst/suggestions — starter questions for the chip row.
  async analystSuggestions() {
    if (USE_MOCK) return []
    const { data } = await api.get(ENDPOINTS.aiAnalyst.suggestions)
    return Array.isArray(data) ? data : []
  },

  // LIVE: POST /ai/analyst/ask — deterministic answer computed from stored
  // evidence. Returns { question, intent, answer, items[], source, generatedAt }.
  // Single request/response; there is no streaming to handle.
  async analystAsk(question) {
    if (USE_MOCK) return { question, answer: 'Mock mode — the analyst needs the live backend.' }
    const { data } = await api.post(ENDPOINTS.aiAnalyst.ask, { question })
    return data || {}
  },
}
