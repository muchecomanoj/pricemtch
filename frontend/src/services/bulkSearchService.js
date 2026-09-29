import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'

// Bulk product search.
//
// Takes the same spreadsheet as the product import and does the opposite with
// it: searches every row against the marketplaces and saves nothing. Rows are
// promoted to real products one at a time, by a person, after reading the
// verdict. /products/imports creates products immediately and never calls a
// marketplace — same file, two different intentions.
//
// Throughput is ~3 rows a minute, capped by the AI token budget rather than by
// the implementation, so a 100-row file takes over half an hour. Every screen
// that starts one has to say so before the upload, not after.
const EMPTY_PAGE = { content: [], totalElements: 0, totalPages: 0, number: 0, size: 25 }

// Statuses that mean the job is still working, so polling should continue.
export const RUNNING = ['PENDING', 'PROCESSING']

// matchesJson / rejectedJson arrive as JSON STRINGS, unlike the single-search
// response where the same data is a real array. A row whose blob will not parse
// still renders — losing the whole row over a malformed field would hide the
// input columns and the message, which are the parts that explain what went
// wrong.
export function parseListings(raw) {
  if (Array.isArray(raw)) return raw
  if (typeof raw !== 'string' || !raw.trim()) return []
  try {
    const v = JSON.parse(raw)
    return Array.isArray(v) ? v : []
  } catch { return [] }
}

export const bulkSearchService = {
  // POST /search/bulk — returns 202 with the job; it does not wait.
  async start(file, { judge = true, country, postalCode } = {}) {
    if (USE_MOCK) return { id: 0, status: 'PENDING', totalRows: 0 }
    const form = new FormData()
    form.append('file', file)
    const { data } = await api.post(ENDPOINTS.bulkSearch.root, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      // Destination rides as query params here, not in the body.
      params: { judge, country: country || undefined, postalCode: postalCode || undefined },
    })
    return data
  },

  async job(id) {
    if (USE_MOCK) return null
    return (await api.get(ENDPOINTS.bulkSearch.job(id))).data
  },

  async rows(id, { status, page = 0, size = 25 } = {}) {
    if (USE_MOCK) return EMPTY_PAGE
    const { data } = await api.get(ENDPOINTS.bulkSearch.rows(id), {
      params: { status: status || undefined, page, size },
    })
    return data || EMPTY_PAGE
  },

  // Promotes one row to a real product.
  //
  // Returns a typed outcome, and every one of them is HTTP 200 — CREATED,
  // EXISTS, UPDATED and UNCHANGED are all successful answers to "save this".
  // EXISTS in particular is NOT a failure: the product is already in the
  // catalogue and the caller is being asked whether to overwrite it, which is
  // why it must not be surfaced as an error toast.
  //
  // `update: true` is the second call, made only after a person says yes.
  async save(jobId, rowId, { sku, update = false } = {}) {
    if (USE_MOCK) return { status: 'CREATED', productId: 0, differences: [] }
    const { data } = await api.post(ENDPOINTS.bulkSearch.save(jobId, rowId), null, {
      params: {
        sku: sku?.trim() || undefined,
        update: update || undefined,
      },
    })
    return data
  },

  async history({ page = 0, size = 10 } = {}) {
    if (USE_MOCK) return EMPTY_PAGE
    return (await api.get(ENDPOINTS.bulkSearch.root, { params: { page, size } })).data || EMPTY_PAGE
  },
}
