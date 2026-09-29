import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { FiSearch, FiBookmark, FiArrowRight, FiInfo, FiAlertTriangle } from 'react-icons/fi'
import Modal from '../../components/common/Modal'
import Button from '../../components/common/Button'
import SearchBar from '../../components/common/SearchBar'
import EmptyState from '../../components/common/EmptyState'
import { SkeletonRows } from '../../components/common/Skeleton'
import { productService } from '../../services/productService'
import { competitorListingService } from '../../services/competitorListingService'
import { useNotification } from '../../context/NotificationContext'
import MarketplaceLogo from '../../components/common/MarketplaceLogo'
import { marketplaceErrorText, storefrontLabel, mpLabel } from '../../utils/marketplaces'
import { formatCurrency } from '../../utils/format'

// Attaching a marketplace listing to one of our products.
//
// With no `decision` the listing lands as a CANDIDATE — "look at this", not
// "this is the same product" — which is what an unjudged listing gets. Search
// passes the verdict it already has, and the backend still runs the pack-size
// and condition rules on the way in.
export default function AttachToProductModal({
  show, onClose, marketplace, itemId, title, decision,
  // The storefront the listing was FOUND in. Without it the backend re-fetches
  // from whichever storefront answers first, which is how a listing found on
  // amazon.ca was stored as the amazon.com one at a US price.
  storefront,
}) {
  const { notify } = useNotification()
  const shopName = `${mpLabel(String(marketplace || '').toUpperCase())} ${
    storefrontLabel({ countryCode: storefront }) || ''}`.trim()
  const [term, setTerm] = useState('')
  const [search, setSearch] = useState('')
  const [saving, setSaving] = useState(null)     // productId in flight
  const [done, setDone] = useState(null)         // { product, result }

  useEffect(() => {
    if (!show) { setTerm(''); setSearch(''); setDone(null); setSaving(null) }
  }, [show])

  // Debounced so typing doesn't fire a request per keystroke.
  useEffect(() => {
    const t = setTimeout(() => setSearch(term), 350)
    return () => clearTimeout(t)
  }, [term])

  const { data, isLoading } = useQuery({
    queryKey: ['products', 'attach-picker', search],
    queryFn: () => productService.list({ page: 1, size: 10, search }),
    enabled: show,
  })
  const rows = data?.content ?? []

  const attach = async (product) => {
    setSaving(product.id)
    try {
      const result = await competitorListingService.attach(product.id, {
        marketplace, marketplaceItemId: itemId, decision, region: storefront,
      })
      setDone({ product, result })

      // Attaching something already on that product returns the EXISTING row
      // rather than erroring. Saying "Added" then would be a lie — and if it
      // was previously rejected, it is a listing someone already dismissed. So
      // compare against what we ASKED for: anything else is a pre-existing row.
      const asked = decision || 'CANDIDATE'
      if (result.matchStatus === asked) {
        const where = storefrontLabel({ countryCode: result.storefront })
        notify.success(asked === 'CANDIDATE'
          ? 'Added — review it on the product'
          : `Saved as a competitor${where ? ` (${where})` : ''}`)
      } else {
        notify.info(`Already attached to this product (${String(result.matchStatus).toLowerCase()})`)
      }
    } catch (err) {
      // The backend returns readable messages for 404 and both 400s — an
      // unknown marketplace even names the valid ones. Pass them through.
      // A storefront the connector cannot reach now fails honestly instead of
      // quietly returning the US listing, so its message needs to read as a
      // setup problem rather than a bug.
      notify.error(marketplaceErrorText(err, shopName) || 'Could not attach this listing.')
    } finally {
      setSaving(null)
    }
  }

  return (
    <Modal show={show} onClose={onClose} size="lg" title="Save to a product"
      footer={<Button variant="light" onClick={onClose}>{done ? 'Done' : 'Cancel'}</Button>}>

      {/* Which storefront's copy is being attached. The same ASIN on two
          storefronts is two competitors at two prices, so this is part of what
          is being saved, not decoration. */}
      {storefront && !done && (
        <div className="text-muted small mb-3">
          Attaching the <strong>{shopName}</strong> listing
          {title ? <> — {title}</> : null}
        </div>
      )}

      {done ? (
        <div className="text-center py-3">
          <div className="icon-box icon-grad-success mx-auto mb-3"><FiBookmark /></div>
          {/* Only a status DIFFERENT from the one asked for proves the row
              already existed — the response carries no "created" flag, and
              calling every non-candidate result "already attached" reported a
              successful save as a no-op. */}
          <h6 className="fw-bold mb-1">
            {done.result.matchStatus === (decision || 'CANDIDATE')
              ? (done.result.matchStatus === 'CANDIDATE' ? 'Added as a candidate' : 'Saved as a competitor')
              : `Already attached (${String(done.result.matchStatus).toLowerCase()})`}
          </h6>
          <p className="text-muted small mb-1">
            {done.result.matchStatus === 'CANDIDATE'
              ? 'It needs reviewing before it counts towards price statistics.'
              : done.result.matchStatus === (decision || 'CANDIDATE')
                ? 'Its price is followed from now on.'
                : 'This listing was already on that product with a different status, so nothing changed.'}
          </p>

          {/* Which copy was stored. The same item id can be attached once per
              storefront, so this is the difference between a duplicate and a
              second, legitimate competitor. */}
          <p className="small mb-3">
            <MarketplaceLogo marketplace={done.result.marketplace || marketplace}
              storefront={done.result.storefront} />
            {done.result.landedPrice != null && (
              <span className="ms-2 fw-semibold">
                {formatCurrency(done.result.landedPrice, done.result.currency || 'USD')}
              </span>
            )}
          </p>

          {/* The storefront asked for and the one stored must agree. If they do
              not, the region did not reach the marketplace and the stored price
              is from the wrong country — the exact bug this parameter fixed. */}
          {storefront && done.result.storefront
            && String(done.result.storefront).toUpperCase() !== String(storefront).toUpperCase() && (
            <div className="field-note field-note--warn text-start mb-3" style={{ maxWidth: 'none' }}>
              <FiAlertTriangle size={15} />
              <span>
                You attached the <strong>{shopName}</strong> listing, but the stored competitor is
                from <strong>{storefrontLabel({ countryCode: done.result.storefront })}</strong>.
                Its price is from the wrong storefront — worth reporting.
              </span>
            </div>
          )}

          <Link to={`/products/${done.product.id}`} onClick={onClose}
            className="btn btn-light d-inline-flex align-items-center gap-1">
            Review on {done.product.title} <FiArrowRight />
          </Link>
        </div>
      ) : (
        <>
          <p className="text-muted small">
            Attaching <strong>{title || itemId}</strong> from {marketplace}. Pick the product it
            competes with.
          </p>

          <SearchBar grow value={term} onChange={setTerm} placeholder="Search your products by name, SKU or brand…" />

          <div className="mt-3">
            {isLoading ? <SkeletonRows rows={4} /> : rows.length === 0 ? (
              <EmptyState icon={FiSearch} title="No products match"
                message={search ? 'Try a different name or SKU.' : 'Search for the product this competes with.'} />
            ) : (
              <div className="d-flex flex-column gap-2">
                {rows.map((p) => (
                  <div key={p.id} className="result-card align-items-center">
                    <div className="flex-grow-1" style={{ minWidth: 0 }}>
                      <div className="fw-semibold clamp-2">{p.title}</div>
                      <div className="text-muted small mt-1">
                        {p.sku && <span className="font-monospace me-2">{p.sku}</span>}
                        {p.brand && <span>· {p.brand}</span>}
                      </div>
                    </div>
                    <Button variant="light" icon={FiBookmark} className="flex-shrink-0"
                      loading={saving === p.id} disabled={saving != null}
                      onClick={() => attach(p)}>
                      Attach
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="field-note mt-3" style={{ maxWidth: 'none' }}>
            <FiInfo size={15} />
            <span>
              Saved as a candidate for review, never as a confirmed match. Price and
              availability are read live from the marketplace — nothing here is typed in.
            </span>
          </div>
        </>
      )}
    </Modal>
  )
}
