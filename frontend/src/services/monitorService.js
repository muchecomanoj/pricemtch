import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'

// Scheduled competitor discovery — GET/POST /monitors, PATCH/DELETE /monitors/{id}.
// Writes need ADMIN, MANAGER or SUPER_ADMIN.
//
// The api.js interceptor unwraps { success, message, data }, so `data` here is
// already the payload. Error messages are copied onto error.message by the same
// interceptor, which matters here: the backend returns a specific 400 when the
// requested cadence is below the plan floor, and that text is worth showing
// verbatim rather than replacing with something generic.

const E = ENDPOINTS.monitors

export const monitorService = {
  async list({ productId, enabled } = {}) {
    if (USE_MOCK) return []
    const { data } = await api.get(E.list, {
      params: {
        productId: productId ?? undefined,
        // `false` is a real filter value, so only undefined may be dropped.
        enabled: enabled === undefined ? undefined : enabled,
      },
    })
    return Array.isArray(data) ? data : []
  },

  // markets omitted or empty => every enabled channel, matching search-jobs.
  async create({ productId, markets, maxResults, intervalMinutes, enabled = true }) {
    if (USE_MOCK) return { id: 0, productId, enabled }
    const body = { productId, maxResults, intervalMinutes, enabled }
    if (markets?.length) body.markets = markets
    return (await api.post(E.list, body)).data
  },

  // PATCH is partial — send only what changed, so toggling `enabled` from a
  // list row can't overwrite a cadence someone set elsewhere.
  async update(id, patch) {
    if (USE_MOCK) return { id, ...patch }
    return (await api.patch(E.byId(id), patch)).data
  },

  async remove(id) {
    if (USE_MOCK) return { ok: true }
    return (await api.delete(E.byId(id))).data
  },
}

// Cadence choices offered in the UI. The backend enforces the plan floor
// regardless of what the client sends; these are capped for convenience only.
export const INTERVALS = [
  { minutes: 60, label: 'Every hour' },
  { minutes: 180, label: 'Every 3 hours' },
  { minutes: 360, label: 'Every 6 hours' },
  { minutes: 720, label: 'Every 12 hours' },
  { minutes: 1440, label: 'Once a day' },
  { minutes: 4320, label: 'Every 3 days' },
  { minutes: 10080, label: 'Once a week' },
]

export function intervalLabel(minutes) {
  const known = INTERVALS.find((i) => i.minutes === minutes)
  if (known) return known.label
  if (minutes == null) return '—'
  if (minutes % 1440 === 0) return `Every ${minutes / 1440} days`
  if (minutes % 60 === 0) return `Every ${minutes / 60} hours`
  return `Every ${minutes} min`
}
