import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { FiExternalLink, FiX, FiEye } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import DataTable from '../../components/tables/DataTable'
import Pagination from '../../components/common/Pagination'
import SearchBar from '../../components/common/SearchBar'
import Button from '../../components/common/Button'
import { ItemIdentifiers } from '../../components/common/ListingCells'
import { marketplaceItemService } from '../../services/marketplaceItemService'
import { formatCurrency, formatDate, formatRelativeTime } from '../../utils/format'
import MarketplaceLogo from '../../components/common/MarketplaceLogo'
import { mpLabel, shortItemId } from '../../utils/marketplaces'
import { useMarketplaceCodes } from '../../hooks/useMarketplaces'

const PAGE_SIZE = 20

// Every listing the judge accepted during a search, followed from then on.
// Rejected lookalikes appear in search results but are not tracked, which is
// why this page is shorter than a day's searching would suggest.

export default function TrackedItems() {
  const [page, setPage] = useState(1)            // UI is 1-based, API is 0-based
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [marketplace, setMarketplace] = useState('')
  const { codes: marketplaces } = useMarketplaceCodes()

  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput); setPage(1) }, 400)
    return () => clearTimeout(t)
  }, [searchInput])

  const hasFilters = Boolean(search || marketplace)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['marketplaceItems', { page, search, marketplace }],
    queryFn: () => marketplaceItemService.list({ page: page - 1, size: PAGE_SIZE, search, marketplace }),
    placeholderData: keepPreviousData,
  })

  const clearFilters = () => { setSearchInput(''); setSearch(''); setMarketplace(''); setPage(1) }

  const shown = data?.content?.length ?? 0
  const rangeStart = shown ? (page - 1) * PAGE_SIZE + 1 : 0
  const rangeEnd = shown ? rangeStart + shown - 1 : 0

  const columns = [
    {
      key: 'item',
      header: 'Item',
      minWidth: 320,
      render: (r) => (
        <div style={{ maxWidth: 420 }}>
          <Link to={`/marketplace-tools/${String(r.marketplace).toLowerCase()}/${encodeURIComponent(r.marketplaceItemId)}`}
            className="fw-semibold clamp-2 text-decoration-none" title={r.title || ''}>
            {r.title || 'Untitled listing'}
          </Link>
          <div className="text-muted small mt-1 d-flex flex-wrap gap-2">
            <span className="font-monospace" title={r.marketplaceItemId}>
              {shortItemId(r.marketplaceItemId)}
            </span>
            <MarketplaceLogo marketplace={r.marketplace} storefront={r.storefront} />
            {/* Amazon's catalogue returns no merchant, so `seller` is filled
                with the brand — printing both gives "Apple · Apple", and
                calling the second one a seller states something untrue. */}
            {r.brand && <span>· {r.brand}</span>}
            {/* `seller` on Amazon is the brand fallback; `sellerId` is the real
                merchant now that offers are read when a price moves. Prefer the
                id where it exists, and never print the brand as a merchant. */}
            {r.sellerId
              ? <span title={r.sellerId}>· sold by {shortItemId(r.sellerId)}</span>
              : r.seller && r.seller !== r.brand && <span>· sold by {r.seller}</span>}
            {r.condition && <span>· {r.condition}</span>}
          </div>
          <ItemIdentifiers identifiers={r.identifiers} />
        </div>
      ),
    },
    {
      key: 'price',
      // landedPrice is item + shipping and is what comparisons must use.
      header: 'Landed price',
      className: 'text-nowrap',
      render: (r) => {
        const v = r.landedPrice ?? r.itemPrice
        // Amazon returns no price when nobody holds the Buy Box. That is
        // "unknown", never zero — a 0 here would read as free.
        if (v == null) return <span className="text-muted">no price</span>
        return (
          <>
            <span className="fw-semibold">{formatCurrency(v, r.currency || 'USD')}</span>
            {r.shipping > 0 && (
              <div className="text-muted" style={{ fontSize: 11 }}>
                incl. {formatCurrency(r.shipping, r.currency || 'USD')} shipping
              </div>
            )}
          </>
        )
      },
    },
    {
      key: 'watch',
      // The two counts only mean something together. "Checked 12×, moved 2×"
      // says the price is genuinely stable; "checked 1×, moved 0×" says nothing
      // is known yet. One number alone cannot tell those apart.
      header: 'Watched',
      className: 'text-nowrap',
      render: (r) => (
        <>
          <div className="small">
            checked <strong>{r.observationCount ?? 0}×</strong>
            {' · '}
            moved <strong>{r.changeCount ?? 0}×</strong>
          </div>
          {r.firstSeenAt && (
            <div className="text-muted" style={{ fontSize: 11 }}>
              tracked since {formatDate(r.firstSeenAt)}
            </div>
          )}
        </>
      ),
    },
    {
      key: 'seen',
      // lastSeenAt and lastChangedAt answer different questions. A price
      // unchanged for a month and a price nobody has looked at for a month are
      // indistinguishable unless both are shown.
      header: 'Last checked',
      className: 'text-nowrap',
      render: (r) => (
        <>
          <div className="small">{r.lastSeenAt ? formatRelativeTime(r.lastSeenAt) : '—'}</div>
          <div className="text-muted" style={{ fontSize: 11 }}>
            {r.lastChangedAt
              ? `this price since ${formatDate(r.lastChangedAt)}`
              : 'never priced'}
          </div>
        </>
      ),
    },
    {
      key: 'actions',
      header: '',
      className: 'text-end text-nowrap',
      render: (r) => (
        <>
          {/* The storefront travels with the link: the detail page reads
              prices from it rather than guessing, and the same ASIN in two
              countries opens as two different pages. */}
          <Link className="icon-btn" title="Price history and changes"
            to={`/marketplace-tools/${String(r.marketplace).toLowerCase()}/${encodeURIComponent(r.marketplaceItemId)}${
              r.storefront ? `?storefront=${r.storefront}` : ''}`}>
            <FiEye />
          </Link>
          {/* The backend picks the country domain matching the region the price
              came from, so the URL is used as sent and never rebuilt here. */}
          {r.url
            ? <a href={r.url} target="_blank" rel="noreferrer" className="icon-btn" title="Open on marketplace">
              <FiExternalLink />
            </a>
            : <span className="icon-btn opacity-25" title="No link available"><FiExternalLink /></span>}
        </>
      ),
    },
  ]

  return (
    <>
      <PageHeader title="Tracked Items"
        subtitle="Marketplace listings confirmed as real products, followed over time so their prices are kept."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Tracked Items' }]} />

      <Card
        title={`Tracked items (${data?.totalElements ?? 0})`}
        actions={<SearchBar value={searchInput} onChange={setSearchInput} placeholder="Filter by title or ID…" />}
      >
        <div className="d-flex flex-wrap gap-2 mb-3">
          <select className="form-select" style={{ maxWidth: 190 }} value={marketplace}
            onChange={(e) => { setMarketplace(e.target.value); setPage(1) }}>
            <option value="">All marketplaces</option>
            {marketplaces.map((m) => <option key={m} value={m}>{mpLabel(m)}</option>)}
          </select>
          {hasFilters && <Button variant="light" icon={FiX} onClick={clearFilters}>Clear filters</Button>}
        </div>

        {isError ? (
          <div className="alert alert-danger py-2 small mb-0">
            {error?.message || 'Could not load tracked items.'}
          </div>
        ) : (
          <>
            <DataTable
              columns={columns}
              rows={data?.content ?? []}
              loading={isLoading}
              emptyMessage={hasFilters
                ? 'No tracked items match your filter.'
                : 'Nothing tracked yet. Run a product search — every listing the AI confirms is followed from then on.'}
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
