import Card from '../common/Card'

// KPI tile for pages backed by real figures.
//
// Deliberately NOT StatsCard: that one renders a `pseudoSeries()` sparkline
// derived from the value itself, which draws a trend line where no time series
// exists. Fine for the mock dashboard, misleading next to live numbers.
export default function MetricCard({ icon: Icon, label, value, tone = 'primary', hint }) {
  return (
    <Card hover className="h-100">
      {Icon && <div className={`icon-box icon-grad-${tone} mb-3`}><Icon /></div>}
      <div className="kpi-value">{value ?? '—'}</div>
      <div className="text-muted small fw-medium mt-1">{label}</div>
      {hint && <div className="text-muted mt-1" style={{ fontSize: 11 }}>{hint}</div>}
    </Card>
  )
}
