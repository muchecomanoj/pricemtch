import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'

// SMTP settings for outbound mail (activation + welcome emails).
//
// PASSWORD RULE: the API never returns the stored password — only
// `passwordSet: true|false`. So the payload omits `password` unless the user
// actually typed a new one; sending an empty string would wipe a working
// credential the moment someone edits the host.

const EMPTY = { enabled: false, configured: false, status: 'NOT_CONFIGURED', passwordSet: false }

export const emailConfigService = {
  async get() {
    if (USE_MOCK) return EMPTY
    return (await api.get(ENDPOINTS.emailConfig.get)).data || EMPTY
  },

  async save(values) {
    const body = {
      enabled: !!values.enabled,
      host: values.host?.trim() || null,
      port: values.port === '' || values.port == null ? null : Number(values.port),
      username: values.username?.trim() || null,
      fromAddress: values.fromAddress?.trim() || null,
      fromName: values.fromName?.trim() || null,
      startTls: !!values.startTls,
      sslEnabled: !!values.sslEnabled,
    }
    // Only send the password when it was retyped — omitting it keeps the existing one.
    if (values.password) body.password = values.password

    if (USE_MOCK) return { ...EMPTY, ...body, configured: true, status: 'CONFIGURED' }
    return (await api.put(ENDPOINTS.emailConfig.get, body)).data
  },

  // Tests the SAVED settings, not whatever is on screen — save first.
  async test() {
    if (USE_MOCK) return { status: 'CONNECTED', lastMessage: 'Connection successful' }
    return (await api.post(ENDPOINTS.emailConfig.test)).data
  },
}
