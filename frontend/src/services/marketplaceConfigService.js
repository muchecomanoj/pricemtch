import api from './api'
import { ENDPOINTS } from './apiEndpoints'
import { configSchema } from '../mock/marketplaceConfig'

// ---------------------------------------------------------------------------
// Marketplace integration config (super-admin). LIVE against /admin/marketplaces:
//   GET  /admin/marketplaces            list (cards + status + capabilities + masked creds)
//   PUT  /admin/marketplaces/{code}    { enabled, credentials } -> updated entry
//   POST /admin/marketplaces/{code}/test -> entry with fresh status/lastMessage
//
// Secrets are write-only: the API returns them masked and only accepts a secret
// when the admin actually types a new one.
// ---------------------------------------------------------------------------
export const MP_CONFIG_LIVE = true

const E = ENDPOINTS.adminMarketplaces

export const marketplaceConfigService = {
  live: MP_CONFIG_LIVE,
  schema: configSchema,   // credential field schema per code (labels + exact keys)

  // All marketplaces with their current status/credentials (super-admin only).
  async list() {
    return (await api.get(E.list)).data
  },

  // payload: { enabled, credentials: {...} } — include only the secrets the admin
  // (re)typed; omitted secrets keep their stored value.
  async save(code, payload) {
    return (await api.put(E.byCode(code), payload)).data
  },

  // Runs a real auth against the stored credentials; returns the updated entry.
  async test(code) {
    return (await api.post(E.test(code))).data
  },
}
