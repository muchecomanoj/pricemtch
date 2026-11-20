// One way to turn a failed API call into words for the user.
//
// Every axios client in services/ runs its errors through normalizeApiError,
// so a rejected request always carries:
//   error.message        readable text, never axios's own "Request failed
//                        with status code 500" / "Network Error"
//   error.status         HTTP status, or null when no response came back
//   error.kind           'http' | 'offline' | 'timeout'
//   error.serverMessage  the backend's own sentence, when it sent one
//   error.errorCode      the backend's `code` (SUBSCRIPTION_EXPIRED, …)
//   error.fieldErrors    { field: message } from a validation refusal, or null
//
// Screens show errors with errorMessage(err, 'What failed, in context.'):
//
//   onError: (e) => notify.error(errorMessage(e, 'The rule could not be saved.'))
//   {isError && <div className="alert alert-danger">{errorMessage(error, 'Could not load rules.')}</div>}
//
// The backend's sentence wins, because it knows what went wrong. Without one,
// the screen's own fallback ("The rule could not be saved.") is shown in place
// of a generic status sentence, except where the status itself says what to
// do: no permission, a conflict, too large, too many requests, or no
// connection.

const STATUS_TEXT = {
  400: 'The request was not accepted. Check the values and try again.',
  401: 'Your session has ended. Sign in again.',
  403: 'You don\'t have permission to do that.',
  404: 'That could not be found. It may have been deleted.',
  409: 'That clashes with a change made elsewhere. Refresh and try again.',
  413: 'That file is too large.',
  422: 'The request was not accepted. Check the values and try again.',
  429: 'Too many requests. Wait a moment and try again.',
}
const SERVER_DOWN = 'The server is not responding right now. Please try again in a moment.'
const SERVER_FAULT = 'The server ran into a problem. Please try again in a moment.'
const OFFLINE = 'Could not reach the server. Check your connection and try again.'
const TIMEOUT = 'The server took too long to respond. Please try again in a moment.'

// Statuses whose own sentence tells the user more than a screen's fallback.
const STATUS_WINS = new Set([401, 403, 409, 413, 429])

const statusText = (status) => STATUS_TEXT[status]
  || ([502, 503, 504].includes(status) ? SERVER_DOWN : status >= 500 ? SERVER_FAULT : 'Something went wrong. Please try again.')

// The envelope's `errors` is a { field: message } map. An array form
// ([{ field, message }] or plain strings) is folded into the same shape.
function readFieldErrors(errors) {
  if (!errors || typeof errors !== 'object') return null
  if (Array.isArray(errors)) {
    const map = {}
    errors.forEach((e, i) => {
      if (typeof e === 'string') map[i] = e
      else if (e && (e.message || e.defaultMessage)) map[e.field || i] = e.message || e.defaultMessage
    })
    return Object.keys(map).length ? map : null
  }
  return Object.keys(errors).length ? errors : null
}

// For the response interceptors. Mutates and returns the axios error.
export function normalizeApiError(error) {
  if (!error || error.kind || error.code === 'ERR_CANCELED') return error
  const res = error.response
  const body = res?.data && typeof res.data === 'object' && !(res.data instanceof Blob) ? res.data : {}

  error.status = res?.status ?? null
  error.kind = res ? 'http'
    : (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT' ? 'timeout' : 'offline')
  error.serverMessage = typeof body.message === 'string' && body.message.trim() ? body.message.trim() : null
  error.errorCode = typeof body.code === 'string' ? body.code : null
  error.fieldErrors = readFieldErrors(body.errors)
  error.message = errorMessage(error)
  return error
}

// "Validation failed" alone is no help; add what the fields said.
function withFieldErrors(message, fieldErrors) {
  if (!fieldErrors) return message
  const details = Object.values(fieldErrors).filter((v) => typeof v === 'string').slice(0, 3)
  if (!details.length) return message
  return /^validation failed\.?$/i.test(message) ? details.join(' ') : `${message} ${details.join(' ')}`
}

// The sentence to show for any error: an API error, an Error thrown by a
// service, or a plain string. `fallback` describes what failed on this screen.
export function errorMessage(error, fallback) {
  if (!error) return fallback || 'Something went wrong. Please try again.'
  if (typeof error === 'string') return error

  if (error.kind) {
    if (error.serverMessage) return withFieldErrors(error.serverMessage, error.fieldErrors)
    if (error.kind === 'offline') return OFFLINE
    if (error.kind === 'timeout') return TIMEOUT
    if (STATUS_WINS.has(error.status)) return statusText(error.status)
    return fallback || statusText(error.status)
  }

  // Not from an API client: an Error a service or the page threw itself,
  // whose message was written for the user.
  return error.message || fallback || 'Something went wrong. Please try again.'
}
