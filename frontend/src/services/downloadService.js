import api from './api'

// Every CSV export in the app goes through here.
//
// A download cannot be a plain <a href>: the auth token is attached by the
// axios request interceptor, and a browser navigation never passes through it,
// so the link would come back 401. Fetch the bytes first, then hand them to
// the browser.

// Prefer the filename the server set. Falls back to a dated name so repeated
// exports don't pile up as export.csv, export (1).csv, export (2).csv.
export function filenameFrom(headers, fallback = 'export') {
  const cd = headers?.['content-disposition'] || ''
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(cd)
  if (match) return decodeURIComponent(match[1])
  return `${fallback}-${new Date().toISOString().slice(0, 10)}.csv`
}

export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)   // Firefox ignores a click on a detached node
  a.click()
  a.remove()
  // Object URLs live until the tab closes, so each export would otherwise leak
  // the whole file. Deferred because revoking synchronously can cancel the
  // download in some browsers before it has read the URL.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

// With responseType: 'blob' an error body arrives as a Blob too, so
// error.message and error.response.data.message are both undefined and the
// shared extraction helper returns nothing. Read the blob back as text.
export async function blobErrorMessage(error, fallback) {
  try {
    const body = error?.response?.data
    if (body instanceof Blob) {
      const text = await body.text()
      const parsed = JSON.parse(text)
      if (parsed?.message) return parsed.message
    }
  } catch { /* not JSON, or nothing to read — fall through */ }
  return error?.message || fallback
}

// Fetch one CSV and save it. Throws on failure so the caller can report it —
// a download that quietly does nothing is the bug this replaces.
export async function downloadCsv(url, params, fallbackName = 'export') {
  const res = await api.get(url, { params, responseType: 'blob' })
  downloadBlob(res.data, filenameFrom(res.headers, fallbackName))
}
