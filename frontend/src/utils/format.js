// Display formatters. Deterministic formatting only; never derive business values here.

export const formatCurrency = (n, currency = 'USD') =>
  typeof n === 'number'
    ? new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(n)
    : '—'

export const formatNumber = (n) =>
  typeof n === 'number' ? new Intl.NumberFormat('en-US').format(n) : '—'

export const formatPercent = (n) => (typeof n === 'number' ? `${n.toFixed(1)}%` : '—')

// Auto-generate a tenant company code from the organization name.
// "Suyog India Pvt Ltd" -> "SUYOGIND-4821". Kept short, uppercase, unique-ish.
export function generateCompanyCode(name = '') {
  const slug = name
    .normalize('NFKD')
    .replace(/[^a-zA-Z0-9\s]/g, '')      // strip punctuation
    .trim()
    .split(/\s+/)
    .join('')
    .toUpperCase()
    .slice(0, 8)
  const base = slug || 'CLIENT'
  const suffix = Math.floor(1000 + Math.random() * 9000) // 4 digits
  return `${base}-${suffix}`
}

export const formatValue = (item) => {
  const { value, prefix = '', suffix = '' } = item
  if (value == null) return '—'
  const body = suffix === '%' ? value.toFixed(1) : formatNumber(value)
  return `${prefix}${body}${suffix}`
}

// Whole days between today and an ISO/date string (negative once it has passed).
// Compared at day boundaries so "ends today" is 0, not a fractional value.
export function daysUntil(dateStr) {
  if (!dateStr) return null
  const end = new Date(dateStr)
  if (Number.isNaN(end.getTime())) return null
  const startOfDay = (d) => new Date(d.getFullYear(), d.getMonth(), d.getDate())
  return Math.round((startOfDay(end) - startOfDay(new Date())) / 86400000)
}

// The year is dropped for dates in the CURRENT year and kept for every other.
//
// Almost everything on screen is recent, so "2026" repeats on every row and
// carries no information — until the one row that is from last year, where its
// absence would be read as "this year" and quietly mislead. Showing it only
// when it differs makes the year meaningful again: if you see one, the date is
// not from this year.
const sameYear = (dt) => dt.getFullYear() === new Date().getFullYear()

export const formatDate = (d) => {
  if (!d) return '—'
  const dt = new Date(d)
  if (Number.isNaN(dt.getTime())) return d
  return dt.toLocaleDateString(undefined, {
    day: '2-digit', month: 'short', ...(sameYear(dt) ? {} : { year: 'numeric' }),
  })
}

// Date AND time, in the viewer's zone.
//
// For the tracked-item timestamps specifically (firstSeenAt, lastSeenAt,
// lastChangedAt, observedAt, previousObservedAt) this is safe: they are UTC and
// carry a Z, so Date parses them correctly and toLocale* converts. Do NOT use
// it on createdAt/updatedAt, which are zone-less server-local audit fields and
// are not what anyone means by "when".
export const formatDateTime = (d) => {
  if (!d) return '—'
  const dt = new Date(d)
  if (Number.isNaN(dt.getTime())) return d
  return dt.toLocaleString(undefined, {
    day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit',
    ...(sameYear(dt) ? {} : { year: 'numeric' }),
  })
}

// Each unit's size in terms of the previous one; the last entry ends the walk.
const RELATIVE_UNITS = [
  ['second', 60], ['minute', 60], ['hour', 24],
  ['day', 7], ['week', 4.345], ['month', 12], ['year', Infinity],
]

// "5 minutes ago" / "in 2 days", falling back to an absolute date past a year.
// NOTE: backend timestamps arrive without an offset (2026-07-29T17:20:00), so
// JS parses them in the viewer's local zone. Accurate only while the server and
// the user share a timezone — ask the backend for an offset or Z suffix to fix.
export function formatRelativeTime(value) {
  if (!value) return ''
  const then = new Date(value)
  if (Number.isNaN(then.getTime())) return ''
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
  let delta = (then.getTime() - Date.now()) / 1000
  for (const [unit, span] of RELATIVE_UNITS) {
    if (Math.abs(delta) < span) return rtf.format(Math.round(delta), unit)
    delta /= span
  }
  return formatDate(value)
}
