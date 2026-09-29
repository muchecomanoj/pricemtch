import { useState } from 'react'
import { DashboardProvider, useDashboard } from '../../context/DashboardContext'
import PageHeader from '../../components/common/PageHeader'
import StatsCard from '../../components/cards/StatsCard'
import ChartCard from '../../components/charts/ChartCard'
import Card from '../../components/common/Card'
import StatusBadge from '../../components/common/StatusBadge'
import { SkeletonCard } from '../../components/common/Skeleton'
import { FiDownload } from 'react-icons/fi'
import Button from '../../components/common/Button'
import { ENDPOINTS } from '../../services/apiEndpoints'
import { downloadCsv, blobErrorMessage } from '../../services/downloadService'
import { useNotification } from '../../context/NotificationContext'
import { formatCurrency } from '../../utils/format'

// The backend sends marketplaces as enum names (AMAZON, EBAY).
const MARKETPLACE_LABEL = { AMAZON: 'Amazon', EBAY: 'eBay', WALMART: 'Walmart' }
const prettyMarketplaces = (chart) => (chart
  ? { ...chart, labels: (chart.labels || []).map((l) => MARKETPLACE_LABEL[l] || l) }
  : chart)

// Each activity list is empty on day one, and an empty card with no words in
// it reads as something broken rather than as nothing having happened yet.
function ActivityList({ items, empty, children }) {
  if (!items?.length) return <div className="text-muted small py-2">{empty}</div>
  return <ul className="list-unstyled mb-0">{items.map(children)}</ul>
}

function DashboardInner() {
  const { summary, charts, activities } = useDashboard()
  const { notify } = useNotification()
  const [exporting, setExporting] = useState(false)
  const c = charts.data

  // The KPI figures, same numbers as the tiles.
  const exportSummary = async () => {
    setExporting(true)
    try {
      await downloadCsv(ENDPOINTS.dashboard.summaryExport, undefined, 'dashboard-summary')
    } catch (err) {
      notify.error(await blobErrorMessage(err, 'Could not download the file.'))
    } finally {
      setExporting(false)
    }
  }

  return (
    <>
      <PageHeader title="Dashboard" breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Dashboard' }]}
        actions={(
          <Button variant="light" icon={FiDownload} loading={exporting}
            disabled={!summary.data?.length} onClick={exportSummary}>
            Export figures
          </Button>
        )} />

      {/* KPI cards */}
      <div className="row g-3 mb-4">
        {summary.isLoading
          ? Array.from({ length: 8 }).map((_, i) => (
              <div className="col-12 col-sm-6 col-xl-3" key={i}><SkeletonCard /></div>
            ))
          : summary.data?.map((item) => (
              <div className="col-12 col-sm-6 col-xl-3" key={item.key}>
                <StatsCard item={item} />
              </div>
            ))}
      </div>

      {/* Charts */}
      <div className="row g-3 mb-4">
        <div className="col-12 col-xl-8">
          <ChartCard title="Price Trend (Ours vs Market)" subtitle="Average price over the last six months"
            type="line" data={c?.priceTrend} loading={charts.isLoading}
            emptyMessage="No price history yet."
            exportUrl={ENDPOINTS.dashboard.chartExport('price-trend')} exportName="price-trend" />
        </div>
        <div className="col-12 col-xl-4">
          <ChartCard title="Category Distribution" type="doughnut" data={c?.categoryDistribution}
            loading={charts.isLoading} emptyMessage="No products yet."
            exportUrl={ENDPOINTS.dashboard.chartExport('category-distribution')}
            exportName="category-distribution" />
        </div>
        <div className="col-12 col-md-6 col-xl-4">
          <ChartCard title="Marketplace Comparison" subtitle="Competitor listings per marketplace"
            type="bar" data={prettyMarketplaces(c?.marketplaceComparison)} loading={charts.isLoading}
            emptyMessage="No competitor listings yet."
            exportUrl={ENDPOINTS.dashboard.chartExport('marketplace-comparison')}
            exportName="marketplace-comparison" />
        </div>
        <div className="col-12 col-md-6 col-xl-4">
          <ChartCard title="Profit Analysis" subtitle="Gross and net profit per unit"
            type="line" data={c?.profitAnalysis} loading={charts.isLoading}
            emptyMessage="Add product costs to see profit."
            exportUrl={ENDPOINTS.dashboard.chartExport('profit-analysis')} exportName="profit-analysis" />
        </div>
        <div className="col-12 col-md-6 col-xl-4">
          <ChartCard title="Top Competitors" subtitle="Listings matched to your products"
            type="bar" data={c?.topCompetitors} loading={charts.isLoading}
            emptyMessage="No competitors matched yet."
            exportUrl={ENDPOINTS.dashboard.chartExport('top-competitors')} exportName="top-competitors" />
        </div>
        <div className="col-12">
          <ChartCard title="Search Activity (7 days)" type="line" data={c?.searchActivity}
            loading={charts.isLoading} height={220} emptyMessage="No searches in the last week."
            exportUrl={ENDPOINTS.dashboard.chartExport('search-activity')} exportName="search-activity" />
        </div>
      </div>

      {/* Activity tables */}
      <div className="row g-3">
        <div className="col-12 col-xl-4">
          <Card title="Recent Searches">
            <ActivityList items={activities.data?.searches} empty="No searches yet.">
              {(s) => (
                <li key={s.id} className="d-flex justify-content-between py-2 border-bottom">
                  <div>
                    <div className="fw-semibold small">{s.query}</div>
                    <div className="text-muted small">{s.type} • {s.results} results</div>
                  </div>
                  <span className="text-muted small flex-shrink-0 ms-2">{s.time}</span>
                </li>
              )}
            </ActivityList>
          </Card>
        </div>
        <div className="col-12 col-xl-4">
          <Card title="Recent Alerts">
            <ActivityList items={activities.data?.alerts} empty="No alerts yet.">
              {(a) => (
                <li key={a.id} className="d-flex justify-content-between py-2 border-bottom">
                  <div>
                    <StatusBadge status={a.severity} label={a.type} />
                    <div className="text-muted small mt-1">{a.product}</div>
                  </div>
                  <span className="text-muted small flex-shrink-0 ms-2">{a.time}</span>
                </li>
              )}
            </ActivityList>
          </Card>
        </div>
        <div className="col-12 col-xl-4">
          <Card title="Recent Recommendations">
            <ActivityList items={activities.data?.recommendations} empty="No suggestions yet.">
              {(r) => (
                // align-items-start, or the badge stretches to the height of a
                // two-line product name and reads as a coloured blob.
                <li key={r.id} className="d-flex justify-content-between align-items-start py-2 border-bottom">
                  <div>
                    <div className="fw-semibold small">{r.product}</div>
                    <div className="text-muted small">{formatCurrency(r.price)} • {Math.round(r.confidence * 100)}% conf.</div>
                  </div>
                  <span className="flex-shrink-0 ms-2"><StatusBadge status={r.risk} /></span>
                </li>
              )}
            </ActivityList>
          </Card>
        </div>
      </div>
    </>
  )
}

export default function Dashboard() {
  return (
    <DashboardProvider>
      <DashboardInner />
    </DashboardProvider>
  )
}
