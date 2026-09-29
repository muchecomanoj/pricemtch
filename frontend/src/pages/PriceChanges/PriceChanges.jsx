import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { FiX, FiArrowRight } from 'react-icons/fi'
import SearchBar from '../../components/common/SearchBar'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import DataTable from '../../components/tables/DataTable'
import Pagination from '../../components/common/Pagination'
import Button from '../../components/common/Button'
import { ChangeDelta, ChangeWhen, CHANGE_FIELD, isPriceCut } from '../../components/common/ChangeEvent'
import { marketplaceItemService } from '../../services/marketplaceItemService'
import MarketplaceLogo from '../../components/common/MarketplaceLogo'
import { mpLabel } from '../../utils/marketplaces'
import { useMarketplaceCodes } from '../../hooks/useMarketplaces'

const PAGE_SIZE = 25

// "Price cuts only" needs every event in the window, not one page of it.
//
// The endpoint has no direction or seller filter, so cuts are picked out here.
// Filtering the 25 rows on screen left the title and the pager counting the
// whole unfiltered feed — 101 changes, 101 "price cuts", five pages of mostly
// hidden rows. Walking all pages gives a real count and real pagination.
// Capped so a busy account with months of history cannot pull thousands of
// rows on one click; the cap is reported when hit rather than miscounted.
const CUTS_CAP = 2000
const CUTS_FETCH_SIZE = 200

async function fetchWholeWindow(params) {
  let events = []
  let total = 0
  for (let page = 0; events.length < CUTS_CAP; page += 1) {
    const res = await marketplaceItemService.changes({ ...params, page, size: CUTS_FETCH_SIZE })
    events = events.concat(res?.content ?? [])
    total = res?.totalElements ?? events.length
    if (page + 1 >= (res?.totalPages ?? 0)) break
  }
  return { events, total, truncated: events.length < total }
}

// "What happened this week" — every recorded movement across every tracked
// item, newest first.
const WINDOWS = [
  ['1', 'Last 24 hours'],
  ['7', 'Last 7 days'],
  ['30', 'Last 30 days'],
  ['', 'All time'],
]

