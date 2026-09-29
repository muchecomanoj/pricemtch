import api from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'
import { EMAIL_TEMPLATES } from '../mock/emailTemplates'

// ---------------------------------------------------------------------------
// Email templates (super-admin). LIVE against /admin/email-templates:
//   GET  /admin/email-templates              list (cards)
//   GET  /admin/email-templates/{key}         one template
//   PUT  /admin/email-templates/{key}        { subject, body }  -> updated (edited:true)
//   GET  /admin/email-templates/{key}/preview -> { subject, htmlBody } (saved template, sample data)
//   POST /admin/email-templates/{key}/test   { toEmail }
//
// Set TEMPLATES_LIVE = false to run the editor on the bundled defaults (edits
// persist for the session only) if the endpoint is ever unavailable.
// ---------------------------------------------------------------------------
export const TEMPLATES_LIVE = true

// Session-persistent mock store (a copy so edits don't mutate the defaults).
let store = null
const mock = () => (store ||= EMAIL_TEMPLATES.map((t) => ({ ...t })))

const E = ENDPOINTS.adminEmailTemplates

export const templateService = {
  live: TEMPLATES_LIVE,

  async list() {
    if (!TEMPLATES_LIVE) return (await mockResponse(mock(), 300)).data
    return (await api.get(E.list)).data
  },

  async get(key) {
    if (!TEMPLATES_LIVE) return (await mockResponse(mock().find((t) => t.key === key), 200)).data
    return (await api.get(E.byKey(key))).data
  },

  async update(key, { subject, body }) {
    if (!TEMPLATES_LIVE) {
      const t = mock().find((x) => x.key === key)
      if (t) { t.subject = subject; t.body = body; t.edited = true }
      return (await mockResponse(t, 400)).data
    }
    return (await api.put(E.byKey(key), { subject, body })).data
  },

  // Server-rendered preview of the SAVED template with realistic sample values.
  async preview(key) {
    if (!TEMPLATES_LIVE) {
      const t = mock().find((x) => x.key === key)
      return (await mockResponse({ subject: t?.subject || '', htmlBody: `<pre>${t?.body || ''}</pre>` }, 200)).data
    }
    return (await api.get(E.preview(key))).data
  },

  async sendTest(key, toEmail) {
    if (!TEMPLATES_LIVE) return (await mockResponse({ sent: true, toEmail }, 500)).data
    return (await api.post(E.test(key), { toEmail })).data
  },
}
