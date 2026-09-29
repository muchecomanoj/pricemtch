import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { FiExternalLink, FiX } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import DataTable from '../../components/tables/DataTable'
import Pagination from '../../components/common/Pagination'
import StatusBadge from '../../components/common/StatusBadge'
import SearchBar from '../../components/common/SearchBar'
import Button from '../../components/common/Button'
import { competitorListingService } from '../../services/competitorListingService'
import { formatRelativeTime } from '../../utils/format'
import { mpLabel } from '../../utils/marketplaces'
import { useMarketplaceCodes } from '../../hooks/useMarketplaces'
import { MarginCell, LandedPrice } from '../../components/common/ListingCells'

const PAGE_SIZE = 20
const CONDITIONS = ['NEW', 'USED', 'REFURBISHED']
const MATCH_STATUSES = ['CANDIDATE', 'MATCHED', 'EQUIVALENT', 'REJECTED']

// Availability is its own vocabulary — map to a readable label.
const AVAILABILITY = {
  IN_STOCK: 'In Stock', LOW_STOCK: 'Low Stock', OUT_OF_STOCK: 'Out of Stock',
}

// Amazon rows come back with null shipping/availability/rating — only eBay and
// Web fill those in. Every cell below must render '—' rather than blank or NaN.
const dash = (v) => (v == null || v === '' ? '—' : v)

export default function CompetitorListings() {
  const [page, setPage] = useState(1)            // UI is 1-based, API is 0-based
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')       // debounced value actually queried
  const [marketplace, setMarketplace] = useState('')
  const [condition, setCondition] = useState('')
  const [matchStatus, setMatchStatus] = useState('')
  // Channels come from the API — a hardcoded list sends filters it rejects.
  const { codes: marketplaces } = useMarketplaceCodes()

  // Debounce the filter box so typing doesn't fire a request per keystroke.
  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput); setPage(1) }, 400)
    return () => clearTimeout(t)
  }, [searchInput])

  const filters = { search, marketplace, condition, matchStatus }
  const hasFilters = Object.values(filters).some(Boolean)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['competitorListings', { page, ...filters }],
    queryFn: () => competitorListingService.list({ page: page - 1, size: PAGE_SIZE, ...filters }),
    placeholderData: keepPreviousData,
  })

  const clearFilters = () => {
    setSearchInput(''); setSearch(''); setMarketplace(''); setCondition(''); setMatchStatus(''); setPage(1)
  }

  // "Showing 21–30 of 30" — the row range on this page, not just its size.
  const shown = data?.content?.length ?? 0
  const rangeStart = shown ? (page - 1) * PAGE_SIZE + 1 : 0
  const rangeEnd = shown ? rangeStart + shown - 1 : 0

  const columns = [
    {
      key: 'product',
      header: 'Product',
      minWidth: 260,
      // Both titles. Showing only our product name made five different
      // refrigerators matched to one SKU look like five identical rows —
      // the competitor's own title is the only thing that tells them apart.
      render: (r) => (
        <div style={{ maxWidth: 340 }}>
          <div className="fw-semibold clamp-2" title={r.title || ''}>{dash(r.title)}</div>
          {r.productId
            ? <Link to={`/products/${r.productId}`} className="text-muted small text-decoration-none d-block text-truncate"
              title={r.productTitle || ''}>
              matched to {dash(r.productTitle)}
            </Link>
            : <span className="text-muted small">unmatched</span>}
        </div>
      ),
    },
    { key: 'marketplace', header: 'Marketplace', className: 'text-nowrap', render: (r) => dash(r.marketplace) },
    { key: 'seller', header: 'Seller', minWidth: 140, render: (r) => dash(r.seller) },
    {
      key: 'landedPrice',
      // landedPrice is item price + shipping and is what the margin is figured
      // from. lastPrice is the raw marketplace price, so the two differ — the
      // header must not claim "landed" while rendering the other one.
      header: 'Landed price',
      className: 'text-nowrap',
      render: (r) => <LandedPrice listing={r} />,
    },
    {
      key: 'estimatedMargin',
      header: 'Est. margin',
      className: 'text-nowrap',
      render: (r) => <MarginCell listing={r} />,
    },
    {
      key: 'availability',
      header: 'Availability',
      className: 'text-nowrap',
      render: (r) => (r.availability
        ? <StatusBadge status={r.availability} label={AVAILABILITY[r.availability] || r.availability} />
        : <span className="text-muted">—</span>),
    },
    {
      key: 'condition',
      header: 'Condition',
      className: 'text-nowrap',
      render: (r) => (r.condition ? <StatusBadge status={r.condition} /> : <span className="text-muted">—</span>),
    },
    {
      key: 'matchStatus',
      header: 'Match',
      className: 'text-nowrap',
      render: (r) => (r.matchStatus ? <StatusBadge status={r.matchStatus} /> : <span className="text-muted">—</span>),
    },
    {
      key: 'sourceTimestamp',
      header: 'Last updated',
      className: 'text-nowrap',
      render: (r) => <span className="text-muted small">{r.sourceTimestamp ? formatRelativeTime(r.sourceTimestamp) : '—'}</span>,
    },
    {
      key: 'actions',
      header: '',
      className: 'text-end',
      render: (r) => (r.url
        ? <a href={r.url} target="_blank" rel="noreferrer" className="icon-btn" title="Open listing"><FiExternalLink /></a>
        : <span className="icon-btn opacity-25" title="No link available"><FiExternalLink /></span>),
    },
  ]

  return (
    <>
      <PageHeader title="Competitor Listings"
        subtitle="Listings captured by product searches across your enabled marketplaces."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Competitors' }]} />

      <Card
        title={`Listings (${data?.totalElements ?? 0})`}
        actions={<SearchBar value={searchInput} onChange={setSearchInput} placeholder="Filter listings…" />}
      >
        <div className="d-flex flex-wrap gap-2 mb-3">
          <select className="form-select" style={{ maxWidth: 170 }} value={marketplace}
            onChange={(e) => { setMarketplace(e.target.value); setPage(1) }}>
            <option value="">All marketplaces</option>
            {marketplaces.map((m) => <option key={m} value={m}>{mpLabel(m)}</option>)}
          </select>
          <select className="form-select" style={{ maxWidth: 160 }} value={condition}
            onChange={(e) => { setCondition(e.target.value); setPage(1) }}>
            <option value="">All conditions</option>
            {CONDITIONS.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <select className="form-select" style={{ maxWidth: 170 }} value={matchStatus}
            onChange={(e) => { setMatchStatus(e.target.value); setPage(1) }}>
            <option value="">All match states</option>
            {MATCH_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          {hasFilters && <Button variant="light" icon={FiX} onClick={clearFilters}>Clear filters</Button>}
        </div>

        {isError ? (
          <div className="alert alert-danger py-2 small mb-0">
            {error?.message || 'Could not load competitor listings.'}
          </div>
        ) : (
          <>
            <DataTable
              columns={columns}
              rows={data?.content ?? []}
              loading={isLoading}
              emptyMessage={hasFilters
                ? 'No listings match your filter.'
                : 'No competitor listings yet. Run a search on a product to populate this page.'}
            />
            {(data?.totalPages ?? 1) > 1 && (
              <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mt-3 pt-3 border-top-soft">
                <span className="text-muted small">
                  Showing <strong>{rangeStart}</strong>–<strong>{rangeEnd}</strong> of{' '}
                  <strong>{data?.totalElements ?? 0}</strong>
                </span>
                <Pagination page={page} totalPages={data?.totalPages ?? 1} onChange={setPage} />
              </div>
            )}
          </>
        )}
      </Card>
    </>
  )
}
