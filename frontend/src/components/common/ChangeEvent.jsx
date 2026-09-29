import { FiArrowDown, FiArrowUp, FiMinus, FiUser, FiUsers } from 'react-icons/fi'
import { formatCurrency, formatDateTime } from '../../utils/format'

// One recorded movement on a tracked marketplace item.
//
// oldValue/newValue are always strings, because the same event type carries
// money and stock states. Only the money fields are parsed back to numbers.
export const CHANGE_FIELD = {
  PRICE: 'Price',
  SHIPPING: 'Shipping',
  LANDED_PRICE: 'Landed price',
  AVAILABILITY: 'Availability',
}
const MONEY = ['PRICE', 'SHIPPING', 'LANDED_PRICE']

const AVAILABILITY_LABEL = {
  IN_STOCK: 'In stock', LOW_STOCK: 'Low stock', OUT_OF_STOCK: 'Out of stock',
}

function renderValue(field, raw, currency) {
  if (raw == null || raw === '') return '—'
  if (!MONEY.includes(field)) return AVAILABILITY_LABEL[raw] || raw
  const n = Number(raw)
  return Number.isNaN(n) ? raw : formatCurrency(n, currency || 'USD')
}

// Direction is coloured; the row is not. A feed of tinted bands stops being
// scannable at about six entries, and the delta is the only part that carries
// the signal anyway.
//
// These are COMPETITOR prices, so the sign of the news is inverted from the
// usual reading: a rival undercutting you is the threat, a rival raising price
// is the opening. Colouring a competitor's price cut green would mark the one
// event that needs attention as good news.
const DIRECTION = {
  down: { cls: 'text-danger', Icon: FiArrowDown, hint: 'Competitor is cheaper than before' },
  up: { cls: 'text-success', Icon: FiArrowUp, hint: 'Competitor is more expensive than before' },
  flat: { cls: 'text-muted', Icon: FiMinus, hint: 'No numeric change' },
}

// Amazon gives seller IDs, never names — A294P4X9EWVXLJ is the whole of what
// exists. Truncated so it reads as a reference rather than as a failed lookup,
// with the full id on hover. eBay sends usernames and those pass through whole.
const shortSeller = (id) => (typeof id === 'string' && id.length > 10
  ? `${id.slice(0, 6)}…` : id)

// Whether the price moved, or the shop under it did.
//
// These are opposite instructions. A competitor cutting their own price is
// someone undercutting you — react. Two merchants trading the Buy Box is the
// same product at two standing prices — nothing changed, don't chase it. That
// AirPods listing with 52 "price changes" in six days was mostly the second.
//
// Three states, not two: null means the seller was unknown on one side of the
// comparison, which is normal on early data and must never be shown as "same".
export function SellerChange({ event: e }) {
  if (e.sellerChanged == null) return null
  const changed = e.sellerChanged === true
  return (
    <span className={`mr-pill ${changed ? 'mr-pill--slate' : 'mr-pill--orange'} d-inline-flex align-items-center gap-1`}
      title={changed
        ? `Buy Box moved from ${e.previousSellerId || 'an unknown seller'} to ${e.sellerId || 'another seller'} — the product was not repriced`
        : `Same seller (${e.sellerId || 'unknown'}) changed their own price`}>
      {changed ? <FiUsers size={11} /> : <FiUser size={11} />}
      {changed
        ? <>now {shortSeller(e.sellerId) || 'another seller'}</>
        : 'same seller'}
    </span>
  )
}

// A price cut: a money field that went DOWN, and was not the Buy Box changing
// hands. Both halves are needed — a rise is not a cut, and a cheaper offer from
// a different seller is not the competitor lowering their price, it is a
// different shop at a different standing price.
//
// sellerChanged null means the seller was unknown on one side, which is normal
// on anything recorded before seller tracking began. Those rows are KEPT: a
// genuine decrease should not disappear because we cannot name the merchant.
export function isPriceCut(e) {
  if (!MONEY.includes(e?.field)) return false
  if (e.changeAmount == null || e.changeAmount >= 0) return false
  return e.sellerChanged !== true
}

export function ChangeDelta({ event: e }) {
  const money = MONEY.includes(e.field)
  const dir = !money || e.changeAmount == null ? 'flat'
    : e.changeAmount < 0 ? 'down' : e.changeAmount > 0 ? 'up' : 'flat'
  const { cls, Icon, hint } = DIRECTION[dir]

  return (
    <span className="d-inline-flex align-items-center flex-wrap gap-2">
      <span className="text-muted">{renderValue(e.field, e.oldValue, e.currency)}</span>
      <span className="text-muted">→</span>
      <span className="fw-semibold">{renderValue(e.field, e.newValue, e.currency)}</span>
      {dir !== 'flat' && (
        <span className={`d-inline-flex align-items-center gap-1 ${cls}`} title={hint}>
          <Icon size={13} />
          {/* changePct arrives signed with 4dp; the arrow already carries the
              sign, so a "-" in front of it would state it twice. */}
          {e.changePct != null && <span>{Math.abs(e.changePct).toFixed(1)}%</span>}
        </span>
      )}
      <SellerChange event={e} />
    </span>
  )
}

// How long the old value stood before the new one was seen.
//
// The gap is the point, not the two dates. Both readings of a 17% drop taken
// 31 minutes apart fall on the same calendar day, so printing dates alone
// renders the most important fact — that it moved in half an hour — invisible.
// A move that size over 31 minutes is usually a different seller taking the
// Buy Box; the same move over three weeks is a real repricing.
function spanLabel(from, to) {
  const a = Date.parse(from)
  const b = Date.parse(to)
  if (!Number.isFinite(a) || !Number.isFinite(b) || b <= a) return null
  const mins = Math.round((b - a) / 60000)
  if (mins < 1) return 'under a minute'
  if (mins < 60) return `${mins} minute${mins === 1 ? '' : 's'}`
  const hrs = Math.round(mins / 60)
  if (hrs < 48) return `${hrs} hour${hrs === 1 ? '' : 's'}`
  const days = Math.round(hrs / 24)
  if (days < 14) return `${days} days`
  const weeks = Math.round(days / 7)
  if (weeks < 9) return `${weeks} weeks`
  const months = Math.round(days / 30)
  return `${months} month${months === 1 ? '' : 's'}`
}

// Both timestamps, always. "Fell 5%" and "fell 5% over three weeks" are
// different claims, and a move measured against a stale reading is far weaker
// evidence than one against yesterday's.
export function ChangeWhen({ event: e }) {
  const span = e.previousObservedAt ? spanLabel(e.previousObservedAt, e.observedAt) : null
  return (
    <div className="text-muted small text-nowrap">
      <div>{formatDateTime(e.observedAt)}</div>
      {e.previousObservedAt && (
        <div style={{ fontSize: 11 }}>
          {span ? <>over {span} · from </> : 'previous value seen '}
          {formatDateTime(e.previousObservedAt)}
        </div>
      )}
    </div>
  )
}
