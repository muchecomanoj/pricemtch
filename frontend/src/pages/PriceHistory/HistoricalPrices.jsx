import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FiTrendingUp } from 'react-icons/fi'
import Card from '../../components/common/Card'
import ChartCard from '../../components/charts/ChartCard'
import EmptyState from '../../components/common/EmptyState'
import { productIntelService } from '../../services/productIntelService'
import { formatCurrency } from '../../utils/format'

// GET /products/{id}/price-history-rollup?bucket=day|week&from&to
//
// One point per bucket, but points only exist for days a search actually ran —
// so a product searched once returns a single point. That is expected, and is
// rendered as a dot rather than an invisible zero-length line.
const RANGES = [[30, '30 days'], [90, '90 days'], [365, '1 year']]
const BUCKETS = [['day', 'Day'], ['week', 'Week'], ['month', 'Month']]

// FR-PRICE-003's "volatility". Scatter as a percentage of the average, so it
// compares across products — 22% means the same on a $400 monitor as on a $4
// cable, which a currency spread never does.
//
// null is a bucket with a single price: nothing was measured. Rendered as a
// dash, never 0%, because zero would claim a stability that was never observed.
const VOLATILITY = [
  { max: 5, label: 'steady', cls: 'text-success' },
  { max: 15, label: 'normal', cls: 'text-muted' },
  { max: Infinity, label: 'volatile', cls: 'text-danger fw-semibold' },
]
const volatilityOf = (pct) => (pct == null ? null : VOLATILITY.find((v) => pct < v.max))

const SERIES = [
  { key: 'ourPrice', label: 'Our price', color: '#3fa9f5', dash: [6, 4], width: 2.5 },
  { key: 'lowest', label: 'Lowest', color: '#16a34a' },
  { key: 'highest', label: 'Highest', color: '#dc2626' },
  { key: 'median', label: 'Median', color: '#94a3b8' },
]

export default function HistoricalPrices({ productId, currency = 'USD' }) {
  const [days, setDays] = useState(90)
  const [bucket, setBucket] = useState('day')

  const from = new Date(Date.now() - days * 86400000).toISOString()

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['priceRollup', productId, bucket, days],
    queryFn: () => productIntelService.priceHistoryRollup(productId, { bucket, from }),
    enabled: !!productId,
  })

  const points = data ?? []
  const single = points.length === 1
  // The most recent bucket, summarised under the chart. The trend line answers
  // "which way"; this answers "how far apart are they right now", which is the
  // question a pricing decision actually turns on.
  const latest = points[points.length - 1]
  const vol = volatilityOf(latest?.volatilityPct)

  // Passed as a ready chart.js dataset so each series keeps its assigned colour
  // — ChartCard's default palette assigns by index, which would put "highest"
  // in amber and "median" in red.
  const chartData = points.length ? {
    labels: points.map((p) => p.period),
    datasets: SERIES.map((s) => ({
      label: s.label,
      data: points.map((p) => p[s.key] ?? null),
      borderColor: s.color,
      backgroundColor: `${s.color}22`,
      borderWidth: s.width ?? 2,
      borderDash: s.dash ?? [],
      tension: 0.35,
      spanGaps: true,
      // A lone point needs a visible marker or the chart looks blank.
      pointRadius: single ? 5 : 2,
      pointHoverRadius: 6,
    })),
  } : null

  const options = {
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { position: 'bottom', labels: { boxWidth: 12, usePointStyle: true } },
      tooltip: {
        callbacks: {
          label: (ctx) => `${ctx.dataset.label}: ${ctx.parsed.y == null ? '—' : formatCurrency(ctx.parsed.y, currency)}`,
          // Surface how many competitors backed each point — a median drawn
          // from 2 listings deserves less trust than one drawn from 20.
          afterBody: (items) => {
            const p = points[items[0]?.dataIndex]
            if (!p) return ''
            const lines = []
            if (p.competitorCount != null) {
              lines.push(`${p.competitorCount} competitor${p.competitorCount === 1 ? '' : 's'} that ${bucket}`)
            }
            if (p.priceRange != null) lines.push(`spread ${formatCurrency(p.priceRange, currency)}`)
            // Only where it was actually computed — a single-price bucket has
            // no scatter to report and saying "0%" would invent one.
            if (p.volatilityPct != null) {
              lines.push(`${p.volatilityPct.toFixed(1)}% volatility · ${volatilityOf(p.volatilityPct).label}`)
            }
            return lines
          },
        },
      },
    },
  }

  const controls = (
    <div className="d-flex flex-wrap gap-2">
      <div className="btn-group btn-group-sm">
        {BUCKETS.map(([v, label]) => (
          <button key={v} type="button" className={`btn ${bucket === v ? 'btn-primary' : 'btn-light'}`}
            onClick={() => setBucket(v)}>{label}</button>
        ))}
      </div>
      <div className="btn-group btn-group-sm">
        {RANGES.map(([v, label]) => (
          <button key={v} type="button" className={`btn ${days === v ? 'btn-primary' : 'btn-light'}`}
            onClick={() => setDays(v)}>{label}</button>
        ))}
      </div>
    </div>
  )

  if (isError) {
    return (
      <Card title="Historical prices" className="h-100">
        <div className="alert alert-danger py-2 small mb-0">
          {error?.message || 'Could not load the price history.'}
        </div>
      </Card>
    )
  }

  if (!isLoading && points.length === 0) {
    return (
      <Card title="Historical prices" actions={controls} className="h-100">
        <EmptyState icon={FiTrendingUp} title="No price history yet"
          message="Run Find competitors on this product over several days to build a trend." />
      </Card>
    )
  }

  // The note goes in the card's footer, not after it — a sibling note would
  // shorten the card and break alignment with the panel beside it.
  return (
    <ChartCard
      title="Historical prices"
      subtitle="Your price against the competitor range over time"
      type="line"
      data={chartData}
      options={options}
      loading={isLoading}
      height={320}
      actions={controls}
      className="h-100"
      footer={(
        <>
          {latest && latest.lowest != null && latest.highest != null && (
            <span>
              Latest {bucket}: {formatCurrency(latest.lowest, currency)}
              {' – '}{formatCurrency(latest.highest, currency)}
              {latest.priceRange != null && <> · spread {formatCurrency(latest.priceRange, currency)}</>}
              {' · '}
              {/* A dash, not 0%. One price in a bucket means nothing was
                  measured, and zero would read as "perfectly stable". */}
              {vol
                ? <span className={vol.cls}>{latest.volatilityPct.toFixed(1)}% volatility, {vol.label}</span>
                : <span className="text-muted">volatility not measurable (one price only)</span>}
            </span>
          )}
          {single && (
            <span className="d-block">
              Only one snapshot so far — the trend builds as searches run on different days.
            </span>
          )}
        </>
      )}
    />
  )
}
