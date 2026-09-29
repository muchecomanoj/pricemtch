import { useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import Card from '../../components/common/Card'
import ChartCard from '../../components/charts/ChartCard'
import EmptyState from '../../components/common/EmptyState'
import { FiBarChart2, FiActivity } from 'react-icons/fi'
import { productIntelService } from '../../services/productIntelService'
import { ENDPOINTS } from '../../services/apiEndpoints'
import { formatDate } from '../../utils/format'
import { NeedsSearch } from './shared'

// Price history is per LISTING, not per product — GET /listings/{id}/price-history.
const RANGES = [['7', '7 days'], ['30', '30 days'], ['90', '90 days'], ['365', '1 year']]

// Observations are timestamped events, and several can land on the same day —
// a monitor checking hourly produces 24. Labelling those by date alone repeats
// the same string across consecutive points, which reads as a rendering fault
// rather than as several readings taken that day.
//
// So the time is added only when it carries information: when two points share
// a date. A series with one reading per day stays clean.
function axisLabels(rows) {
  const dates = rows.map((r) => formatDate(r.observedAt))
  const needsTime = new Set(dates).size < dates.length
  if (!needsTime) return dates

  return rows.map((r, i) => {
    const dt = new Date(r.observedAt)
    if (Number.isNaN(dt.getTime())) return dates[i]
    const time = dt.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
    // Repeat the date only when the day changes, so a run of readings from one
    // day reads as times under a single date rather than the date eight times.
    return dates[i] === dates[i - 1] ? time : `${dates[i]} ${time}`
  })
}

// The range buttons drive real from/to query params (ISO datetimes).
function rangeToParams(days) {
  const to = new Date()
  const from = new Date(to.getTime() - Number(days) * 86400000)
  return { from: from.toISOString(), to: to.toISOString() }
}

export default function PriceHistoryTab({
  candidates, selectedId, onSelect, range, onRange, canManage, onSearch, searching,
}) {
  // Only listings with at least one priced observation can produce a chart.
  // `hasPriceHistory` is pooled across every client tracking the same listing,
  // so it means "a chart exists", not "it is priced right now" — an
  // out-of-stock competitor still has a history worth plotting.
  //
  // The picker is filtered, NOT the endpoint: Competitors gets the same array
  // and must keep every row, because an unpriced candidate is still something
  // to accept or reject.
  const chartable = useMemo(
    () => (candidates || []).filter((c) => c.hasPriceHistory), [candidates])

  // The parent defaults the selection to candidates[0], which may be a row
  // that isn't in this picker — leaving the chart blank with a listing named
  // above it. Move to the first chartable row instead.
  const inPicker = chartable.some((c) => c.id === selectedId)
  useEffect(() => {
    if (!inPicker && chartable.length) onSelect(chartable[0].id)
  }, [inPicker, chartable, onSelect])

  const activeId = inPicker ? selectedId : chartable[0]?.id ?? null

  const params = rangeToParams(range)
  const history = useQuery({
    queryKey: ['priceHistory', activeId, range],
    queryFn: () => productIntelService.priceHistory(activeId, params),
    enabled: !!activeId,
  })

  // No candidates at all is a different problem from candidates that have
  // never been priced — the first needs a search, the second needs patience.
  if (!candidates.length) {
    return <NeedsSearch onSearch={onSearch} canSearch={canManage} busy={searching} what="price history" />
  }

  if (!chartable.length) {
    return (
      <Card>
        <EmptyState
          icon={FiActivity}
          title="No priced history yet"
          message={`${candidates.length} competitor${candidates.length === 1 ? ' is' : 's are'} tracked for this
                    product, but none has had a price recorded yet. History builds up as those listings are
                    re-checked — set up a monitor to collect it automatically.`}
        />
      </Card>
    )
  }

  const rows = history.data || []
  const chart = rows.length
    ? {
      labels: axisLabels(rows),
      'Item price': rows.map((r) => r.itemPrice),
      'Landed price': rows.map((r) => r.landedPrice),
    }
    : null

  return (
    <>
      <div className="d-flex flex-wrap justify-content-between align-items-end gap-2 mb-3">
        <div style={{ minWidth: 260 }}>
          <label className="form-label">Listing</label>
          <select className="form-select" value={activeId || ''} onChange={(e) => onSelect(Number(e.target.value))}>
            {chartable.map((c) => (
              <option key={c.id} value={c.id}>
                {c.marketplace} · {c.seller || 'Unknown seller'} — {c.title?.slice(0, 40) || `#${c.id}`}
              </option>
            ))}
          </select>
          {chartable.length < candidates.length && (
            <div className="form-text">
              Showing {chartable.length} of {candidates.length} competitors — the rest have no recorded prices yet.
            </div>
          )}
        </div>
        <div className="btn-group btn-group-sm">
          {RANGES.map(([v, label]) => (
            <button key={v} className={`btn ${range === v ? 'btn-primary' : 'btn-light'}`}
              onClick={() => onRange(v)}>{label}</button>
          ))}
        </div>
      </div>

      {rows.length === 0 && !history.isLoading ? (
        <Card>
          <EmptyState icon={FiBarChart2} title="No price snapshots in this range"
            message="Snapshots are recorded as the listing is refreshed. Try a wider range." />
        </Card>
      ) : (
        <ChartCard title="Price over time" subtitle="Item price vs landed price for the selected listing"
          type="line" data={chart} loading={history.isLoading} height={320}
          // The CSV is the listing and range on screen, not everything ever
          // recorded — an export that ignores the filters is a different answer
          // from the chart above it.
          exportUrl={activeId ? ENDPOINTS.priceHistoryExport.listing(activeId) : undefined}
          exportParams={rangeToParams(range)}
          exportName={`price-history-${activeId}`} />
      )}
    </>
  )
}
