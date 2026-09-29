import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FiPackage, FiCheckCircle, FiAlertTriangle, FiArrowRight } from 'react-icons/fi'
import Modal from '../common/Modal'
import Button from '../common/Button'
import MarketplaceLogo from '../common/MarketplaceLogo'
import ProductDraftFields from './ProductDraftFields'
import { productService } from '../../services/productService'
import { competitorListingService } from '../../services/competitorListingService'
import { useNotification } from '../../context/NotificationContext'
import { brandStore, categoryStore } from '../../services/localMasterData'
import {
  conditionOf, identifiersOf, suggestSku, primaryIdentifier, brandOf,
} from '../../utils/listingToProduct'
import { formatCurrency } from '../../utils/format'

// "This listing is a product I sell" — straight from a search result.
//
// The other button on the card, Save as competitor, answers the opposite
// question: somebody else selling something I already stock. Both start from
// the same listing, so they sit together, but they must never be one button —
// attaching your own product to itself as a competitor would price you against
// your own listing.
//
// Two calls, because the API has no single "create from listing": POST
// /products with the identifiers the listing carries, then the attach. The
// second is optional but ticked, since a product with nothing to compare
// against produces no statistics at all.
export default function SaveAsProductModal({ show, onClose, listing, decision }) {
  const qc = useQueryClient()
  const { notify } = useNotification()

  const [form, setForm] = useState({
    title: '', sku: '', brand: '', category: '', condition: '', ourPrice: '', color: '', size: '', model: '', packQuantity: '',
  })
  // Suggestions from Master Data, as the manual product form uses. Free text
  // still: brand and category are plain strings on the API, and forcing a
  // picker would block a genuinely new value mid-entry.
  const { data: brands = [] } = useQuery({
    queryKey: ['masterData', 'brands'], queryFn: () => brandStore.list(),
  })
  const { data: categories = [] } = useQuery({
    queryKey: ['masterData', 'categories'], queryFn: () => categoryStore.list(),
  })

  const [track, setTrack] = useState(true)
  const [done, setDone] = useState(null)

  const ids = identifiersOf(listing)
  const primary = primaryIdentifier(ids)

  // Re-seeded each time it opens: the card behind it may be a different
  // listing from the last one saved.
  useEffect(() => {
    if (!show || !listing) return
    setDone(null)
    setTrack(true)
    setForm({
      title: listing.title || '',
      sku: suggestSku(listing),
      brand: brandOf(listing),
      category: '',
      condition: conditionOf(listing.condition),
      ourPrice: '',
      color: '', size: '', model: '', packQuantity: '',
    })
  }, [show, listing])

  // An exact identifier lookup, not a text search: GET /products takes
  // `identifier`, so a product already holding this ASIN or barcode is found
  // reliably rather than by hoping its title contains the digits. Catching the
  // duplicate here is far cheaper than merging two product records later.
  const dupQ = useQuery({
    queryKey: ['products', 'dupCheck', primary],
    queryFn: () => productService.list({ identifier: primary, size: 5 }),
    enabled: show && !!primary,
  })
  const duplicate = (dupQ.data?.content ?? []).find((p) => [
    ['asin', ids.asin], ['upc', ids.upc], ['ean', ids.ean], ['gtin', ids.gtin],
  ].some(([f, v]) => v && String(p[f] || '').toUpperCase() === String(v).toUpperCase()))

  const create = useMutation({
    mutationFn: async () => {
      const product = await productService.create({
        sku: form.sku.trim(),
        title: form.title.trim(),
        brand: form.brand.trim() || undefined,
        category: form.category.trim() || undefined,
        condition: form.condition || undefined,
        ourPrice: form.ourPrice === '' ? undefined : Number(form.ourPrice),
        // Variant facts the match judge compares; blanks are dropped by the
        // request mapper, so an unopened section sends nothing.
        color: form.color, size: form.size, model: form.model, packQuantity: form.packQuantity,
        ...ids,
      })
      let attached = false
      if (track && listing?.marketplaceItemId) {
        try {
          await competitorListingService.attach(product.id, {
            marketplace: listing.marketplace,
            marketplaceItemId: listing.marketplaceItemId,
            // Where it was found, so a Canadian listing is stored as Canadian.
            region: listing.storefront,
            decision,
          })
          attached = true
        } catch {
          // The product exists by now, and losing it because the attach was
          // refused — a pack-size or condition rule — would be the worse
          // outcome. Reported on the result panel instead.
        }
      }
      return { product, attached }
    },
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['products'] })
      setDone(res)
      notify.success('Added to your catalog')
    },
    onError: (e) => notify.error(e?.message || 'The product could not be created.'),
  })

  const valid = form.title.trim() && form.sku.trim()

  return (
    <Modal show={show} onClose={onClose} size="lg" scrollable
      title={done ? 'Added to your catalog' : 'Add to your catalog'}
      footer={done ? (
        <Button onClick={onClose}>Done</Button>
      ) : (
        <>
          <Button variant="light" onClick={onClose}>Cancel</Button>
          <Button icon={FiPackage} loading={create.isPending} disabled={!valid}
            onClick={() => create.mutate()}>Add to catalog</Button>
        </>
      )}>

      {done ? (
        <div className="text-center py-3">
          <div className="icon-box icon-grad-success mx-auto mb-3"><FiCheckCircle /></div>
          <h6 className="fw-bold mb-1">{done.product.title}</h6>
          <p className="text-muted small mb-3">
            {done.attached
              ? 'Added, with this listing tracked as a competitor from now on.'
              : track
                ? 'Added. The listing could not be tracked as a competitor — usually a pack-size or condition rule. Add it from the product page to see why.'
                : 'Added. Nothing is being compared against it yet.'}
          </p>
          <Link to={`/products/${done.product.id}`} onClick={onClose}
            className="btn btn-light d-inline-flex align-items-center gap-1">
            Open the product <FiArrowRight />
          </Link>
          <div className="mt-2">
            <Link to={`/products/${done.product.id}`} state={{ edit: true }} onClick={onClose}
              className="small">
              Add more details — weight, dimensions, description
            </Link>
          </div>
        </div>
      ) : (
        <div className="d-flex flex-column gap-3">
          {/* Which listing this is being built from, so the fields below read
              as "taken from this" rather than appearing from nowhere. */}
          <div className="draft-source">
            <MarketplaceLogo marketplace={listing?.marketplace} storefront={listing?.storefront} />
            <span className="text-truncate">{listing?.title}</span>
            {listing?.price != null && (
              <span className="fw-semibold ms-auto flex-shrink-0">
                {formatCurrency(listing.price, listing.currency || 'USD')}
              </span>
            )}
          </div>

          {duplicate && (
            <div className="field-note field-note--warn" style={{ maxWidth: 'none' }}>
              <FiAlertTriangle size={15} />
              <span>
                <strong>{duplicate.title}</strong> already has this identifier.{' '}
                <Link to={`/products/${duplicate.id}`} onClick={onClose}>Open it</Link> and save
                this listing as a competitor instead of creating a second product.
              </span>
            </div>
          )}

          <ProductDraftFields form={form} setForm={setForm} ids={ids}
            brands={brands} categories={categories} idPrefix="sap" />

          {/* Ticked by default: a product with nothing attached produces no
              market statistics, no margin and no recommendations. */}
          <label className="draft-panel draft-panel__head mb-0" htmlFor="sap-track"
            style={{ cursor: 'pointer' }}>
            <input className="form-check-input mt-0" type="checkbox" id="sap-track"
              checked={track} onChange={(e) => setTrack(e.target.checked)} />
            <span>Also track this listing as a competitor</span>
            <span className="draft-panel__hint">Its price is recorded from now on</span>
          </label>
        </div>
      )}
    </Modal>
  )
}
