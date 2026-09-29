import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FiTrendingUp } from 'react-icons/fi'
import Card from '../../components/common/Card'
import ChartCard from '../../components/charts/ChartCard'
import EmptyState from '../../components/common/EmptyState'
import { productIntelService } from '../../services/productIntelService'
import { formatCurrency } from '../../utils/format'

// GET /products/{id}/profitability/trend?from&to  (dates as YYYY-MM-DD)
//
// These are STORED daily snapshots, not the live calculation behind the KPI
// cards — the two come from different endpoints and can legitimately differ if
// costs changed today.
const RANGES = [[7, '7 days'], [30, '30 days'], [90, '90 days']]

const METRICS = [
  { key: 'netProfit', label: 'Net profit', color: '#3fa9f5', money: true },
  { key: 'marginPct', label: 'Margin', color: '#16a34a', money: false },
  { key: 'roiPct', label: 'ROI', color: '#8b5cf6', money: false },
]

// The API wants a plain date, not an ISO datetime.
const isoDate = (d) => d.toISOString().slice(0, 10)

export default function NetProfitTrend({ productId, currency = 'USD' }) {
  const [days, setDays] = useState(30)
  const [metricKey, setMetricKey] = useState('netProfit')
  const metric = METRICS.find((m) => m.key === metricKey)

  const from = isoDate(new Date(Date.now() - days * 86400000))

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['profitTrend', productId, days],
    queryFn: () => productIntelService.profitabilityTrend(productId, { from }),
    enabled: !!productId,
  })

  const points = data ?? []
  const single = points.length === 1

  // One series at a time: money and percentages on a shared axis would be
  // meaningless, and a second axis costs more than it explains here.
  const chartData = points.length ? {
    labels: points.map((p) => p.period),
    datasets: [{
      label: metric.label,
      data: points.map((p) => p[metric.key] ?? null),
      borderColor: metric.color,
      backgroundColor: `${metric.color}22`,
      borderWidth: 2.5,
      tension: 0.35,
      spanGaps: true,
      fill: true,
      pointRadius: single ? 5 : 0,
      pointHoverRadius: 6,
    }],
  } : null

  const fmt = (v, money) => {
    if (v == null) return '—'
    return money ? formatCurrency(v, currency) : `${Number(v).toFixed(2)}%`
  }

  const options = {
    interaction: { mode: 'index', intersect: false },
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (ctx) => `${metric.label}: ${fmt(ctx.parsed.y, metric.money)}`,
          // Every figure for that day, whichever series is on screen.
          afterBody: (items) => {
            const p = points[items[0]?.dataIndex]
            if (!p) return ''
            return [
              `Net profit: ${fmt(p.netProfit, true)}`,
              `Margin: ${fmt(p.marginPct, false)}`,
              `ROI: ${fmt(p.roiPct, false)}`,
              `Price: ${fmt(p.sellingPrice, true)}`,
            ]
          },
        },
      },
    },
  }

  const controls = (
    <div className="d-flex flex-wrap gap-2">
      <div className="btn-group btn-group-sm">
        {METRICS.map((m) => (
          <button key={m.key} type="button" className={`btn ${metricKey === m.key ? 'btn-primary' : 'btn-light'}`}
            onClick={() => setMetricKey(m.key)}>{m.label}</button>
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
      <Card title="Net profit trend" className="h-100">
        <div className="alert alert-danger py-2 small mb-0">
          {error?.message || 'Could not load the profitability trend.'}
        </div>
      </Card>
    )
  }

  if (!isLoading && points.length === 0) {
    return (
      <Card title="Net profit trend" actions={controls} className="h-100">
        <EmptyState icon={FiTrendingUp} title="No history yet"
          message="Profitability is captured daily once the product has a selling price and costs. Set both to start tracking." />
      </Card>
    )
  }

  // The note goes in the card's footer, not after it — a sibling note would
  // shorten the card and break alignment with the calculation panel beside it.
  return (
    <ChartCard
      title="Net profit trend"
      subtitle="Captured daily from the product's price and costs"
      type="line"
      data={chartData}
      options={options}
      loading={isLoading}
      height={320}
      actions={controls}
      className="h-100"
      footer={single ? 'Only one snapshot so far — a new one is captured each day.' : null}
    />
  )
}
