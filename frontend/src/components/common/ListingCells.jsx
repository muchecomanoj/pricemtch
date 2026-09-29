import { useState } from 'react'
import { FiInfo, FiAlertTriangle } from 'react-icons/fi'
import { formatCurrency } from '../../utils/format'

// Cells shared by the screens that render a marketplace listing: the two
// competitor-listing tables, search results, and the listing detail page.

// How trustworthy a single figure is. The backend sends `valueStatus` as a map
// keyed by field name, so a row can have a real price and an estimated margin
// at the same time.
//
// ACTUAL is the normal case and gets no mark — flagging everything would make
// the exceptions invisible, which is the whole point of having the flag.
const VALUE_FLAG = {
  STALE: { icon: FiAlertTriangle, cls: 'text-warning', title: 'Stale — this figure has not been refreshed recently.' },
  ESTIMATED: { icon: FiInfo, cls: 'text-muted', title: 'Estimated — derived, not observed.' },
  CALCULATED: { icon: FiInfo, cls: 'text-muted', title: 'Calculated from other values.' },
  UNAVAILABLE: { icon: FiAlertTriangle, cls: 'text-warning', title: 'The source does not provide this figure.' },
}

export function ValueFlag({ status }) {
  const flag = VALUE_FLAG[status]
  if (!flag) return null
  const Icon = flag.icon
  return <Icon size={12} className={`${flag.cls} ms-1`} style={{ verticalAlign: 'baseline' }} title={flag.title} />
}

// Estimated margin at this listing's landed price.
//
// `marginBasis` decides what is honest to show, so the number alone is never
// enough. A missing cost profile is not a zero margin, and a figure computed
// from generic tenant defaults must not look identical to one derived from the
// product's own costs.
export function MarginCell({ listing }) {
  const { estimatedMargin: amt, estimatedMarginPct: pct, marginBasis: basis, currency, valueStatus } = listing

  if (basis === 'CURRENCY_MISMATCH') {
    return (
      <span className="text-muted"
        title="This listing is priced in another currency and no exchange rate is available.">
        —
      </span>
    )
  }
  if (!basis || amt == null) {
    return <span className="text-muted small">Add costs</span>
  }

  const tone = amt < 0 ? 'text-danger' : 'text-success'
  return (
    <span title={basis === 'TENANT_DEFAULT'
      ? 'Estimated from your global cost defaults — this product has no cost profile of its own.'
      : 'Calculated from this product’s own cost profile.'}>
      <span className={`fw-semibold ${tone}`}>{formatCurrency(amt, currency || 'USD')}</span>
      {pct != null && <span className="text-muted small ms-1">{Number(pct).toFixed(1)}%</span>}
      {/* TENANT_DEFAULT is an estimate however precise the number looks. */}
      {basis === 'TENANT_DEFAULT' && <ValueFlag status="ESTIMATED" />}
      <ValueFlag status={valueStatus?.estimatedMargin} />
    </span>
  )
}

// The barcodes a listing carries, beside the marketplace item ID that is
// already on the card.
//
// ASIN is dropped because the card shows it next to the marketplace name and a
// second copy reads as a different identifier. Everything else is kept as sent,
// including EAN/GTIN pairs that differ only by leading zeros — 195949688522,
// 0195949688522 and 00195949688522 are the same barcode in three registries,
// and which one a supplier file uses is exactly what makes this worth showing.
// Search results send a real array; tracked items send the same data as a JSON
// STRING. Both shapes are absorbed here rather than at four call sites — and a
// string that will not parse yields nothing rather than throwing, because a
// malformed identifier blob is not a reason to lose the whole listing.
export function parseIdentifiers(raw) {
  if (Array.isArray(raw)) return raw
  if (typeof raw !== 'string' || !raw.trim()) return []
  try {
    const v = JSON.parse(raw)
    return Array.isArray(v) ? v : []
  } catch { return [] }
}

// A long-lived product distributed across many regions accumulates a barcode
// per packaging per market — the TI-84 Plus carries close to a hundred. Printing
// them all buries the title, the price and the verdict under a wall of digits.
//
// So a handful is shown and the rest are counted. Nothing is dropped: the full
// list is one click away, because the reason to show these at all is
// reconciling against a supplier's file, and that needs the actual numbers.
const IDENTIFIER_LIMIT = 5

