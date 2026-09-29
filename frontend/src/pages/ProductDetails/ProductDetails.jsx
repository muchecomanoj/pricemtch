import { useEffect, useState } from 'react'
import { useParams, Link, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  FiEdit2, FiTrendingUp, FiPackage, FiTag,
  FiArrowUp, FiArrowDown, FiSearch, FiActivity, FiDollarSign, FiRefreshCw, FiRadio,
  FiAward, FiAlertTriangle,
} from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import { shortItemId } from '../../utils/marketplaces'
import Card from '../../components/common/Card'
import Modal from '../../components/common/Modal'
import StatusBadge from '../../components/common/StatusBadge'
import Loader from '../../components/common/Loader'
import Button from '../../components/common/Button'
import ProductForm from '../../components/forms/ProductForm'
import { productService } from '../../services/productService'
import { productIntelService } from '../../services/productIntelService'
import { monitorService, intervalLabel } from '../../services/monitorService'
import { useNotification } from '../../context/NotificationContext'
import { useAuth } from '../../context/AuthContext'
import { money, pctOf, Kpi, rankOf, gapLabel, rankIsProvisional, RANK_CAVEAT } from './shared'
import CompetitorsTab from './CompetitorsTab'
import PriceHistoryTab from './PriceHistoryTab'
import CostsProfitTab from './CostsProfitTab'
import SalesTab from './SalesTab'
import RecommendationsTab from './RecommendationsTab'
import TrackPriceModal from './TrackPriceModal'
import FindCompetitorsModal from './FindCompetitorsModal'
import MonitorModal from '../Monitors/MonitorModal'
import { useDestination, destinationPayload } from '../../hooks/useDestination'

const TABS = ['Overview', 'Competitors', 'Price History', 'Costs & Profit', 'Sales', 'Recommendations']
const isRealImg = (url) => url && !/placehold/i.test(url)

// Every figure on this page comes from a live endpoint. Load order and
// dependencies follow the backend's documented flow: a search job must run
// before candidates / market-prices / sales return anything.
// The marketplace's own id for the product, in the slot where it belongs.
//
// An eBay-only product has an item number and no ASIN — eBay has no ASINs —
// so a fixed "ASIN: —" read as a missing identifier on a product that has
// one. The item number takes that slot instead; a product known to both
// marketplaces shows both. Shortened for display, since eBay's composite form
// (v1|365412789012|0) is noise around the number people actually use.
function marketplaceIds(p) {
  const ebay = p?.ebayItemId ? shortItemId(p.ebayItemId) : null
  if (ebay && !p?.asin) return [['eBay item', ebay]]
  if (ebay) return [['ASIN', p.asin], ['eBay item', ebay]]
  return [['ASIN', p?.asin]]
}

