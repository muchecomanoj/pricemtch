import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse, paginate } from '../mock/mockHelper'
import { products } from '../mock/data'
import { mapProduct, mapPage, toProductRequest } from './mappers'
import { downloadBlob, blobErrorMessage, filenameFrom } from './downloadService'

// Re-exported so existing importers keep working — every CSV download in the
// app shares one implementation in downloadService.
export { downloadBlob, blobErrorMessage }

// In-memory store so mock create/update/delete feel real.
let store = [...products]

const ASIN_TIMEOUT_MS = 60000
export const EMPTY_ASIN_LOOKUP = { status: 'NOT_FOUND', message: null, retryable: false, proposedCount: 0, suggestions: [] }

// `status` is an untyped string in the contract, so nothing here switches on a
// closed set of values: `message` is the backend's own wording and is shown as
// written, `status` only picks a tone, and anything unrecognised falls back to
// neutral rather than being reported as a failure.
export function normalizeAsinLookup(data) {
  if (!data) return EMPTY_ASIN_LOOKUP
  return {
    status: data.status || 'NOT_FOUND',
    message: data.message || null,
    retryable: !!data.retryable,
    // The model proposed this many; fewer may survive verification against
    // Amazon. The gap is worth showing — it explains a short list.
    proposedCount: data.proposedCount ?? 0,
    suggestions: Array.isArray(data.suggestions) ? data.suggestions : [],
  }
}

export const productService = {
  // GET /products supports search, status, brand, category, identifier, page,
  // size, sortBy and direction — all filtering happens server-side.
  //
  // The query parameter is `search`, not `q`. Sending `q` was silently ignored
  // by the backend, which is why this list had to filter in the browser.
  async list(params = {}) {
    if (USE_MOCK) return (await mockResponse(paginate(store, params))).data
    const {
      page = 1, size = 10, search, status, brand, category, identifier, sortBy, direction,
    } = params
    // Blank strings must become undefined: an empty `brand=` is still a filter
    // to the backend and would match nothing.
    const clean = (v) => (typeof v === 'string' ? v.trim() || undefined : v || undefined)
    const { data } = await api.get(ENDPOINTS.products.list, {
      params: {
        page: page - 1,                  // backend paging is 0-based; UI is 1-based
        size,
        search: clean(search),
        status: clean(status),
        brand: clean(brand),
        category: clean(category),
        identifier: clean(identifier),
        sortBy: clean(sortBy),
        direction: clean(direction),
      },
    })
    const mapped = mapPage(data, mapProduct)
    mapped.page = page // keep UI's 1-based page for the pager
    return mapped
  },

  // CSV of the catalog under the same filters the list is showing.
  //
  // Deliberately has NO mock branch, unlike every other method here. There is
  // no mock CSV to hand back, and inventing one would be worse than failing —
  // a file of fabricated products is the kind of thing that gets emailed on.
  // With VITE_USE_MOCK=true this therefore hits the real backend while the
  // table beside it shows mock rows.
  //
  // Returns the blob AND the server's filename, so a name set by
  // Content-Disposition wins over ours.
  async exportCsv(filters = {}) {
    const clean = (v) => (typeof v === 'string' ? v.trim() || undefined : v || undefined)
    const res = await api.get(ENDPOINTS.products.export, {
      params: {
        search: clean(filters.search),
        status: clean(filters.status),
        brand: clean(filters.brand),
        category: clean(filters.category),
        identifier: clean(filters.identifier),
      },
      responseType: 'blob',
    })
    return { blob: res.data, filename: filenameFrom(res.headers, 'products') }
  },

  // Drives the list filters. Returns every distinct value in the tenant's
  // catalog, so the dropdowns are complete no matter how large it grows.
  async filterOptions() {
    if (USE_MOCK) return { brands: [], categories: [], statuses: [] }
    const { data } = await api.get(ENDPOINTS.products.filterOptions)
    return {
      brands: data?.brands ?? [],
      categories: data?.categories ?? [],
      statuses: data?.statuses ?? [],
    }
  },

  // Proposes real Amazon ASINs for a product we only know by name.
  //
  // The model searches the web, then EVERY candidate is verified against
  // Amazon before it comes back — anything that doesn't resolve is dropped.
  // So the asin is the model's contribution; title, brand, price and
  // availability are all Amazon's. The model is explicitly not asked for a
  // price: in testing it was 22% out of date.
  //
  // 5-20 seconds is normal for that round trip, and the global 20s axios
  // timeout would sometimes cut it off mid-verification.
  async suggestAsin(productId) {
    if (USE_MOCK) return EMPTY_ASIN_LOOKUP
    const { data } = await api.post(ENDPOINTS.products.suggestAsin(productId), null, {
      timeout: ASIN_TIMEOUT_MS,
    })
    return normalizeAsinLookup(data)
  },

  async get(id) {
    if (USE_MOCK) return (await mockResponse(store.find((p) => p.id === Number(id)))).data
    const { data } = await api.get(ENDPOINTS.products.byId(id))
    return mapProduct(data)
  },

  async create(payload) {
    if (USE_MOCK) {
      const item = { ...payload, id: Math.max(0, ...store.map((p) => p.id)) + 1 }
      store = [item, ...store]
      return (await mockResponse(item)).data
    }
    const { data } = await api.post(ENDPOINTS.products.list, toProductRequest(payload, { create: true }))
    return mapProduct(data)
  },

  async update(id, payload) {
    if (USE_MOCK) {
      store = store.map((p) => (p.id === Number(id) ? { ...p, ...payload } : p))
      return (await mockResponse(store.find((p) => p.id === Number(id)))).data
    }
    const { data } = await api.put(ENDPOINTS.products.byId(id), toProductRequest(payload))
    return mapProduct(data)
  },

  async remove(id) {
    if (USE_MOCK) {
      store = store.filter((p) => p.id !== Number(id))
      return (await mockResponse({ ok: true })).data
    }
    return (await api.delete(ENDPOINTS.products.byId(id))).data
  },
}
