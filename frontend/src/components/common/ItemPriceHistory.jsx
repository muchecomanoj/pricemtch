import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FiBarChart2 } from 'react-icons/fi'
import Card from './Card'
import EmptyState from './EmptyState'
import ChartCard from '../charts/ChartCard'
import { marketplaceItemService } from '../../services/marketplaceItemService'
import { formatCurrency, formatDateTime, formatRelativeTime } from '../../utils/format'

// Recorded prices for one tracked marketplace item.
//
// GET /marketplace-items/{mp}/{itemId}/price-history returns a plain array —
// no page wrapper — and repeats are no longer stored, so consecutive points are
// always genuinely different prices. A flat run therefore means the price held,
// not that the same number was written six times.
//
// Lives here rather than on a page because both the item detail screen and the
// search results render it, and two copies of a chart config drift.
// A tracked item gains a point every time its price moves, so the series only
// ever grows — a busy ASIN swapping Buy Box holders produces dozens a week.
// Without a window the chart eventually plots months into a few hundred pixels
// and stops being readable at exactly the point it holds the most information.
const RANGES = [[7, '7d'], [30, '30d'], [90, '90d']]
const DEFAULT_DAYS = 30

export default function ItemPriceHistory({
  marketplace, itemId, currency = 'USD', title = 'Price history', height = 280, actions,
  // Which storefront's copy to chart. The same ASIN in two countries is two
  // series in two currencies; drawing them as one line was the zig-zag chart.
  storefront,
}) {
  const [days, setDays] = useState(DEFAULT_DAYS)

  // Recomputed only when the window changes, so it does not drift on every
  // render and give the query cache a new key each time.
  const from = useMemo(
    () => new Date(Date.now() - days * 86400000).toISOString(),
    [days],
  )

  const { data, isLoading } = useQuery({
    queryKey: ['mp-item-history', marketplace, itemId, days, storefront],
    queryFn: () => marketplaceItemService.priceHistory(marketplace, itemId, { from, storefront }),
    enabled: !!marketplace && !!itemId,
  })

  const ranges = (
    <div className="btn-group btn-group-sm" role="group" aria-label="How far back to chart">
      {RANGES.map(([d, label]) => (
        <button key={d} type="button" className={`btn ${days === d ? 'btn-primary' : 'btn-light'}`}
          onClick={() => setDays(d)}>{label}</button>
      ))}
    </div>
  )
  // Range last, so it sits at the far right of the header. It is the smaller,
  // more-often-used control of the two — the caller's picker changes WHAT is
  // charted and reads first; this only changes how far back.
  const header = actions
    ? (
      <div className="d-flex flex-wrap align-items-center justify-content-end gap-2">
        {actions}{ranges}
      </div>
    )
    : ranges

  const rows = data || []

  // An empty chart has three quite different causes and they need different
  // answers: never tracked, tracked but only seen once, or tracked for a while
  // with nothing inside this window. Only the item record can tell them apart,
  // so it is fetched exactly when the chart has nothing to draw.
  const { data: item, isLoading: itemLoading } = useQuery({
    queryKey: ['mp-item', marketplace, itemId, storefront],
    queryFn: () => marketplaceItemService.detail(marketplace, itemId, { storefront }).catch(() => null),
    enabled: !isLoading && rows.length === 0 && !!marketplace && !!itemId,
  })

  const chart = rows.length ? {
    labels: rows.map((r) => formatDateTime(r.observedAt)),
    datasets: [{
      label: 'Landed price',
      data: rows.map((r) => r.landedPrice ?? r.itemPrice),
      borderColor: '#3fa9f5',
      backgroundColor: 'rgba(63,169,245,0.15)',
      // A price sits at a value and jumps to the next; it does not glide
      // between them. A smooth curve would draw prices that never existed.
      stepped: 'after',
      tension: 0,
      pointRadius: 3,
    }],
  } : null

  // A first sighting has one reading and nothing to plot a line between. That
  // is the normal state of a newly found listing, not a failure.
  if (!isLoading && rows.length === 0) {
    const seen = item?.observationCount ?? 0
    const empty = itemLoading
      ? { title: 'Checking…', message: 'Looking up what has been recorded for this listing.' }
      : !item
        ? {
          title: 'Not tracked yet',
          message: `Tracking begins the first time a search confirms this listing as a real
                    product. Nothing has been recorded for it, so there is nothing to chart.`,
        }
        : seen <= 1
          ? {
            title: 'Only seen once',
            message: `First seen ${formatRelativeTime(item.firstSeenAt)}. A price line needs two
                      readings — search this product again and the second one starts the chart.`,
          }
          : {
            title: 'Nothing in this window',
            message: `Checked ${seen} times, but no price moved in the last ${days} days.
                      Try a wider window.`,
          }

    return (
      <Card title={title} actions={header} className="h-100">
        <EmptyState icon={FiBarChart2} title={empty.title} message={empty.message} />
      </Card>
    )
  }

  return (
    <ChartCard title={title} subtitle="Every recorded change — flat means the price held"
      type="line" data={chart} loading={isLoading} height={height} actions={header}
      options={{
        // One series, so a legend only repeats the subtitle. Money on both the
        // axis and the tooltip — a bare 99 beside a bare 143 loses the currency
        // the whole page is denominated in.
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: (c) => formatCurrency(c.parsed.y, currency) } },
        },
        scales: {
          // Every point carries a full date and time, which at eighteen points
          // becomes an unreadable diagonal wall. Thinned on the axis only —
          // the tooltip still gives the exact moment for the point hovered.
          x: { ticks: { autoSkip: true, maxTicksLimit: 7, maxRotation: 0 } },
          y: { ticks: { callback: (v) => formatCurrency(v, currency) } },
        },
      }}
      footer={rows.length === 1
        ? `One reading in the last ${days} days. A second is needed before a line means anything.`
        : `${rows.length} distinct prices in the last ${days} days.`} />
  )
}
