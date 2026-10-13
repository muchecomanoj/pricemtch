import axios from 'axios'
import { appPath, currentRoute } from '../utils/appPath'

// ---------------------------------------------------------------------------
// Centralized Axios instance.
//
// Real backend contract (springdoc / Spring Boot):
//   • Base path:      /api/v1
//   • Auth:           JWT bearer token (access token expires in ~900s)
//   • Response shape: { success, message, data, errors, timestamp }
//
// The Vite dev proxy forwards /api -> the backend (see vite.config.js), which
// avoids CORS. To point elsewhere, set VITE_API_BASE_URL in .env.local.
// ---------------------------------------------------------------------------

export const USE_MOCK = (import.meta.env.VITE_USE_MOCK ?? 'true') === 'true'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

const api = axios.create({
  baseURL: BASE_URL,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' },
})

// Attach the JWT on every request.
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// ── Token refresh ──────────────────────────────────────────────
// The access token is short-lived, so a 401 usually means "expired", not
// "unauthorized". Transparently refresh once and replay the original request.
// Concurrent 401s queue behind a single refresh instead of stampeding it
// (important: the backend rotates refresh tokens — reusing one fails).

let isRefreshing = false
let waiters = []

const flushWaiters = (error, token = null) => {
  waiters.forEach(({ resolve, reject }) => (error ? reject(error) : resolve(token)))
  waiters = []
}

// Pages that work without signing in (the routes outside ProtectedRoute in
// AppRoutes.jsx). A leftover token in the browser still makes the app check
// the session on these, and when that session turns out to be dead the user
// must stay where they are — not be bounced to the login page. Matched as
// prefixes, after the base folder is stripped ('/pricing' covers
// '/pricing-legacy'); '/' only exactly.
const PUBLIC_ROUTES = [
  '/login', '/two-factor', '/forgot-password', '/reset-password',
  '/signup', '/register/', '/activate', '/onboarding/', '/billing/',
  '/unsubscribe', '/home', '/pricing', '/403', '/error',
]
const onPublicPage = () => {
  const route = currentRoute()
  return route === '/' || PUBLIC_ROUTES.some((p) => route.startsWith(p))
}

function clearSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
}

// Bare client so the refresh call never re-enters these interceptors.
async function requestNewToken(refreshToken) {
  const { data } = await axios.post(
    `${BASE_URL}/auth/refresh`,
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' } }
  )
  const payload = data?.data ?? data // unwrap ApiResponse envelope
  if (!payload?.accessToken) throw new Error('No access token in refresh response')
  localStorage.setItem('token', payload.accessToken)
  // The backend rotates the refresh token — always store the new one.
  if (payload.refreshToken) localStorage.setItem('refreshToken', payload.refreshToken)
  return payload.accessToken
}

api.interceptors.response.use(
  (res) => {
    // Unwrap the ApiResponse envelope so services receive the payload directly:
    // response.data === envelope.data
    const body = res.data
    if (body && typeof body === 'object' && 'success' in body && 'data' in body) {
      res.data = body.data
    }
    return res
  },
  async (error) => {
    const original = error.config
    const status = error.response?.status
    const url = original?.url || ''
    // Never try to refresh the auth calls themselves.
    const isAuthCall = /\/auth\/(refresh|login|logout)/.test(url)

    if (status === 401 && original && !original._retry && !isAuthCall) {
      const refreshToken = localStorage.getItem('refreshToken')
      if (!refreshToken) {
        clearSession()
        return Promise.reject(error)
      }

      // A refresh is already running — wait for it, then replay.
      if (isRefreshing) {
        return new Promise((resolve, reject) => waiters.push({ resolve, reject }))
          .then((token) => {
            original._retry = true
            original.headers.Authorization = `Bearer ${token}`
            return api(original)
          })
      }

      original._retry = true
      isRefreshing = true
      try {
        const newToken = await requestNewToken(refreshToken)
        flushWaiters(null, newToken)
        original.headers.Authorization = `Bearer ${newToken}`
        return api(original) // replay the original request
      } catch (refreshError) {
        flushWaiters(refreshError)
        clearSession()
        // Session is unrecoverable — send the user to sign in. Not from a
        // public page, though: someone opening an activation link or coming
        // back from Stripe chose that URL, and a dead session from an earlier
        // visit is no reason to throw it (and its token) away.
        if (!onPublicPage()) window.location.assign(appPath('/login'))
        return Promise.reject(error)
      } finally {
        isRefreshing = false
      }
    }

    // Refusals driven by the company's subscription carry a code. A 403 without
    // one is an ordinary permission error and is left exactly as it was.
    //
    // This file sits outside React, so it announces the change rather than
    // making it: the auth state and the banner listen for these events.
    if (status === 403 && !isAuthCall) {
      const body = error.response?.data || {}
      if (body.code === 'SUBSCRIPTION_EXPIRED') {
        // The plan lapsed while signed in. Nothing to undo — the request was
        // refused before it did anything — so the app just switches to
        // read-only and says why.
        window.dispatchEvent(new CustomEvent('account:read-only', {
          detail: { message: body.message, access: body.access || 'READ_ONLY' },
        }))
      } else if (body.code === 'ACCOUNT_BLOCKED') {
        // Suspended or cancelled mid-session: sign out, and carry the reason
        // to the login page so the user is not left guessing.
        clearSession()
        try { sessionStorage.setItem('loginNotice', body.message || 'Your account has been suspended.') } catch { /* storage blocked */ }
        if (!onPublicPage()) window.location.assign(appPath('/login'))
      }
    }

    // Surface the backend's error message when present.
    const msg = error.response?.data?.message
    if (msg) error.message = msg
    return Promise.reject(error)
  }
)

export default api
