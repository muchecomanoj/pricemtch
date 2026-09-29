import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'
import { competitorListings } from '../mock/data'

// Visual lookup against the tenant's own product images. Only products that
// have an image stored can ever match, and perceptual hashing finds
// near-identical images rather than "another photo of a similar item".
export async function searchByImage(dataUri) {
  if (USE_MOCK) return []
  const { data } = await api.post(ENDPOINTS.products.searchByImage, { image: dataUri })
  return Array.isArray(data) ? data : []
}

export const searchService = {
  // Backend SearchRequest is { query }. It returns { query, matchedStage,
  // totalMatches, trace[], results[] }. The UI reads `.results`, so we adapt.
  async search(payload) {
    if (USE_MOCK) {
      return (await mockResponse({ query: payload, results: competitorListings }, 1200)).data
    }
    const query = payload.value || payload.query || ''
    const { data } = await api.post(ENDPOINTS.search.run, { query })
    return {
      query: data.query,
      matchedStage: data.matchedStage,
      totalMatches: data.totalMatches,
      trace: data.trace || [],
      results: data.results || [],
    }
  },

  async history(params = {}) {
    if (USE_MOCK) return []
    const { data } = await api.get(ENDPOINTS.search.history, { params })
    return data.content || []
  },
}