export default function ProductDetails() {
  const { id } = useParams()
  const qc = useQueryClient()
  const { notify } = useNotification()
  const { can } = useAuth()
  const canManage = can('MANAGE_PRODUCTS')      // search + match review
  const canManagePricing = can('MANAGE_PRICING') // recommendations + alerts
  const canManageCosts = can('MANAGE_COSTS')
  // Set once on the search screen and remembered — a landed price is only
  // comparable to another when both were figured to the same destination.
  const { destination } = useDestination()

  const [tab, setTab] = useState('Overview')
  const [range, setRange] = useState('30')
  const [imgIdx, setImgIdx] = useState(0)
  const [listingId, setListingId] = useState(null)
  const [tracking, setTracking] = useState(false)
  const [editing, setEditing] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [monitorOpen, setMonitorOpen] = useState(false)

  const product = useQuery({ queryKey: ['product', id], queryFn: () => productService.get(id) })

  // Arriving with { edit: true } — "Add more details" after adding a product
  // from search — opens the full form straight away, once the product has
  // loaded so the form is filled. The flag is then cleared from history, or a
  // reload of this page would keep reopening the editor.
  const location = useLocation()
  const navigate = useNavigate()
  useEffect(() => {
    if (!location.state?.edit || !product.data) return
    setEditing(true)
    navigate(location.pathname, { replace: true, state: null })
  }, [location.state, location.pathname, product.data, navigate])
  const candidates = useQuery({ queryKey: ['candidates', id], queryFn: () => productIntelService.candidates(id) })
  const marketPrices = useQuery({ queryKey: ['marketPrices', id], queryFn: () => productIntelService.marketPrices(id) })
  const costProfile = useQuery({ queryKey: ['costProfile', id], queryFn: () => productIntelService.costProfile(id) })
  const sales = useQuery({ queryKey: ['sales', id], queryFn: () => productIntelService.sales(id) })
  const recommendations = useQuery({ queryKey: ['recommendations', id], queryFn: () => productIntelService.recommendations(id) })

  const mp = marketPrices.data || {}
  const ccy = mp.currency || 'USD'
  const comps = candidates.data || []
  // Same call that already feeds Lowest / Avg / Highest — no extra request.
  const rank = rankOf(mp)
  const ourPrice = product.data?.ourPrice ?? null

  // Called with NO price param so the backend uses the product's own ourPrice —
  // this is the real margin, not a scenario. Without an ourPrice the API 400s,
  // so skip the call entirely and prompt for one instead.
  const profitability = useQuery({
    queryKey: ['profitability', id, 'ourPrice', ourPrice],
    queryFn: () => productIntelService.profitability(id, null),
    enabled: ourPrice != null,
  })

  // PUT /products/{id} — same form the Products list uses, so ourPrice and the
  // identifier/attribute reshaping stay in one place (toProductRequest).
  const save = useMutation({
    mutationFn: (values) => productService.update(id, values),
    onSuccess: () => {
      notify.success('Product saved')
      setEditing(false)
      qc.invalidateQueries({ queryKey: ['product', id] })
      qc.invalidateQueries({ queryKey: ['products'] })
      qc.invalidateQueries({ queryKey: ['profitability', id] })
    },
    onError: (err) => notify.error(err?.message || 'Could not save the product.'),
  })

  // One mutation drives both actions on the same endpoint; `refresh` decides
  // whether the backend serves cached research or calls the marketplaces live.
  // Options come from the modal: which marketplaces, and how many results each.
  const search = useMutation({
    mutationFn: (opts) => productIntelService.startSearch(id, {
      ...opts,
      // The saved delivery destination, so a landed price found here is
      // comparable with one found on the search screen.
      destination: destinationPayload(destination),
    }),
    onSuccess: (job, opts) => {
      setSearchOpen(false)
      const n = job?.resultCount
      const plural = n === 1 ? '' : 's'
      // resultCount 0 is a valid outcome, not a failure — nothing matched.
      if (n === 0) notify.info(opts?.refresh ? 'No listings were updated.' : 'No listings found.')
      else if (n != null) {
        notify.success(opts?.refresh
          ? `Prices refreshed — ${n} listing${plural} updated.`
          : `Search finished — ${n} listing${plural} found.`)
      } else notify.success('Search started.')
      // `note` explains cache adoption ("3 of 12 served from cache"). Nullable.
      if (job?.note) notify.info(job.note)

      // marketPrices is NOT optional here: the backend also adopts shared
      // research on that endpoint, so refreshing only the candidates table
      // leaves the KPI cards above it showing the previous figures.
      qc.invalidateQueries({ queryKey: ['candidates', id] })
      qc.invalidateQueries({ queryKey: ['marketPrices', id] })
      qc.invalidateQueries({ queryKey: ['sales', id] })
      qc.invalidateQueries({ queryKey: ['matchReview'] })
    },
    onError: (err) => notify.error(err?.message || 'Search failed.'),
  })

  // Which of the two actions is in flight — they share one mutation, so the
  // spinner has to follow the variables rather than isPending alone.
  const refreshing = search.isPending && search.variables?.refresh === true
  const searching = search.isPending && !refreshing

  // At most one monitor per product; the list endpoint filters by productId.
  const monitors = useQuery({
    queryKey: ['monitors', { productId: id }],
    queryFn: () => monitorService.list({ productId: id }),
    enabled: canManage,
  })
  const monitor = monitors.data?.[0] ?? null

  const saveMonitor = useMutation({
    mutationFn: (values) => (monitor
      ? monitorService.update(monitor.id, values)
      : monitorService.create(values)),
    onSuccess: () => {
      notify.success(monitor ? 'Monitor updated' : 'Monitoring started')
      setMonitorOpen(false)
      qc.invalidateQueries({ queryKey: ['monitors'] })
    },
    onError: (err) => notify.error(err?.message || 'Could not save the monitor.'),
  })

  if (product.isLoading) return <Loader />
  const p = product.data
  const images = (p?.images || []).filter((im) => isRealImg(im.url || im.imageUrl))
  const selectedListing = listingId ?? comps[0]?.id ?? null

  const openHistory = (c) => { setListingId(c.id); setTab('Price History') }

  return (
    <>
      <PageHeader breadcrumb={[
        { label: 'Home', to: '/dashboard' }, { label: 'Products', to: '/products' }, { label: 'Details' },
      ]} />

      {/* ── Hero ─────────────────────────────────────────── */}
      <Card className="mb-3">
        <div className="d-flex flex-wrap align-items-center gap-3">
          <ProductThumb image={p?.image} title={p?.title} size={64} />
          <div style={{ minWidth: 0 }}>
            <div className="d-flex align-items-center gap-2 flex-wrap">
              <h3 className="fw-bold mb-0" style={{ fontFamily: 'Plus Jakarta Sans' }}>{p?.title || '—'}</h3>
              {p?.status && <StatusBadge status={p.status} />}
            </div>
            <div className="text-muted small mt-1 d-flex flex-wrap gap-3">
              <span><FiTag size={12} className="me-1" />{p?.brand || '—'}</span>
              <span>SKU: <span className="font-monospace">{p?.sku || '—'}</span></span>
              <span>{p?.category || '—'}</span>
            </div>
          </div>
          <div className="ms-auto d-flex flex-wrap gap-2">
            {canManage && (
              <Button write variant="light" icon={FiSearch} loading={searching} disabled={search.isPending}
                onClick={() => setSearchOpen(true)}>Find competitors</Button>
            )}
            {/* Live re-pull of the listings we already hold. Distinct from
                "Find competitors", which is cached, free and near-instant —
                this one spends marketplace quota and can take up to a minute,
                so it is only offered once there is something to refresh. */}
            {canManage && comps.length > 0 && (
              <Button write variant="light" icon={FiRefreshCw} loading={refreshing} disabled={search.isPending}
                title="Fetch live prices from the marketplaces (uses API quota)"
                onClick={() => search.mutate({ refresh: true })}>Refresh prices</Button>
            )}
            {/* One monitor per product, so this is either "start" or "edit" —
                never a second one, which the backend would reject with a 409. */}
            {canManage && (
              <Button write variant={monitor ? 'light' : 'light'} icon={FiRadio}
                title={monitor
                  ? `Checked ${intervalLabel(monitor.intervalMinutes).toLowerCase()}`
                  : 'Check this product automatically on a schedule'}
                onClick={() => setMonitorOpen(true)}>
                {monitor ? 'Monitoring' : 'Monitor'}
              </Button>
            )}
            <Button write variant="light" icon={FiTrendingUp} onClick={() => setTracking(true)}>Track price</Button>
            {canManage && <Button write icon={FiEdit2} onClick={() => setEditing(true)}>Edit product</Button>}
          </div>
        </div>
      </Card>

      {/* Statistics are now computed in ONE currency. When a listing's currency
          cannot be converted it is left out — so the figures below describe part
          of the market, not all of it, and saying so is the entire point of the
          change. Silence here would leave an authoritative-looking median that
          quietly ignored a third of the competitors. */}
      {mp.excludedForCurrency > 0 && (
        <div className="field-note field-note--warn mb-3 align-items-center" style={{ maxWidth: 'none' }}>
          <FiAlertTriangle size={15} />
          <span className="flex-grow-1">
            <strong>
              {mp.excludedForCurrency} listing{mp.excludedForCurrency === 1 ? '' : 's'} not included
            </strong>
            {' — no exchange rate for '}
            {mp.missingRatesFor?.length ? mp.missingRatesFor.join(', ') : 'their currency'}.
            The figures below cover the rest of the market.
          </span>
          {/* Straight to the tab, not to Settings' front page — the label
              promises one action and should land on it. */}
          <Link className="btn btn-sm btn-light flex-shrink-0"
            to="/settings?tab=Exchange%20Rates">Add a rate</Link>
        </div>
      )}

      {/* Quieter: nothing is missing, but some numbers are not the ones the
          marketplace printed. */}
      {mp.converted && !mp.excludedForCurrency && (
        <div className="text-muted small mb-3">
          Some figures were converted into {mp.currency || ccy} using your stored exchange rates.
        </div>
      )}

      {/* ── KPI row — product.ourPrice + /market-prices + /profitability ── */}
      <div className="row g-3 mb-4">
        <Kpi icon={FiDollarSign} grad="primary" label="Current price"
          value={money(ourPrice, ccy)} hint={ourPrice == null ? 'set on the product' : null} />
        {/* Competitor figures are landed prices — marketplace price plus
            shipping — so each says so rather than being read as the sticker. */}
        <Kpi icon={FiArrowDown} grad="success" label="Lowest competitor" value={money(mp.lowest, ccy)}
          hint="landed, incl. shipping" />
        <Kpi icon={FiActivity} grad="info" label="Avg market" value={money(mp.average, ccy)}
          hint="landed, incl. shipping" />
        <Kpi icon={FiArrowUp} grad="warning" label="Highest competitor" value={money(mp.highest, ccy)}
          hint="landed, incl. shipping" />
        <Kpi icon={FiAward} grad={rank.known && rank.rank === 1 ? 'success' : 'purple'}
          label="Market rank"
          value={rank.known ? rank.label : '—'}
          flag={rank.known && rankIsProvisional(rank.basis) && (
            <span className="mr-pill mr-pill--orange" title={RANK_CAVEAT}>
              <FiAlertTriangle size={11} /> Provisional
            </span>
          )}
          hint={rank.known
            ? gapLabel(rank.gap, ccy)
            : ourPrice == null ? 'set a price to see your rank' : 'no priced competitors yet'} />
        <Kpi icon={FiTrendingUp} grad={profitability.data?.contributionMarginPct >= 0 ? 'success' : 'danger'}
          label="Margin"
          value={profitability.data?.costProfileConfigured ? pctOf(profitability.data.contributionMarginPct) : '—'}
          hint={ourPrice == null ? 'set a price' : profitability.data?.costProfileConfigured ? `at ${money(ourPrice, ccy)}` : 'add costs'} />
      </div>

      {/* ── Tabs ──────────────────────────────────────────── */}
      <div className="d-flex flex-wrap gap-1 mb-3">
        {TABS.map((t) => (
          <button key={t} className={`btn btn-sm ${tab === t ? 'btn-primary' : 'btn-light'}`} onClick={() => setTab(t)}>{t}</button>
        ))}
      </div>

      {tab === 'Overview' && (
        <div className="row g-3">
          <div className="col-12 col-lg-5">
            <Card title="Images">
              <Gallery images={images} title={p?.title} imgIdx={imgIdx} setImgIdx={setImgIdx} />
            </Card>
          </div>
          <div className="col-12 col-lg-7">
            <Card title="General information & identifiers">
              <div className="row g-3">
                {[
                  ['Title', p?.title], ['Brand', p?.brand], ['Category', p?.category],
                  ['SKU', p?.sku], ...marketplaceIds(p), ['GTIN', p?.gtin],
                  ['EAN', p?.ean], ['UPC', p?.upc], ['MPN', p?.mpn],
                  ['Status', p?.status], ['Pack qty', p?.packQuantity],
                  ['Weight', p?.weight != null ? `${p.weight} kg` : null],
                ].map(([k, v]) => (
                  <div className="col-6 col-lg-4" key={k}>
                    <div className="text-muted small">{k}</div>
                    <div className="fw-semibold text-truncate">{v || '—'}</div>
                  </div>
                ))}
              </div>
              {(p?.length || p?.width || p?.height) && (
                <>
                  <hr />
                  <div className="text-muted small">Dimensions (cm)</div>
                  <div className="fw-semibold">{p.length || '—'} × {p.width || '—'} × {p.height || '—'}</div>
                </>
              )}
              {p?.description && (
                <>
                  <hr />
                  <div className="text-muted small mb-1">Description</div>
                  <div className="small">{p.description}</div>
                </>
              )}
              <hr />
              <div className="text-muted small mb-2">Marketplace availability</div>
              <div className="d-flex flex-wrap gap-2">
                {(p?.marketplaces || []).length
                  ? p.marketplaces.map((m) => <span key={m} className="badge rounded-pill text-bg-light border">{m}</span>)
                  : <span className="text-muted small">Not listed on any marketplace yet.</span>}
              </div>
            </Card>
          </div>
        </div>
      )}

      {tab === 'Competitors' && (
        <CompetitorsTab productId={id} candidates={comps} loading={candidates.isLoading}
          canManage={canManage} onSearch={() => setSearchOpen(true)} searching={searching}
          onSelectListing={openHistory} />
      )}

      {tab === 'Price History' && (
        <PriceHistoryTab candidates={comps} selectedId={selectedListing} onSelect={setListingId}
          range={range} onRange={setRange} canManage={canManage}
          onSearch={() => setSearchOpen(true)} searching={searching} />
      )}

      {tab === 'Costs & Profit' && (
        <CostsProfitTab productId={id} costProfile={costProfile.data} loadingCost={costProfile.isLoading}
          canManageCosts={canManageCosts} defaultPrice={ourPrice ?? mp.median ?? null} />
      )}

      {tab === 'Sales' && <SalesTab sales={sales.data} loading={sales.isLoading} />}

      {tab === 'Recommendations' && (
        <RecommendationsTab productId={id} recommendations={recommendations.data}
          loading={recommendations.isLoading} canManagePricing={canManagePricing}
          costConfigured={costProfile.data?.configured}
          costSource={costProfile.data?.costSource} />
      )}

      {/* Matches the Add Product modal on the Products page — same form. */}
      <Modal show={editing} title="Edit Product" onClose={() => setEditing(false)} size="xl" scrollable>
        {editing && (
          <ProductForm defaultValues={p} submitting={save.isPending}
            onSubmit={(v) => save.mutate({ ...p, ...v })} onCancel={() => setEditing(false)} />
        )}
      </Modal>

      <FindCompetitorsModal show={searchOpen} onClose={() => setSearchOpen(false)}
        busy={search.isPending} onSearch={(opts) => search.mutate(opts)} />

      <MonitorModal show={monitorOpen} onClose={() => setMonitorOpen(false)}
        onSave={saveMonitor.mutate} saving={saveMonitor.isPending}
        monitor={monitor} lockProduct productId={Number(id)} />

      <TrackPriceModal show={tracking} onClose={() => setTracking(false)}
        productId={id} canManage={canManagePricing} />
    </>
  )
}

