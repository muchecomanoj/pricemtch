import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FiActivity, FiChevronDown, FiChevronUp } from 'react-icons/fi'
import Card from './Card'
import Loader from './Loader'
import { ChangeDelta, ChangeWhen, CHANGE_FIELD } from './ChangeEvent'
import { marketplaceItemService } from '../../services/marketplaceItemService'

// Movements recorded on one tracked item, newest first.
//
// Shared by the item detail screen and the search results, so the two cannot
// drift apart on layout or on the collapsing rule below.

// PRICE and LANDED_PRICE are the same number whenever shipping is nil or
// unchanged — which on Amazon is nearly always. Listing both doubles the length
// of the feed and shows one fact twice, so identical siblings collapse to the
// simpler label. They stay separate the moment they genuinely differ, which is
// exactly when the distinction carries information.
const FIELD_RANK = { PRICE: 0, LANDED_PRICE: 1, SHIPPING: 2, AVAILABILITY: 3 }

// Enough to fill the height of the chart it sits beside, and no more. An ASIN
// whose Buy Box changes hands hourly produces a feed several times the height
// of everything around it, which drags the whole row down and leaves the chart
// stranded in white space.
const COLLAPSED = 4
// Expanded, it scrolls rather than growing without limit — otherwise "show all"
// just moves the problem further down the page.
const EXPANDED_MAX_HEIGHT = 320

function collapse(events) {
  const seen = new Map()
  for (const e of events) {
    const key = `${e.observedAt}|${e.oldValue}|${e.newValue}`
    const held = seen.get(key)
    if (!held || (FIELD_RANK[e.field] ?? 9) < (FIELD_RANK[held.field] ?? 9)) seen.set(key, e)
  }
  return [...seen.values()]
}

export default function ItemRecentChanges({
  marketplace, itemId, limit = 10, title = 'Recent changes',
  // Which storefront's copy. Movements on amazon.ca are not movements on
  // amazon.com, and listing both under one heading invented price changes.
  storefront,
}) {
  const [expanded, setExpanded] = useState(false)
  const { data, isLoading } = useQuery({
    queryKey: ['mp-item-changes', marketplace, itemId, limit, storefront],
    queryFn: () => marketplaceItemService.itemChanges(marketplace, itemId, { size: limit, storefront }),
    enabled: !!marketplace && !!itemId,
  })

  const events = collapse(data?.content || [])
  const shown = expanded ? events : events.slice(0, COLLAPSED)
  const hidden = events.length - shown.length

  return (
    <Card className="h-100"
      title={events.length ? `${title} (${events.length})` : title}
      actions={<FiActivity className="text-muted" />}>
      {isLoading ? <Loader label="Loading changes…" />
        : events.length === 0 ? (
          // An empty list is the correct answer for a stable price, not a
          // failure — and it should not be worded like one.
          <div className="text-muted small">No changes recorded yet — the price has held.</div>
        ) : (
          <div className="d-flex flex-column gap-3"
            style={expanded ? { maxHeight: EXPANDED_MAX_HEIGHT, overflowY: 'auto' } : undefined}>
            {shown.map((e, i) => (
              // Stacked, never side by side. A two-column row wraps at a
              // different point for every entry depending on how long the
              // numbers are, so the date jumps above the price on some rows and
              // below it on others — which reads as a rendering fault.
              <div key={e.id} className={i < shown.length - 1 ? 'pb-3 border-bottom-soft' : ''}>
                <div className="d-flex align-items-center flex-wrap gap-2 mb-1">
                  <span className="mr-pill mr-pill--slate">{CHANGE_FIELD[e.field] || e.field}</span>
                  <ChangeDelta event={e} />
                </div>
                <ChangeWhen event={e} />
              </div>
            ))}
          </div>
        )}

      {(hidden > 0 || expanded) && (
        <button type="button"
          className="btn btn-link btn-sm p-0 mt-3 text-decoration-none d-inline-flex align-items-center gap-1"
          onClick={() => setExpanded((v) => !v)}>
          {expanded
            ? <>Show fewer <FiChevronUp size={14} /></>
            : <>Show {hidden} more <FiChevronDown size={14} /></>}
        </button>
      )}
      {/* The feed is capped server-side too, so "all" means the most recent
          batch rather than the item's whole history. Said once, here. */}
      {expanded && events.length >= limit && (
        <div className="text-muted mt-2" style={{ fontSize: 11 }}>
          Showing the {limit} most recent changes.
        </div>
      )}
    </Card>
  )
}
