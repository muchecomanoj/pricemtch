import { FiShield } from 'react-icons/fi'
import { formatCurrency } from '../../utils/format'

// Which policy limit caught a recommendation, and what the engine wanted before
// it did.
//
// Without this a capped recommendation and an uncapped one that happened to
// land on the same number are indistinguishable — so nobody can tell whether a
// policy is protecting them or quietly distorting every answer it touches.
const BOUND = {
  FLOOR_PRICE: 'held by the price floor',
  FLOOR_MARGIN: 'held by the margin floor',
  CEILING: 'held by the price ceiling',
  MAX_CHANGE: 'limited by the max change per cycle',
}

export function BoundNote({ rec: r }) {
  if (!r?.boundApplied) return null
  const label = BOUND[r.boundApplied] || r.boundApplied.toLowerCase().replace(/_/g, ' ')
  return (
    <div className="text-muted small d-inline-flex align-items-start gap-1 mt-1">
      <FiShield size={13} className="flex-shrink-0 mt-1" />
      <span>
        {label}
        {r.unboundedPrice != null && (
          <> · engine suggested {formatCurrency(r.unboundedPrice, r.currency || 'USD')}</>
        )}
      </span>
    </div>
  )
}

// A rolled-back recommendation ends REJECTED, because the audit has to keep the
// fact that it went live — reverting it to draft would erase that. But the list
// must not then read as though it was simply turned down: those are different
// histories and only one of them changed a price.
export function recStatusLabel(r) {
  if (r?.rolledBackAt) return 'Published, then rolled back'
  return r?.status
}