// UPC-12, EAN-13 and GTIN-14 are the same number at different lengths, so one
// barcode arrives as three entries — "UPC 622356535588 · EAN 0622356535588 ·
// GTIN 00622356535588" is one code printed three times. Leading zeros are
// padding, not data, so they set the group; the digits that remain are what a
// supplier file is matched on. A 14-digit code whose extra digit is NOT a zero
// (10622356535588) is a different barcode — usually a case or multipack — and
// stays its own row.
const GTIN_FAMILY = new Set(['GTIN', 'UPC', 'EAN', 'GTIN_12', 'GTIN_13', 'GTIN_14', 'UPC_A', 'EAN_13'])

export function groupIdentifiers(list) {
  const groups = []
  const byKey = new Map()
  list.forEach((i) => {
    const type = String(i.type || '').toUpperCase()
    const value = String(i.value).trim()
    const family = GTIN_FAMILY.has(type) && /^\d+$/.test(value)
    // Non-barcodes (MPN, model numbers) are compared as sent — "NJ110GR" and
    // "nj110gr-1" are different parts, and trimming either would merge them.
    const digits = family ? value.replace(/^0+/, '') || '0' : value
    const key = family ? `gtin:${digits}` : `${type}:${value}`
    const found = byKey.get(key)
    if (found) {
      if (!found.types.includes(type)) found.types.push(type)
      return
    }
    const group = { value: digits, types: [type] }
    byKey.set(key, group)
    groups.push(group)
  })
  return groups
}

export function ItemIdentifiers({ identifiers, exclude = ['ASIN'] }) {
  const [open, setOpen] = useState(false)
  const parsed = parseIdentifiers(identifiers).filter((i) => i?.value && !exclude.includes(i.type))
  const all = groupIdentifiers(parsed)
  if (!all.length) return null

  const shown = open ? all : all.slice(0, IDENTIFIER_LIMIT)
  const hidden = all.length - shown.length

  // With dozens, how many of each type is the more useful summary — it says
  // "this is a widely distributed product", which the first five never do.
  const byType = all.reduce((acc, g) => ({ ...acc, [g.types[0]]: (acc[g.types[0]] || 0) + 1 }), {})
  const manyTypes = Object.entries(byType).filter(([, n]) => n > IDENTIFIER_LIMIT)

  return (
    <div className="text-muted small mt-1">
      <div className="font-monospace"
        style={{
          wordBreak: 'break-all',
          ...(open ? { maxHeight: 140, overflowY: 'auto' } : {}),
        }}>
        {/* "UPC/EAN/GTIN 622356535588" — every registry the code came in,
            against the one number they all mean. */}
        {shown.map((g) => `${g.types.join('/')} ${g.value}`).join(' · ')}
      </div>

      {(hidden > 0 || open) && (
        <button type="button"
          className="btn btn-link btn-sm p-0 text-decoration-none"
          style={{ fontSize: 'inherit' }}
          onClick={() => setOpen((v) => !v)}>
          {open
            ? 'show fewer'
            : `+${hidden} more${manyTypes.length
              ? ` (${manyTypes.map(([t, n]) => `${n} ${t}`).join(', ')})`
              : ''}`}
        </button>
      )}
    </div>
  )
}

// Landed price, honest about what it does not include.
//
// Landed price is item + delivery. When the marketplace will not say what
// delivery costs, the figure we hold is the item price wearing a landed-price
// label — a FLOOR, not a total. Printing it as "$74.99" states a total we do
// not have, and the whole page compares against it: market position, rank,
// margin, recommendations.
//
// 192 of 200 listings are currently in this state, so the "+" is not an edge
// case being pedantically flagged — it is the normal reading.
export function LandedPrice({ listing: r }) {
  const value = r.landedPrice ?? r.lastPrice
  if (value == null) return <span className="text-muted">—</span>

  const status = r.valueStatus?.landedPrice ?? r.valueStatus?.lastPrice
  const deliveryUnknown = r.valueStatus?.shipping === 'UNAVAILABLE'
  // A floor only when delivery is genuinely missing. An ESTIMATED landed price
  // with a known shipping figure is an estimate of a complete total, which is
  // a different and weaker caveat already carried by the flag.
  const isFloor = deliveryUnknown && status === 'ESTIMATED'

  return (
    <>
      <span className="fw-semibold">
        {formatCurrency(value, r.currency || 'USD')}
        {isFloor && <span title="At least this much — delivery is not included">+</span>}
      </span>
      {!isFloor && <ValueFlag status={status} />}
      {isFloor ? (
        <div className="text-muted" style={{ fontSize: 11 }}>delivery not known</div>
      ) : r.shipping > 0 && r.landedPrice != null && (
        <div className="text-muted" style={{ fontSize: 11 }}>
          incl. {formatCurrency(r.shipping, r.currency || 'USD')} shipping
        </div>
      )}
    </>
  )
}
