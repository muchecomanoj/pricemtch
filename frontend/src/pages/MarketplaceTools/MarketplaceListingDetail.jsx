import { useState, useEffect } from 'react'
import { useParams, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  FiShoppingBag, FiExternalLink, FiStar, FiDollarSign, FiTrendingUp, FiTruck,
  FiArrowLeft, FiInfo, FiBookmark, FiPercent, FiAlertTriangle,
} from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Loader from '../../components/common/Loader'
import StatusBadge from '../../components/common/StatusBadge'
import { ItemIdentifiers } from '../../components/common/ListingCells'
import CompetingSellers from '../../components/common/CompetingSellers'
import EbayOtherSellers from '../../components/common/EbayOtherSellers'
import ItemPriceHistory from '../../components/common/ItemPriceHistory'
import ItemRecentChanges from '../../components/common/ItemRecentChanges'
import { marketplaceService } from '../../services/marketplaceService'
import { useDestination } from '../../hooks/useDestination'
import { marketplaceItemService } from '../../services/marketplaceItemService'
import AttachToProductModal from './AttachToProductModal'
import { useNotification } from '../../context/NotificationContext'
import {
  mpLabel, shortItemId, storefrontLabel, storefrontCode, marketplaceErrorText, REGIONS,
} from '../../utils/marketplaces'
import { formatCurrency, formatDate, formatRelativeTime } from '../../utils/format'

const dash = (v) => (v === null || v === undefined || v === '' ? '—' : v)

