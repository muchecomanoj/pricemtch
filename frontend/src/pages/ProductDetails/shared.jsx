import { FiSearch, FiInfo } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import EmptyState from '../../components/common/EmptyState'
import { formatCurrency, formatRelativeTime } from '../../utils/format'

export const money = (v, ccy = 'USD') => (v == null || v === '' ? '—' : formatCurrency(Number(v), ccy))
export const pctOf = (v) => (v == null || v === '' ? '—' : `${Number(v).toFixed(1)}%`)

// `flag` is a short qualifier shown beside the number — a caveat that belongs
// ON the figure it qualifies, not in a banner further down the page where the
// reader has to work out which card it refers to.
export function Kpi({ icon: Icon, label, value, grad = 'primary', hint, flag }) {
  return (
    <div className="col-6 col-md-4 col-xl-2">
      <Card hover className="h-100">
        <div className={`icon-box icon-grad-${grad} mb-2`}><Icon /></div>
        <div className="d-flex align-items-center gap-2 flex-wrap">
          <div className="h5 fw-bold mb-0">{value}</div>
          {flag}
        </div>
        <div className="text-muted small">{label}</div>
        {hint && <div className="text-muted mt-1" style={{ fontSize: 11 }}>{hint}</div>}
      </Card>
    </div>
  )
}

// Source timestamps must be visible (FR-PRICE-001 / FR-SRCH-003).
export function AsOf({ value }) {
  if (!value) return null
  return <span className="text-muted" style={{ fontSize: 11 }}>as of {formatRelativeTime(value)}</span>
}

// `fetchedAt` lives in components/common/MatchEvidence — Match Review shows
// the same listings and must apply the same rule. Re-exported so the tabs on
// this page keep importing their helpers from one place.
export { fetchedAt } from '../../components/common/MatchEvidence'

// Everything competitor-derived is empty until a search job has run.
export function NeedsSearch({ onSearch, canSearch, busy, what = 'competitors' }) {
  return (
    <Card>
      <EmptyState
        icon={FiSearch}
        title={`No ${what} yet`}
        message="Run a search to look for this product across the enabled marketplaces."
        action={canSearch
          ? <Button icon={FiSearch} loading={busy} onClick={onSearch}>Find competitors</Button>
          : <span className="text-muted small">Ask an admin or manager to run a search.</span>}
      />
    </Card>
  )
}

// Where we sit on price, from the market-prices response.
//
// Two rules from the backend, easy to get wrong:
//   · ties rank JOINT-first — matching the cheapest is rank 1, not 2 — so no
//     tiebreak of our own goes on top
//   · marketRankTotal ALREADY includes us, so never add 1 to it
export function rankOf(mp = {}) {
  const { marketRank: rank, marketRankTotal: total, priceGapToLowest: gap, marketRankBasis: basis } = mp
  // null means we have no price of our own, or no competitor has one. Showing
  // "1" there reads as market-leading when the truth is "we do not know" —
  // that is the failure mode worth avoiding.
  if (rank == null || total == null) return { known: false }
  return { known: true, rank, total, gap, basis, label: `${rank} of ${total}` }
}

// How much to trust the rank. Unreviewed candidates are counted in it, and a
// spare part sitting among the offers moves it materially — the Samsung fridge
// ranks 2 of 8 only because a door shelf and a defrost sensor are still in the
// set. Without this the user acts on a wrong number with no way to know.
export const RANK_CAVEAT = 'Includes unreviewed candidates — confirm matches for an accurate rank.'
export const rankIsProvisional = (basis) => basis === 'MIXED' || basis === 'UNREVIEWED'

// "$4.99 below the cheapest" — the sign carries the meaning, so it is spelled
// out rather than left as a signed number to interpret.
export function gapLabel(gap, ccy) {
  if (gap == null) return null
  if (gap < 0) return `${money(Math.abs(gap), ccy)} below the cheapest`
  if (gap > 0) return `${money(gap, ccy)} above the cheapest`
  return 'Matching the cheapest'
}

// Which cost profile produced a profitability figure. TENANT_DEFAULT means the
// product has no profile of its own and inherited the global cost model, so the
// numbers move if someone edits Cost Management.
export function CostSourceHint({ source }) {
  if (source !== 'TENANT_DEFAULT') return null
  return (
    <div className="d-flex align-items-start gap-2 text-muted small mt-2">
      <FiInfo className="flex-shrink-0 mt-1" />
      <span>Using the global cost defaults — this product has no cost profile of its own.</span>
    </div>
  )
}

// Labelled read-only figure used across the profitability panel.
export function Stat({ label, value, strong = false, tone }) {
  return (
    <div className="d-flex align-items-center justify-content-between py-1">
      <span className="text-muted small">{label}</span>
      <span className={`${strong ? 'fw-bold' : 'fw-semibold'} ${tone ? `text-${tone}` : ''}`}>{value}</span>
    </div>
  )
}
