import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'

// LIVE product bulk-import endpoints (Phase 1 backend).
export const importService = {
  // GET /products/imports — list import jobs (paged)
  async list(params = {}) {
    if (USE_MOCK) return (await mockResponse({ content: [], totalElements: 0, totalPages: 1 })).data
    return (await api.get(ENDPOINTS.products.imports, { params })).data
  },

  // POST /products/imports — start an import (multipart file upload)
  async create(file, mapping) {
    if (USE_MOCK) return (await mockResponse({ id: Date.now(), status: 'PROCESSING' }, 900)).data
    const form = new FormData()
    form.append('file', file)
    if (mapping) form.append('mapping', JSON.stringify(mapping))
    return (await api.post(ENDPOINTS.products.imports, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })).data
  },

  // POST /products/imports/preview — validate + preview rows before committing
  async preview(file) {
    if (USE_MOCK) return (await mockResponse({ rows: [], columns: [], errors: [] }, 700)).data
    const form = new FormData()
    form.append('file', file)
    return (await api.post(ENDPOINTS.products.importPreview, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })).data
  },

  // GET /products/imports/{id} — job status/progress
  async get(id) {
    if (USE_MOCK) return (await mockResponse({ id, status: 'COMPLETED', processed: 0 })).data
    return (await api.get(ENDPOINTS.products.importById(id))).data
  },

  // GET /products/imports/{id}/errors — the row-level error EXPORT.
  //
  // This returns a file (the schema is string/byte), not a JSON array. It was
  // being read as rows and rendered with .map(), which throws on a string and
  // took the whole page down with it — a blank screen every time an import
  // finished with any errors at all.
  //
  // FR-PROD-002 calls for "row-level error export", so a downloadable file is
  // the documented behaviour; the frontend was simply reading it wrong.
  async errorReport(id) {
    if (USE_MOCK) return new Blob([''], { type: 'text/csv' })
    const res = await api.get(ENDPOINTS.products.importErrors(id), { responseType: 'blob' })
    return res.data
  },
}
