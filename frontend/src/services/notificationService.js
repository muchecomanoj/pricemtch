import api, { USE_MOCK } from './api'
import { ENDPOINTS } from './apiEndpoints'
import { mockResponse } from '../mock/mockHelper'

// Server-persisted notifications behind the header bell.
//
// NOT the same thing as context/NotificationContext, which fires transient
// toasts. This is the record list: GET /notifications, unread-count, and the
// two mark-read PATCHes. The backend creates them on subscription events
// (plan assigned/upgraded/downgraded, payments) — the frontend only reads.
//
// The api.js response interceptor already unwraps { success, message, data },
// so `data` below is the envelope's payload.

const MOCK = [
  { id: 2, type: 'PAYMENT_SUCCESS', title: 'Payment received', message: 'Your payment for the Professional plan was received.', link: '/client/subscription', read: false, createdAt: new Date(Date.now() - 6e5).toISOString() },
  { id: 1, type: 'PLAN_ASSIGNED', title: 'Your plan is now Professional', message: 'An administrator set your plan to Professional (YEARLY).', link: '/client/subscription', read: true, createdAt: new Date(Date.now() - 864e5).toISOString() },
]

export const notificationService = {
  // Badge count. Polled, so keep it cheap and never throw a shape error.
  async unreadCount() {
    if (USE_MOCK) return MOCK.filter((n) => !n.read).length
    const { data } = await api.get(ENDPOINTS.notifications.unreadCount)
    return data?.unread ?? 0
  },

  // Page of notifications, newest first (backend ordering). page is 0-based.
  async list({ page = 0, size = 20 } = {}) {
    if (USE_MOCK) {
      return (await mockResponse({ content: MOCK, totalElements: MOCK.length, totalPages: 1, page: 0, size }, 400)).data
    }
    const { data } = await api.get(ENDPOINTS.notifications.list, { params: { page, size } })
    return {
      content: data?.content || [],
      totalElements: data?.totalElements ?? 0,
      totalPages: data?.totalPages ?? 1,
      page: data?.page ?? page,
      size: data?.size ?? size,
    }
  },

  async markRead(id) {
    if (USE_MOCK) return { ok: true }
    return (await api.patch(ENDPOINTS.notifications.markRead(id))).data
  },

  async markAllRead() {
    if (USE_MOCK) return { updated: MOCK.filter((n) => !n.read).length }
    return (await api.patch(ENDPOINTS.notifications.markAllRead)).data
  },
}
