import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  FiTrendingUp, FiPercent, FiDollarSign, FiTarget, FiInfo, FiPieChart,
} from 'react-icons/fi'
import NetProfitTrend from './NetProfitTrend'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import MetricCard from '../../components/cards/MetricCard'
import ProductSelector from '../../components/common/ProductSelector'
import EmptyState from '../../components/common/EmptyState'
import { SkeletonCard } from '../../components/common/Skeleton'
import { productIntelService } from '../../services/productIntelService'
import { formatCurrency } from '../../utils/format'

// GET /products/{id}/profitability — calculated from the product's cost profile
// (or the tenant default) and its ourPrice. Everything here is deterministic;
// nothing is estimated.
const pct = (v) => (v == null ? '—' : `${Number(v).toFixed(1)}%`)

export default function Profitability() {
  const [productId, setProductId] = useState(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['profitability', productId, 'page'],
    queryFn: () => productIntelService.profitability(productId, null),
    enabled: !!productId,
  })

  const ccy = data?.currency || 'USD'
  const money = (v) => (v == null ? '—' : formatCurrency(v, ccy))
  const configured = data?.costProfileConfigured

  return (
    <>
      <PageHeader title="Profitability"
        subtitle="Margin, ROI and break-even for a product, calculated from its costs and selling price."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Profitability' }]}
        actions={<ProductSelector value={productId} onChange={setProductId} />} />

      {isError && (
        <div className="alert alert-danger py-2 small">{error?.message || 'Could not load profitability.'}</div>
      )}

      {!productId ? (
        <Card><EmptyState icon={FiPieChart} title="Select a product"
          message="Pick a product above to see its profitability." /></Card>
      ) : isLoading ? (
        <div className="row g-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div className="col-6 col-xl" key={i}><SkeletonCard /></div>
          ))}
        </div>
      ) : !configured ? (
        <Card>
          {/* configured===false now means NEITHER a product profile NOR the
              tenant defaults exist — either one would satisfy it. */}
          <EmptyState icon={FiInfo} title="No costs configured"
            message="Margin, ROI and break-even need costs. Set global defaults once, or give this product its own profile."
            action={(
              <div className="d-flex flex-wrap justify-content-center gap-2">
                <Link to="/costs" className="btn btn-primary">Set global defaults</Link>
                <Link to={`/products/${productId}`} className="btn btn-light">Costs for this product</Link>
              </div>
            )} />
        </Card>
      ) : (
        <>
          <div className="row g-3 mb-4">
            <div className="col-6 col-xl"><MetricCard icon={FiTrendingUp} tone="primary" label="ROI" value={pct(data.roiPct)} /></div>
            <div className="col-6 col-xl"><MetricCard icon={FiPercent} tone="success" label="Margin" value={pct(data.contributionMarginPct)} /></div>
            <div className="col-6 col-xl"><MetricCard icon={FiDollarSign} tone="info" label="Net Profit" value={money(data.netProfitEstimate)} /></div>
            <div className="col-6 col-xl"><MetricCard icon={FiDollarSign} tone="warning" label="Gross Profit" value={money(data.grossProfit)} /></div>
            <div className="col-6 col-xl"><MetricCard icon={FiTarget} tone="danger" label="Break-even" value={money(data.breakEvenPrice)} /></div>
          </div>

          <div className="row g-3">
            <div className="col-12 col-lg-6">
              <Card title="Calculation" subtitle={`At a selling price of ${money(data.sellingPrice)}`}
                className="h-100">
                <Row label="Net revenue" value={money(data.netRevenue)} />
                <Row label="Variable cost" value={money(data.variableCost)} />
                <Row label="Referral fee" value={money(data.referralFee)} />
                <Row label="Payment fee" value={money(data.paymentFee)} />
                <Row label="Returns allowance" value={money(data.returnsAllowance)} />
                <Row label="Tax" value={money(data.tax)} />
                <hr className="my-2" />
                <Row label="Contribution profit" value={money(data.contributionProfit)} strong />
                <Row label="Max acquisition cost" value={money(data.maxAllowableAcquisitionCost)} />
                {data.costSource === 'TENANT_DEFAULT' && (
                  <div className="d-flex align-items-start gap-2 text-muted small mt-3 pt-3 border-top-soft">
                    <FiInfo className="flex-shrink-0 mt-1" />
                    <span>
                      Using the <Link to="/costs">global cost defaults</Link> — this product has no cost
                      profile of its own.
                    </span>
                  </div>
                )}
                {data.formulaVersion && (
                  <div className="text-muted mt-2" style={{ fontSize: 11 }}>Formula {data.formulaVersion}</div>
                )}
              </Card>
            </div>
            <div className="col-12 col-lg-6">
              <NetProfitTrend productId={productId} currency={ccy} />
            </div>
          </div>
        </>
      )}
    </>
  )
}

function Row({ label, value, strong }) {
  return (
    <div className="d-flex align-items-center justify-content-between py-1">
      <span className="text-muted small">{label}</span>
      <span className={strong ? 'fw-bold' : 'fw-semibold'}>{value}</span>
    </div>
  )
}
