import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'

// Platform admin (SUPER_ADMIN) — the public website's back office:
//   • what visitors sent through the demo / contact / newsletter forms
//   • the landing page's editable sections
const S = ENDPOINTS.adminSubmissions
const L = ENDPOINTS.adminLanding

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true }

export const siteAdminService = {
  // { demoRequestsPending, contactMessagesPending, newsletterSubscribers }
  async summary() {
    if (USE_MOCK) return (await mockResponse({ demoRequestsPending: 0, contactMessagesPending: 0, newsletterSubscribers: 0 })).data
    return (await api.get(S.summary)).data
  },
  // `handled` omitted = all; false = pending only. Newest first.
  async demoRequests({ handled, page = 0, size = 20 } = {}) {
    if (USE_MOCK) return (await mockResponse(emptyPage)).data
    return (await api.get(S.demoRequests, { params: { handled, page, size } })).data
  },
  async contactMessages({ handled, page = 0, size = 20 } = {}) {
    if (USE_MOCK) return (await mockResponse(emptyPage)).data
    return (await api.get(S.contactMessages, { params: { handled, page, size } })).data
  },
  async subscribers({ page = 0, size = 50 } = {}) {
    if (USE_MOCK) return (await mockResponse(emptyPage)).data
    return (await api.get(S.subscribers, { params: { page, size } })).data
  },
  // handled=false undoes it.
  async setDemoHandled(id, handled) {
    if (USE_MOCK) return (await mockResponse({ id, handled })).data
    return (await api.put(S.demoHandled(id), null, { params: { handled } })).data
  },
  async setContactHandled(id, handled) {
    if (USE_MOCK) return (await mockResponse({ id, handled })).data
    return (await api.put(S.contactHandled(id), null, { params: { handled } })).data
  },

  // [{ section, payload: "<json string>", active, updatedAt, updatedBy }] — hidden ones too.
  async landingSections() {
    if (USE_MOCK) return (await mockResponse([])).data
    return (await api.get(L.list)).data
  },
  // `payload` must be the JSON *string*, not an object. Bad JSON comes back
  // as a 400 whose message is the parser's own, so it reads usefully.
  async saveLandingSection(section, { payload, active }) {
    if (USE_MOCK) return (await mockResponse({ section, payload, active })).data
    return (await api.put(L.section(section), { payload, active })).data
  },
}