function ProductThumb({ image, title, size = 44 }) {
  if (isRealImg(image)) return <img src={image} alt={title} width={size} height={size} className="rounded-3" style={{ objectFit: 'cover' }} />
  return (
    <div className="d-grid rounded-3 text-white flex-shrink-0" style={{ width: size, height: size, placeItems: 'center', background: 'var(--grad-primary)' }}>
      <FiPackage size={size * 0.4} />
    </div>
  )
}

function Gallery({ images, title, imgIdx, setImgIdx }) {
  if (!images.length) {
    return (
      <div className="d-grid rounded-3 text-muted" style={{ height: 240, placeItems: 'center', background: 'var(--app-bg)', border: '1px dashed var(--border-soft)' }}>
        <div className="text-center"><FiPackage size={40} className="mb-2 opacity-50" /><div className="small">No images available</div></div>
      </div>
    )
  }
  const main = images[Math.min(imgIdx, images.length - 1)]
  return (
    <>
      <div className="rounded-3 mb-2 d-grid" style={{ height: 240, placeItems: 'center', background: 'var(--app-bg)', overflow: 'hidden' }}>
        <img src={main.url || main.imageUrl} alt={title} style={{ maxHeight: '100%', maxWidth: '100%', objectFit: 'contain' }} />
      </div>
      {images.length > 1 && (
        <div className="d-flex gap-2 flex-wrap">
          {images.map((im, i) => (
            <button key={i} onClick={() => setImgIdx(i)}
              className="p-0 rounded-2 border" style={{ width: 56, height: 56, overflow: 'hidden', borderColor: i === imgIdx ? 'var(--primary)' : 'var(--border-soft)', borderWidth: i === imgIdx ? 2 : 1 }}>
              <img src={im.url || im.imageUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
            </button>
          ))}
        </div>
      )}
    </>
  )
}
