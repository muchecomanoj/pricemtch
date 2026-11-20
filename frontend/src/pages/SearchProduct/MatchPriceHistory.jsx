import { useMemo, useState } from 'react'
import ItemPriceHistory from '../../components/common/ItemPriceHistory'
import ItemRecentChanges from '../../components/common/ItemRecentChanges'
import { mpLabel } from '../../utils/marketplaces'
import { formatCurrency } from '../../utils/format'

// Price history without leaving the search.
//
// Offers everything the judge MATCHED — from the identifier lookup and from the
// competitor search that follows it. Both are tracked, so both have history,
// and listing only the first left three of four charts unreachable when the
// exact search returned one product and the keyword pass found the rest.
//
// Rejected lookalikes stay out: they are shown in the results but never
// tracked, so every one of those entries would open an empty chart, and a
// picker where most choices lead nowhere teaches people to stop opening it.
export default function MatchPriceHistory({ matches, rivals }) {
  const [sel, setSel] = useState(0)

  // The exact match first, then the competitors, deduplicated — an identifier
  // search and its keyword follow-up routinely return the same listing, and
  // charting it twice under two headings reads as two products.
  const options = useMemo(() => {
    const seen = new Set()
    return [...(matches || []), ...(rivals || [])]
      .filter((m) => {
        const key = `${m.marketplace}:${m.marketplaceItemId}`
        if (!m.marketplaceItemId || seen.has(key)) return false
        seen.add(key)
        return true
      })
  }, [matches, rivals])

  // Options change with each search, so an index held from the last one can
  // point past the end of this one.
  const active = options[sel] || options[0]
  if (!active?.marketplaceItemId) return null

  // Short enough that the browser's own dropdown stays near the width of the
  // box. A native popup is drawn at the width of its longest option and can
  // not be styled, so a 48-character title plus a price spilled across the
  // range buttons and into the next card.
  const label = (m) => {
    const t = m.title || m.marketplaceItemId || ''
    const short = t.length > 42 ? `${t.slice(0, 40).trim()}…` : t
    const where = [mpLabel(m.marketplace), m.storefront].filter(Boolean).join(' ')
    const price = m.price != null ? formatCurrency(m.price, m.currency || 'USD') : null
    return [short, where, price].filter(Boolean).join(' · ')
  }

  return (
    <>
      {/* The picker drives both cards below, so it sits above them, on its
          own row — inside the chart header it fought the range buttons for
          space. With a single listing there is nothing to choose between, so
          it is a line of text rather than a dropdown with one option. */}
      <div className="d-flex flex-wrap align-items-center gap-2 mt-3">
        <span className="text-muted small">
          {options.length > 1 ? `Charting 1 of ${options.length} listings:` : 'Charting:'}
        </span>
        {options.length > 1 ? (
          <select className="form-select form-select-sm w-auto" style={{ maxWidth: '100%' }}
            aria-label="Which listing to chart"
            value={options.indexOf(active)} onChange={(e) => setSel(Number(e.target.value))}>
            {options.map((m, i) => (
              <option key={`${m.marketplace}:${m.marketplaceItemId}`} value={i} title={m.title || ''}>
                {label(m)}
              </option>
            ))}
          </select>
        ) : (
          <span className="small fw-semibold text-truncate" style={{ minWidth: 0 }} title={active.title || ''}>
            {label(active)}
          </span>
        )}
      </div>

      <div className="row g-3 mt-0">
        <div className="col-12 col-lg-7">
          <ItemPriceHistory
            marketplace={active.marketplace}
            itemId={active.marketplaceItemId}
            storefront={active.storefront}
            currency={active.currency || 'USD'}
            title="Price history"
          />
        </div>
        <div className="col-12 col-lg-5">
          <ItemRecentChanges marketplace={active.marketplace} itemId={active.marketplaceItemId}
            storefront={active.storefront} />
        </div>
      </div>
    </>
  )
}