// Detail screen for a single marketplace listing: overview + live price + sales
// estimate + an interactive fee estimator.
//   GET  /marketplaces/{mp}/listings/{itemId}          detail
//   GET  /marketplaces/{mp}/listings/{itemId}/price    price breakdown
//   GET  /marketplaces/{mp}/listings/{itemId}/sales    sales estimate
//   POST /marketplaces/{mp}/fees  { marketplaceItemId, price, currency }
export default function MarketplaceListingDetail() {
  const { mp, itemId: rawId } = useParams()
  const itemId = decodeURIComponent(rawId)
  const location = useLocation()
  const navigate = useNavigate()
  const seed = location.state?.item   // search-result item, for instant header render
  // Where the goods would ship to — remembered from the search form. eBay
  // sellers quote delivery against it, and without one many quote nothing.
  const { destination } = useDestination()
  const [attaching, setAttaching] = useState(false)

  // Which national storefront everything on this page is read from.
  //
  // amazon.com and amazon.ca are separate catalogues with separate prices, and
  // every endpoint here now takes the storefront rather than answering from
  // whichever one replies first. The row that linked here knows it, so it is
  // taken from the URL; failing that, from the saved record.
  const [params, setParams] = useSearchParams()
  const [pickedRegion, setPickedRegion] = useState(null)
  const fromUrl = (params.get('storefront') || '').toUpperCase()
  const MP = String(mp).toUpperCase()

  const trackedQ = useQuery({
    queryKey: ['mp-item', mp, itemId, fromUrl],
    queryFn: () => marketplaceItemService.detail(mp, itemId, { storefront: fromUrl }).catch(() => null),
  })
  const tracked = trackedQ.data

  // '' means "whichever storefront has it" — the honest answer for a link
  // pasted in from nowhere, and the only case where the backend chooses.
  const resolved = pickedRegion ?? fromUrl ?? tracked?.storefront ?? ''
  const storefrontPending = !pickedRegion && !fromUrl && trackedQ.isLoading

  const detailQ = useQuery({
    queryKey: ['mp-listing', mp, itemId, resolved],
    queryFn: () => marketplaceService.listing(mp, itemId, { region: resolved }),
    enabled: !storefrontPending,
  })
  const priceQ = useQuery({
    queryKey: ['mp-price', mp, itemId, resolved],
    queryFn: () => marketplaceService.listingPrice(mp, itemId, { region: resolved }),
    enabled: !storefrontPending,
  })
  const salesQ = useQuery({
    queryKey: ['mp-sales', mp, itemId],
    queryFn: () => marketplaceService.listingSales(mp, itemId),
  })

  const d = detailQ.data || seed || {}
  const price = priceQ.data
  const sales = salesQ.data
  const currency = d.currency || price?.currency || 'USD'
  const anyMocked = detailQ.data?.mocked || price?.mocked || sales?.mocked

  // Where the read actually came from: what the response says, else what was
  // asked for. A storefront the connector is not authorised for now fails
  // loudly instead of quietly returning the US listing, so the error is shown
  // rather than a price from the wrong country.
  const shown = detailQ.data || null
  const shownCurrency = shown?.currency || currency
  const liveCode = storefrontCode(detailQ.data || {}) || resolved || ''
  const shopName = `${mpLabel(MP)} ${storefrontLabel({ countryCode: resolved }) || ''}`.trim()
  const reading = storefrontPending || detailQ.isLoading
  const readError = detailQ.isError ? marketplaceErrorText(detailQ.error, shopName) : null

  const setStorefront = (next) => {
    setPickedRegion(next)
    // Kept in the URL so the storefront survives a reload and a shared link —
    // the same listing at two prices is two different pages.
    const p = new URLSearchParams(params)
    if (next) p.set('storefront', next); else p.delete('storefront')
    setParams(p, { replace: true })
  }

  const headlinePrice = shown ? shown.price : d.price
  const viewUrl = shown?.url || d.url
  const priceView = price
  const priceCurrency = currency
  // The saved price and the live one now come from the same storefront
  // whenever the page was opened from a row that knew it, so a difference is a
  // real price move rather than two countries being compared.
  const sameShop = Boolean(tracked?.storefront && resolved && tracked.storefront === resolved)

  if (detailQ.isLoading && !seed) return <Loader label="Loading listing…" />

  return (
    <>
      {/* Breadcrumb goes to /search, not /marketplace-tools: only
          /marketplace-tools/:mp/:itemId is routed. The bare path pointed at
          MarketplaceExplorer, deleted when the three search surfaces were
          folded into one, so the link 404'd. Search is where a listing is
          found now, so that is where "up" belongs. */}
      <PageHeader
        title={d.title || itemId}
        subtitle={`${mpLabel(d.marketplace || mp)} · ${shortItemId(itemId)}`}
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Search', to: '/search' }, { label: 'Listing' }]}
        actions={<Button variant="light" icon={FiArrowLeft} onClick={() => navigate(-1)}>Back</Button>}
      />

      {anyMocked && (
        <div className="alert alert-info d-flex align-items-center gap-2 py-2 small">
          <FiInfo className="flex-shrink-0" />
          <span>This listing is <strong>sample data</strong> from a mock connector — not a live marketplace record.</span>
        </div>
      )}

      {/* Overview hero */}
      <Card className="mb-3">
        <div className="d-flex flex-wrap gap-3">
          <div className="rounded-3 flex-shrink-0 d-grid" style={{ width: 88, height: 88, placeItems: 'center', background: 'var(--hover-soft)', color: 'var(--primary)' }}>
            <FiShoppingBag size={34} />
          </div>
          <div className="flex-grow-1" style={{ minWidth: 220 }}>
            <div className="d-flex flex-wrap align-items-center gap-2 mb-1">
              <span className="badge text-bg-light border">{mpLabel(d.marketplace || mp)}</span>
              {d.condition && <StatusBadge status={d.condition} />}
              {d.available != null && (
                <StatusBadge status={d.available ? 'success' : 'danger'} label={d.available ? 'Available' : 'Unavailable'} />
              )}
            </div>
            <h5 className="fw-bold mb-1">{d.title || itemId}</h5>
            <div className="text-muted small">
              {d.brand && <span className="me-3">Brand: <span className="fw-semibold">{d.brand}</span></span>}
              {/* Only when it is genuinely a merchant. On Amazon this field
                  carries the brand, and the line above already shows it. */}
              {d.seller && d.seller !== d.brand && (
                <span className="me-3">Seller: <span className="fw-semibold">{d.seller}</span></span>
              )}
              {d.rating != null && (
                <span className="d-inline-flex align-items-center gap-1"><FiStar className="text-warning" /> {d.rating}{d.reviewCount != null && <span className="text-muted"> ({d.reviewCount})</span>}</span>
              )}
            </div>
            {/* The item ID is in the URL and the heading; the barcodes are what
                let someone reconcile this against a supplier's catalogue. */}
            <ItemIdentifiers identifiers={d.identifiers} />
            <div className="d-flex flex-wrap align-items-center gap-3 mt-2">
              {reading ? (
                <span className="text-muted small">{resolved ? `Reading ${shopName}…` : 'Loading price…'}</span>
              ) : (
                <span className="h4 fw-bold mb-0">
                  {headlinePrice != null ? formatCurrency(headlinePrice, shownCurrency) : '—'}
                </span>
              )}
              {/* Only once a live read is in — until then `d` is the search
                  result passed in for a fast first paint, and calling that
                  "live" would label the wrong number. */}
              {shown && !reading && <LiveSource live={shown} mp={MP} currency={shownCurrency} />}
              <label className="small text-muted d-inline-flex align-items-center gap-2 mb-0">
                Storefront
                <select className="form-select form-select-sm w-auto" value={resolved}
                  disabled={storefrontPending} onChange={(e) => setStorefront(e.target.value)}>
                  <option value="">Wherever it is sold</option>
                  {REGIONS.filter(([v]) => v).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
                </select>
              </label>
              {viewUrl && (
                <a className="btn btn-sm btn-light d-inline-flex align-items-center gap-1" href={viewUrl} target="_blank" rel="noreferrer">
                  <FiExternalLink /> View on {mpLabel(MP)}
                </a>
              )}
            </div>
            {readError && (
              <div className="field-note field-note--warn mt-2" style={{ maxWidth: 'none' }}>
                <FiAlertTriangle size={15} />
                <span>{readError}</span>
              </div>
            )}
            {(shown?.sourceTimestamp || d.sourceTimestamp) && (
              <div className="form-text mt-1">Fetched {formatDate(shown?.sourceTimestamp || d.sourceTimestamp)}.</div>
            )}
            {shown && !reading && (
              <SavedPriceNote live={shown} tracked={tracked} mp={MP} currency={shownCurrency} sameShop={sameShop} />
            )}
          </div>
        </div>
      </Card>

      <div className="row g-3">
        {/* Price breakdown */}
        <div className="col-12 col-lg-4">
          <Card title="Price" actions={<FiDollarSign className="text-muted" />} className="h-100">
            {reading || priceQ.isLoading ? <Loader label="Loading price…" />
              : priceQ.isError ? (
                <div className="text-danger small">{marketplaceErrorText(priceQ.error, shopName)}</div>
              )
                : (
                  <div className="d-flex flex-column gap-2">
                    <Row label="Item price" value={priceView?.itemPrice != null ? formatCurrency(priceView.itemPrice, priceCurrency) : '—'} strong />
                    <Row label="Shipping" value={priceView?.shipping != null ? formatCurrency(priceView.shipping, priceCurrency) : '—'} icon={FiTruck} />
                    <hr className="my-1" />
                    <Row label="Landed price" value={priceView?.landedPrice != null ? formatCurrency(priceView.landedPrice, priceCurrency) : '—'} strong big />
                    {/* Amazon publishes a price only for the offer that holds
                        the Buy Box. With no winner — nothing in stock, or only
                        third-party offers Amazon will not feature — there is no
                        price to report. A row of dashes on its own reads as a
                        loading failure, which is the wrong thing to chase. */}
                    {priceView?.itemPrice == null && priceView?.landedPrice == null && (
                      <div className="form-text">
                        No featured offer right now, so Amazon publishes no price for this listing.
                      </div>
                    )}
                    {priceView?.observedAt && <div className="form-text">Observed {formatDate(priceView.observedAt)}.</div>}
                  </div>
                )}
          </Card>
        </div>

        {/* Sales estimate */}
        <div className="col-12 col-lg-4">
          <Card title="Sales estimate" actions={<FiTrendingUp className="text-muted" />} className="h-100">
            {salesQ.isLoading ? <Loader label="Loading sales…" />
              : salesQ.isError ? <div className="text-danger small">{salesQ.error?.message || 'Sales estimate unavailable.'}</div>
                // UNAVAILABLE: the note is the answer — rows of dashes above it
                // say nothing and push the reason out of sight.
                  : !sales?.classification || sales.classification === 'UNAVAILABLE' ? (
                    <div className="text-muted small">
                      {sales?.note || 'No sales estimate for this listing — no connected marketplace reports competitor unit sales.'}
                    </div>
                  ) : (
                    <div className="d-flex flex-column gap-2">
                      <Row label="Est. units" value={dash(sales?.estimatedUnits)} strong />
                      <Row label="Est. revenue" value={sales?.estimatedRevenue != null ? formatCurrency(sales.estimatedRevenue, currency) : '—'} strong />
                      <Row label="Classification" value={<StatusBadge status={sales.classification} />} />
                      {sales?.note && <div className="form-text">{sales.note}</div>}
                    </div>
                  )}
          </Card>
        </div>

        {/* Fee estimator */}
        <div className="col-12 col-lg-4">
          {/* Keyed so switching storefront re-fills the price in the new
              currency — it pre-fills once and would otherwise keep the old. */}
          <FeeEstimator key={`${resolved}|${priceCurrency}`} mp={mp} itemId={itemId}
            defaultPrice={priceView?.itemPrice ?? headlinePrice} currency={priceCurrency} />
        </div>
      </div>

      {/* Two marketplaces, two questions. Amazon: who else is selling THIS
          listing, competing for its Buy Box. eBay: which OTHER listings are the
          same product, since every eBay seller has a listing of their own and
          there is no Buy Box to win. Same panel would answer neither. */}
      {MP === 'EBAY' ? (
        <EbayOtherSellers itemId={itemId} region={resolved || liveCode} destination={destination}
          currency={shownCurrency} />
      ) : (
        <CompetingSellers mp={mp} itemId={itemId} identifiers={d.identifiers} currency={shownCurrency}
          region={resolved} storefront={liveCode ? `${mpLabel(MP)} ${storefrontLabel({ countryCode: liveCode }) || liveCode}` : undefined} />
      )}

      {/* One storefront's history, in that storefront's currency — the mixed
          US/CA line this page used to draw was two catalogues in one chart. */}
      <TrackedHistory mp={mp} itemId={itemId} storefront={tracked?.storefront || resolved}
        currency={tracked?.currency || shownCurrency} />

      <Card className="mt-3">
        <div className="d-flex flex-wrap align-items-center justify-content-between gap-2">
          <div>
            <div className="fw-semibold d-flex align-items-center gap-2"><FiBookmark /> Save to a product</div>
            <div className="text-muted small">
              Attach this listing to one of your products so it counts towards price statistics.
            </div>
          </div>
          <Button variant="light" icon={FiBookmark} onClick={() => setAttaching(true)}>
            Save to product
          </Button>
        </div>
      </Card>

      <AttachToProductModal
        show={attaching}
        onClose={() => setAttaching(false)}
        marketplace={mp}
        itemId={itemId}
        title={d.title}
        storefront={liveCode}
      />
    </>
  )
}

// Where the headline price was read from. The listing endpoints take no region
// or delivery address, so the backend reads its default storefront — which
// need not be the one the search used. Saying which is the difference between
// two prices that look contradictory and two that are simply from two shops.
function LiveSource({ live, mp, currency }) {
  const country = storefrontLabel(live)
  return (
    <span className="text-muted small"
      title={live.via === 'search'
        ? 'Read through a search in this storefront — the listing endpoint can only read the default one.'
        : live.marketplaceId ? `Storefront ${live.marketplaceId}` : undefined}>
      live from {mpLabel(live.marketplace || mp)} {country || '(storefront not reported)'} · {currency}
    </span>
  )
}

// The live price beside the one a search saved, when they disagree.
//
// Item price against item price: Tracked Items shows the landed price (item +
// shipping), and comparing that with an item-only figure would flag every
// listing that charges postage.
//
// `sameShop` means the live read came from the storefront the saved price did,
// so a different number can only be a real move.
function SavedPriceNote({ live, tracked, mp, currency, sameShop }) {
  if (!tracked || live?.price == null) return null
  const savedCur = tracked.currency || currency
  const otherCurrency = savedCur !== currency
  const differs = otherCurrency
    || (tracked.itemPrice != null && Math.round(tracked.itemPrice * 100) !== Math.round(live.price * 100))
  if (!differs) return null

  // Shown as the same figure Tracked Items shows, so the two screens can be
  // matched by eye; the shipping is named so it is not mistaken for a move.
  const saved = tracked.landedPrice ?? tracked.itemPrice
  if (saved == null) return null
  const shipping = tracked.shipping > 0 ? ` (incl. ${formatCurrency(tracked.shipping, savedCur)} shipping)` : ''
  const when = tracked.lastSeenAt ? ` ${formatRelativeTime(tracked.lastSeenAt)}` : ''
  const country = storefrontLabel(live)
  const shop = `${mpLabel(live.marketplace || mp)}${country ? ` ${country}` : ''}`
  const savedPlace = storefrontLabel({ countryCode: tracked.storefront })

  // A different currency is not a price move: the two numbers came from two
  // storefronts, and only the saved one is from the storefront you searched.
  if (otherCurrency) {
    return (
      <div className="field-note field-note--warn mt-2" style={{ maxWidth: 'none' }}>
        <FiAlertTriangle size={15} />
        <span>
          Your search saved this listing at <strong>{formatCurrency(saved, savedCur)}</strong>{shipping}{when}.
          The price above is in {currency}, read from {shop} — not the storefront your search used.
          {savedPlace
            ? <> Choose <strong>{savedPlace}</strong> under Storefront to read that one.</>
            : ' Compare against the saved price, which is what Tracked Items shows.'}
        </span>
      </div>
    )
  }

  // Same currency, different number: either the price moved since the search,
  // or the live read came from another storefront in the same currency. The
  // page cannot tell which, so it says both.
  return (
    <div className="field-note mt-2" style={{ maxWidth: 'none' }}>
      <FiInfo size={15} />
      <span>
        Your search saved <strong>{formatCurrency(saved, savedCur)}</strong>{shipping}{when}.
        {sameShop
          ? <> {shop} shows a different price now, so it has moved since.</>
          : <> Either the price has moved since, or this live read came from a different storefront ({shop}).</>}
        {' '}Search the product again to update the saved price.
      </span>
    </div>
  )
}

function Row({ label, value, icon: Icon, strong, big }) {
  return (
    <div className="d-flex align-items-center justify-content-between">
      <span className="text-muted small d-inline-flex align-items-center gap-1">{Icon && <Icon size={13} />}{label}</span>
      <span className={`${strong ? 'fw-bold' : 'fw-semibold'} ${big ? 'h5 mb-0' : ''}`}>{value}</span>
    </div>
  )
}

// Interactive fee estimate — POST /{mp}/fees with an editable price.
function FeeEstimator({ mp, itemId, defaultPrice, currency }) {
  const { notify } = useNotification()
  const [price, setPrice] = useState(defaultPrice ?? '')
  const [result, setResult] = useState(null)
  const [busy, setBusy] = useState(false)

  // Prefill once the listing price loads.
  useEffect(() => { if (defaultPrice != null && price === '') setPrice(defaultPrice) }, [defaultPrice]) // eslint-disable-line react-hooks/exhaustive-deps

  const estimate = async () => {
    const p = Number(price)
    if (!Number.isFinite(p) || p <= 0) { notify.error('Enter a sale price to estimate fees'); return }
    setBusy(true); setResult(null)
    try {
      const r = await marketplaceService.fees(mp, { marketplaceItemId: itemId, price: p, currency })
      setResult(r)
    } catch (e) { notify.error(e.message || 'Fee estimate failed') }
    finally { setBusy(false) }
  }

  const net = result ? Number(result.price) - Number(result.totalFees || 0) : null

  return (
    <Card title="Fee estimate" actions={<FiPercent className="text-muted" />} className="h-100">
      <label className="form-label small text-muted mb-1">Sale price ({currency})</label>
      <div className="input-group input-group-sm mb-3">
        <input type="number" step="0.01" min="0" className="form-control" value={price}
          onChange={(e) => setPrice(e.target.value)} placeholder="0.00" />
        <Button icon={FiDollarSign} loading={busy} onClick={estimate}>Estimate</Button>
      </div>
      {result ? (
        <div className="d-flex flex-column gap-2">
          <Row label="Referral fee" value={result.referralFee != null ? formatCurrency(result.referralFee, currency) : '—'} />
          <Row label="Fulfilment fee" value={result.fulfilmentFee != null ? formatCurrency(result.fulfilmentFee, currency) : '—'} />
          <hr className="my-1" />
          <Row label="Total fees" value={result.totalFees != null ? formatCurrency(result.totalFees, currency) : '—'} strong />
          {net != null && Number.isFinite(net) && <Row label="Net proceeds" value={formatCurrency(net, currency)} strong big />}
        </div>
      ) : (
        <div className="text-muted small">Enter a sale price to estimate marketplace fees and net proceeds.</div>
      )}
    </Card>
  )
}

// Price over time for this item, plus what moved and when.
//
// GET /marketplace-items/{mp}/{itemId}/price-history returns a plain array —
// no page wrapper — and repeats are no longer stored, so consecutive points are
// always genuinely different prices. A flat run therefore means the price held,
// not that the same number was written six times.
function TrackedHistory({ mp, itemId, currency, storefront }) {
  return (
    <div className="row g-3 mt-0">
      <div className="col-12 col-lg-7">
        <ItemPriceHistory marketplace={mp} itemId={itemId} currency={currency} storefront={storefront} />
      </div>
      <div className="col-12 col-lg-5">
        <ItemRecentChanges marketplace={mp} itemId={itemId} storefront={storefront} />
      </div>
    </div>
  )
}