export default function PriceChanges() {
  const [page, setPage] = useState(1)
  const [searchInput, setSearchInput] = useState('')   // what is typed
  const [search, setSearch] = useState('')             // debounced
  const [marketplace, setMarketplace] = useState('')
  // LANDED_PRICE by default. Every movement writes a PRICE row and a
  // LANDED_PRICE row — identical whenever the marketplace reports no shipping,
  // which on Amazon is nearly always — so the unfiltered feed shows each change
  // twice. Landed is also the comparable figure: item price plus delivery.
  const [field, setField] = useState('LANDED_PRICE')
  const [days, setDays] = useState('7')
  // Shows only genuine decreases: down, on a money field, and not a Buy Box
  // handover. See fetchWholeWindow for why this fetches differently.
  const [cutsOnly, setCutsOnly] = useState(false)
  const { codes: marketplaces } = useMarketplaceCodes()

  // Debounced so a request does not go per keystroke, and back to page 1 with
  // it: a narrower result set has fewer pages, and staying on page 4 of a
  // 2-page answer shows nothing at all.
  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput.trim()); setPage(1) }, 300)
    return () => clearTimeout(t)
  }, [searchInput])

  // `from` is an ISO instant. Recomputed only when the window changes, so it
  // does not drift on every render and thrash the query cache.
  const from = useMemo(() => {
    if (!days) return undefined
    return new Date(Date.now() - Number(days) * 86400000).toISOString()
  }, [days])

  const hasFilters = Boolean(
    marketplace || field !== 'LANDED_PRICE' || days !== '7' || cutsOnly || search)
  // Only the cuts filter still needs the whole window: it is the one thing the
  // endpoint cannot do, having no direction or seller parameter. Search is
  // applied in the database now, so it pages normally.
  const wholeWindow = cutsOnly

  // One page from the server — the normal feed.
  const feedQ = useQuery({
    queryKey: ['priceChanges', { page, marketplace, field, days, search }],
    queryFn: () => marketplaceItemService.changes({
      page: page - 1, size: PAGE_SIZE, marketplace, field, from, search,
    }),
    placeholderData: keepPreviousData,
    enabled: !wholeWindow,
  })
  // The whole window, filtered and paged here — cuts mode only. Not keyed on
  // `page`: turning pages slices what is already loaded.
  const cutsQ = useQuery({
    queryKey: ['priceChanges', 'window', { marketplace, field, days, search }],
    queryFn: () => fetchWholeWindow({ marketplace, field, from, search }),
    enabled: wholeWindow,
  })

  const clearFilters = () => {
    setMarketplace(''); setField('LANDED_PRICE'); setDays('7'); setCutsOnly(false)
    setSearchInput(''); setSearch(''); setPage(1)
  }

  const filtered = useMemo(
    () => (cutsQ.data?.events ?? []).filter(isPriceCut),
    [cutsQ.data],
  )

  const windowTotal = cutsQ.data?.total ?? 0

  const { isLoading, isError, error } = wholeWindow ? cutsQ : feedQ
  const total = wholeWindow ? filtered.length : (feedQ.data?.totalElements ?? 0)
  const totalPages = wholeWindow
    ? Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
    : (feedQ.data?.totalPages ?? 1)
  const rows = wholeWindow
    ? filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)
    : (feedQ.data?.content ?? [])

  const rangeStart = rows.length ? (page - 1) * PAGE_SIZE + 1 : 0
  const rangeEnd = rows.length ? rangeStart + rows.length - 1 : 0
  // Rows with no seller badge at all. Seller ids are captured only when a price
  // moves, and only since that shipped — so an older event has no seller on
  // either side and nothing to compare. Left blank rather than labelled "same",
  // because guessing wrong there is the difference between "react" and
  // "ignore". Explained once here rather than as a chip on every such row.
  const unknownSeller = rows.filter((r) => r.sellerChanged == null).length

  const columns = [
    {
      key: 'item',
      header: 'Item',
      minWidth: 220,
      // The event carries no title — only the marketplace and item ID — so the
      // ID is the heading here and the link goes where the title lives.
      render: (r) => (
        <Link className="text-decoration-none"
          to={`/marketplace-tools/${String(r.marketplace).toLowerCase()}/${encodeURIComponent(r.marketplaceItemId)}${
            r.storefront ? `?storefront=${r.storefront}` : ''}`}>
          {/* The name leads, because that is what a person recognises. The id
              stays underneath: it is what the marketplace and every support
              conversation key on. Null on changes recorded before the item was
              tracked, so the id stands alone there. */}
          {r.title ? (
            <>
              <span className="fw-semibold d-block text-truncate" style={{ maxWidth: 320 }}
                title={r.title}>
                {r.title}
              </span>
              <span className="font-monospace text-muted small">{r.marketplaceItemId}</span>
            </>
          ) : (
            <span className="font-monospace fw-semibold">{r.marketplaceItemId}</span>
          )}
          {/* Storefront included: a move on amazon.ca is a different event from
              a move on amazon.com, and they used to be indistinguishable. */}
          <span className="d-block mt-1">
            <MarketplaceLogo marketplace={r.marketplace} storefront={r.storefront} />
          </span>
        </Link>
      ),
    },
    {
      key: 'field',
      header: 'What changed',
      className: 'text-nowrap',
      render: (r) => <span className="mr-pill mr-pill--slate">{CHANGE_FIELD[r.field] || r.field}</span>,
    },
    {
      key: 'delta',
      header: 'Movement',
      minWidth: 300,
      render: (r) => <ChangeDelta event={r} />,
    },
    {
      key: 'when',
      header: 'Observed',
      className: 'text-nowrap',
      render: (r) => <ChangeWhen event={r} />,
    },
    {
      key: 'actions',
      header: '',
      className: 'text-end',
      render: (r) => (
        <Link className="icon-btn" title="Open item"
          to={`/marketplace-tools/${String(r.marketplace).toLowerCase()}/${encodeURIComponent(r.marketplaceItemId)}${
            r.storefront ? `?storefront=${r.storefront}` : ''}`}>
          <FiArrowRight />
        </Link>
      ),
    },
  ]

  return (
    <>
      <PageHeader title="Price Changes"
        subtitle="Every movement recorded on a tracked marketplace item — price, shipping and availability."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Price Changes' }]} />

      <Card title={cutsOnly ? `Price cuts (${total})` : `Changes (${total})`}>
        <div className="d-flex flex-wrap gap-2 mb-3">
          <SearchBar grow value={searchInput} onChange={setSearchInput}
            placeholder="Find by product name, ASIN or item ID…" />
          <select className="form-select" style={{ maxWidth: 170 }} value={days}
            onChange={(e) => { setDays(e.target.value); setPage(1) }}>
            {WINDOWS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
          <select className="form-select" style={{ maxWidth: 190 }} value={marketplace}
            onChange={(e) => { setMarketplace(e.target.value); setPage(1) }}>
            <option value="">All marketplaces</option>
            {marketplaces.map((m) => <option key={m} value={m}>{mpLabel(m)}</option>)}
          </select>
          <select className="form-select" style={{ maxWidth: 190 }} value={field}
            onChange={(e) => { setField(e.target.value); setPage(1) }}>
            <option value="">Everything</option>
            {Object.entries(CHANGE_FIELD).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
          <div className="form-check d-flex align-items-center gap-2 ms-1">
            <input className="form-check-input mt-0" type="checkbox" id="cuts-only"
              checked={cutsOnly} onChange={(e) => { setCutsOnly(e.target.checked); setPage(1) }} />
            <label className="form-check-label small text-nowrap" htmlFor="cuts-only"
              title="Only decreases, and only where the same seller lowered the price">
              Price cuts only
            </label>
          </div>
          {hasFilters && <Button variant="light" icon={FiX} onClick={clearFilters}>Clear filters</Button>}
        </div>

        {unknownSeller > 0 && (
          <div className="text-muted small mb-3">
            {unknownSeller} of {rows.length} have no seller shown — those were recorded before
            seller tracking began, so we cannot tell a price cut from the Buy Box changing hands.
            New changes carry it.
          </div>
        )}

        {cutsOnly && cutsQ.data && !search && (
          <div className="text-muted small mb-3">
            {filtered.length} of {windowTotal} changes in this window are price cuts. The other{' '}
            {windowTotal - filtered.length} are price rises, availability changes, or the Buy Box
            moving to a different seller rather than a price falling.
            {cutsQ.data.truncated && (
              <> Only the newest {cutsQ.data.events.length} were checked — narrow the window or pick
                a marketplace for a complete count.</>
            )}
          </div>
        )}

        {isError ? (
          <div className="alert alert-danger py-2 small mb-0">
            {error?.message || 'Could not load price changes.'}
          </div>
        ) : (
          <>
            <DataTable
              columns={columns}
              rows={rows}
              loading={isLoading}
              // An empty feed is the normal, correct answer when prices have
              // held — not a failure. It should not read like one.
              emptyMessage={hasFilters
                ? 'Nothing moved in this window. Widen the range or clear the filters.'
                : 'No changes recorded yet. Tracked prices have held since we started watching them.'}
            />
            {totalPages > 1 && (
              <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mt-3 pt-3 border-top-soft">
                <span className="text-muted small">
                  Showing <strong>{rangeStart}</strong>–<strong>{rangeEnd}</strong> of{' '}
                  <strong>{total}</strong>
                </span>
                <Pagination page={page} totalPages={totalPages} onChange={setPage} />
              </div>
            )}
          </>
        )}
      </Card>
    </>
  )
}
