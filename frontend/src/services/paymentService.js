import axios from 'axios'

// ---------------------------------------------------------------------------
// Public payment confirmation — NO auth. The client returns here from Stripe
// with its login token gone, so this endpoint must never require a bearer token
// and never 403. It verifies the session with Stripe and activates the plan
// instantly:
//   POST /api/public/payment/confirm   { sessionId }  -> { success, message, data }
//
// Safety net: even if this call is missed, a backend reconciler completes any
// paid-but-unconfirmed payment within ~60s — calling confirm just makes it
// instant and lets us show the success screen right away.
// ---------------------------------------------------------------------------

// Public endpoints live under /api/public (no /api/v1, no auth header). We keep
// the RAW envelope here (no unwrap interceptor) because the confirm flow needs
// both `success` and `message` from { success, message, data }.
const publicApi = axios.create({
  baseURL: import.meta.env.VITE_PUBLIC_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
})

export const paymentService = {
  // Confirm a completed Stripe checkout session. Returns the full envelope
  // { success, message, data } so callers can read the outcome and message
  // (e.g. "Payment is not completed yet.").
  async confirm(sessionId) {
    try {
      const { data } = await publicApi.post('/public/payment/confirm', { sessionId })
      // Normalize to the envelope shape even if the backend ever returns bare data.
      if (data && typeof data === 'object' && 'success' in data) return data
      return { success: true, message: '', data }
    } catch (error) {
      if (error.code === 'ECONNABORTED') {
        throw new Error('The server took too long to respond. Please try again in a moment.')
      }
      if (!error.response) {
        throw new Error('Could not reach the server. Check your connection and try again.')
      }
      // A 4xx/5xx with a backend envelope is a legible failure, not a thrown error
      // the caller must catch differently — return it so the UI can show the message.
      const body = error.response.data
      if (body && typeof body === 'object' && 'success' in body) return body
      throw new Error(body?.message || 'Could not confirm payment. Please refresh in a moment.')
    }
  },
}
