import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'

// Competitor-price export across every product. `generate` returns BOTH a
// capped preview (rows) and the complete file (content), so downloading needs
// no second request.
const EMPTY = { columns: [], rows: [], rowCount: 0 }

// "All" means no filter — omit the key rather than sending the literal string.
const clean = (v) => {
  const t = typeof v === 'string' ? v.trim() : v
  return !t || t === 'All' ? undefined : t
}

export const reportService = {
  async generate(values = {}) {
    const body = {
      format: values.format || 'CSV',
      marketplace: clean(values.marketplace),
      category: clean(values.category),
      from: clean(values.from),
      to: clean(values.to),
    }
    if (USE_MOCK) return { ...EMPTY, format: body.format, filename: 'report.csv' }
    const { data } = await api.post(
      ENDPOINTS.reports.generate,
      Object.fromEntries(Object.entries(body).filter(([, v]) => v !== undefined)),
    )
    return data || EMPTY
  },
}

// Saves the returned file. No network call — `content` already holds every row,
// not just the previewed subset.
export function downloadReport(report) {
  if (!report?.content) return
  const blob = new Blob([report.content], { type: report.mimeType || 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = report.filename || 'report'
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
