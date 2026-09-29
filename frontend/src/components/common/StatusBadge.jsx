// Maps common status strings to Bootstrap contextual colors.
const MAP = {
  Matched: 'success', Review: 'warning', Unmatched: 'secondary',
  'In Stock': 'success', 'Low Stock': 'warning', 'Out of Stock': 'danger',
  New: 'success', Used: 'secondary', Refurbished: 'info',
  Low: 'success', Medium: 'warning', High: 'danger',
  Pending: 'warning', Approved: 'success', Rejected: 'danger',
  actual: 'success', calculated: 'info', estimated: 'warning',
  unavailable: 'secondary', stale: 'danger',
  danger: 'danger', warning: 'warning', info: 'info', success: 'success',
  // Product / tenant lifecycle statuses.
  ACTIVE: 'success', DRAFT: 'warning', INACTIVE: 'secondary', ARCHIVED: 'secondary',
  // Marketplace listing conditions.
  NEW: 'success', USED: 'secondary', REFURBISHED: 'info',
  // Sales estimate classification.
  ESTIMATED: 'warning', REPORTED: 'success',
  // Match review decisions (ReviewRequest.decision).
  CANDIDATE: 'info', MATCHED: 'success', EQUIVALENT: 'primary', REJECTED: 'danger',
  // Value classification — FR-REPORT-001 requires every number to carry one.
  ACTUAL: 'success', UNAVAILABLE: 'secondary', CALCULATED: 'info', STALE: 'danger',
  // Recommendation lifecycle.
  APPROVED: 'success', PUBLISHED: 'primary', EXPIRED: 'secondary',
  // Search job status.
  COMPLETED: 'success', RUNNING: 'info', QUEUED: 'warning', FAILED: 'danger',
  // Competitor listing availability.
  IN_STOCK: 'success', LOW_STOCK: 'warning', OUT_OF_STOCK: 'danger',
}

export default function StatusBadge({ status, label }) {
  // Case-insensitive fallback so "ACTIVE"/"active" both resolve.
  const color = MAP[status] || MAP[String(status).toUpperCase()] || MAP[String(status).toLowerCase()] || 'secondary'
  return <span className={`badge rounded-pill text-bg-${color}`}>{label || status}</span>
}
