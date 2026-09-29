import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { FiDollarSign, FiArrowDown, FiArrowUp, FiActivity, FiBarChart2, FiUsers, FiSearch, FiAward } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import MetricCard from '../../components/cards/MetricCard'
import ProductSelector from '../../components/common/ProductSelector'
import EmptyState from '../../components/common/EmptyState'
import { SkeletonCard } from '../../components/common/Skeleton'
import HistoricalPrices from './HistoricalPrices'
import { productService } from '../../services/productService'
import { productIntelService } from '../../services/productIntelService'
import { rankOf, gapLabel, rankIsProvisional } from '../ProductDetails/shared'
import { formatCurrency } from '../../utils/format'

// Current price comes from the product itself (ourPrice); the market figures
// come from GET /products/{id}/market-prices, which only has data once a search
// job has run for that product.
export default function PriceHistory() {
  const [productId, setProductId] = useState(null)

  const product = useQuery({
    queryKey: ['product', productId],
    queryFn: () => productService.get(productId),
    enabled: !!productId,
  })
  const market = useQuery({
    queryKey: ['marketPrices', productId],
    queryFn: () => productIntelService.marketPrices(productId),
    enabled: !!productId,
  })

  const mp = market.data || {}
  const ccy = mp.currency || 'USD'
  const money = (v) => (v == null ? '—' : formatCurrency(v, ccy))
  const loading = product.isLoading || market.isLoading
  const hasMarket = (mp.competitorCount ?? 0) > 0
  // Arrives on the same market-prices call as the figures above.
  const rank = rankOf(mp)

  return (
    <>
      <PageHeader title="Price Intelligence"
        subtitle="Your price against the competitor set for a product."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Price Intelligence' }]}
        actions={<ProductSelector value={productId} onChange={setProductId} />} />

      {!productId ? (
        <Card><EmptyState icon={FiBarChart2} title="Select a product"
          message="Pick a product above to compare your price against the market." /></Card>
      ) : loading ? (
        <div className="row g-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <div className="col-6 col-xl" key={i}><SkeletonCard /></div>
          ))}
        </div>
      ) : (
        <>
          <div className="row g-3 mb-4">
            <div className="col-6 col-xl">
              <MetricCard icon={FiDollarSign} tone="primary" label="Current Price"
                value={money(product.data?.ourPrice)}
                hint={product.data?.ourPrice == null ? 'set on the product' : null} />
            </div>
            <div className="col-6 col-xl"><MetricCard icon={FiArrowDown} tone="success" label="Lowest Price" value={money(mp.lowest)} /></div>
            <div className="col-6 col-xl"><MetricCard icon={FiArrowUp} tone="danger" label="Highest Price" value={money(mp.highest)} /></div>
            <div className="col-6 col-xl"><MetricCard icon={FiBarChart2} tone="info" label="Median Price" value={money(mp.median)} /></div>
            <div className="col-6 col-xl"><MetricCard icon={FiActivity} tone="warning" label="Average Price" value={money(mp.average)} /></div>
            <div className="col-6 col-xl">
              {/* Position, not just price — the one figure the other five
                  cards cannot answer between them. */}
              <MetricCard icon={FiAward} tone={rank.known && rank.rank === 1 ? 'success' : 'purple'}
                label="Market Rank"
                value={rank.known ? rank.label : '—'}
                hint={rank.known
                  ? [gapLabel(rank.gap, ccy), rankIsProvisional(rank.basis) ? '· provisional' : null]
                    .filter(Boolean).join(' ')
                  : product.data?.ourPrice == null ? 'set a price to see your rank' : 'no priced competitors yet'} />
            </div>
          </div>


          {!hasMarket ? (
            <Card>
              <EmptyState icon={FiSearch} title="No competitors found yet"
                message="Run a search on this product to populate the market figures."
                action={(
                  <Link to={`/products/${productId}`} className="btn btn-primary">
                    Open product and find competitors
                  </Link>
                )} />
            </Card>
          ) : (
            <div className="row g-3">
              <div className="col-12 col-lg-6">
                <Card title="Market position" className="h-100"
                  subtitle={`Based on ${mp.competitorCount} competitor listing${mp.competitorCount === 1 ? '' : 's'}`}>
                  <div className="d-flex align-items-center gap-2 mb-3">
                    <FiUsers className="text-muted" />
                    <span className="fw-semibold">{mp.competitorCount}</span>
                    <span className="text-muted small">competitors tracked</span>
                  </div>
                  {product.data?.ourPrice != null && mp.median != null ? (
                    <p className="mb-0 small">
                      Your price is{' '}
                      <strong className={product.data.ourPrice <= mp.median ? 'text-success' : 'text-warning'}>
                        {money(Math.abs(product.data.ourPrice - mp.median))}{' '}
                        {product.data.ourPrice <= mp.median ? 'below' : 'above'}
                      </strong>{' '}
                      the competitor median.
                    </p>
                  ) : (
                    <p className="text-muted small mb-0">
                      Set a selling price on the product to compare it against the market.
                    </p>
                  )}
                </Card>
              </div>
              <div className="col-12 col-lg-6">
                <HistoricalPrices productId={productId} currency={ccy} />
              </div>
            </div>
          )}
        </>
      )}
    </>
  )
}
