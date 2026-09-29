import { useRef, useState } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { useQuery } from '@tanstack/react-query'
import { FiInfo, FiAlertTriangle } from 'react-icons/fi'
import Button from '../common/Button'
import ImageInput from './ImageInput'
import ComboInput from './ComboInput'
import AsinSuggestModal from './AsinSuggestModal'
import { productService } from '../../services/productService'
import { identifierValidator } from '../../utils/identifiers'
import { brandStore, categoryStore } from '../../services/localMasterData'

// Product master form — FRD FR-PROD-003:
//   "canonical brand, title, identifiers, MPN, category, variant attributes,
//    pack quantity, dimensions, weight, images, and lifecycle status"
// Price and margin are deliberately absent: FR-COST-001 puts cost inputs on the
// cost profile (PUT /products/{id}/cost-profile) and FR-PROFIT-001 makes margin
// a calculated, audited output — never a typed value. The API has no field for
// either, which matches.

// Marks a mandatory field. Title and SKU are required by the API itself
// (CreateProductRequest.required).
const Req = () => <span className="text-danger ms-1" aria-hidden="true">*</span>

// The API's own rule, mirrored here so it is caught before the call: a product
// needs at least one identifier a marketplace can be SEARCHED by. MPN is not
// one of them — no marketplace looks a part number up — so it is deliberately
// absent from this list even though the product master stores it.
const hasAnyIdentifier = (v = {}) =>
  ['asin', 'upc', 'ean', 'gtin', 'ebayItemId'].some((f) => v[f]?.trim())

// Section heading inside the grid.
function Section({ title, first = false }) {
  return (
    <div className="col-12">
      <div className={`form-label mb-0 ${first ? '' : 'pt-3 border-top-soft'}`}>{title}</div>
    </div>
  )
}

const STATUSES = ['ACTIVE', 'INACTIVE', 'DRAFT', 'ARCHIVED']
// CreateProductRequest.condition enum.
const CONDITIONS = ['NEW', 'USED', 'REFURBISHED']

// Identifier rules live in utils/identifiers — FRD §4.2 — so this form and
// Marketplace Search agree on what a valid barcode is. Length alone used to be
// checked here, with check digits deferred to the backend; that stopped being
// tenable once the frontend could call a chargeable marketplace lookup itself.

export default function ProductForm({ defaultValues = {}, onSubmit, onCancel, submitting }) {
  const { register, handleSubmit, control, setValue, watch, formState: { errors } } = useForm({ defaultValues })

  // "At least one identifier" is a rule about the SECTION, so it is checked on
  // submit and reported on the section. Attached to the ASIN box, it painted
  // ASIN red and read as "ASIN is required" — the exact rule it replaced.
  // Recomputed live, so the warning clears the moment any identifier is typed.
  const [idAttempted, setIdAttempted] = useState(false)
  const idValues = watch(['asin', 'upc', 'ean', 'gtin', 'ebayItemId'])
  const missingId = idAttempted
    && !hasAnyIdentifier({ asin: idValues[0], upc: idValues[1], ean: idValues[2], gtin: idValues[3], ebayItemId: idValues[4] })
  const idNoteRef = useRef(null)

  const submit = handleSubmit((values) => {
    if (!hasAnyIdentifier(values)) {
      setIdAttempted(true)
      idNoteRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      return
    }
    onSubmit(values)
  })
  // Amber, not red: nothing typed is wrong — something is missing.
  const idWarn = (name) => (missingId && name !== 'mpn' ? 'field-needs' : '')
  const isEdit = !!defaultValues.id

  // Master Data drives the brand/category suggestions.
  const { data: brands = [] } = useQuery({
    queryKey: ['masterData', 'brands'], queryFn: () => brandStore.list(),
  })
  const { data: categories = [] } = useQuery({
    queryKey: ['masterData', 'categories'], queryFn: () => categoryStore.list(),
  })

  const [findingAsin, setFindingAsin] = useState(false)

  const err = (name) => (errors[name] ? 'is-invalid' : '')
  const msg = (name) => errors[name] && <div className="invalid-feedback">{errors[name].message}</div>

  return (
    <form onSubmit={submit} className="row g-3" noValidate>
      {/* Fills the field only — the user still presses Save. Writing straight
          to the catalogue would recreate the problem this exists to fix. */}
      <AsinSuggestModal
        show={findingAsin}
        onClose={() => setFindingAsin(false)}
        fetcher={() => productService.suggestAsin(defaultValues.id)}
        subject={defaultValues.title}
        onUse={(asin) => setValue('asin', asin, { shouldValidate: true, shouldDirty: true })}
      />

      {/* Everything required, together and first. Title and SKU are required
          by the API; at least one searchable identifier is required by the
          API's product rule. They used to be split across the top and the
          middle of the form, so the one rule most often broken sat below a
          screen of optional fields and was only found when Save failed. */}
      <Section title="Required" first />

      {/* Title and SKU on one row: the two fields the API itself requires. */}
      <div className="col-md-8">
        <label className="form-label">Title<Req /></label>
        <input className={`form-control ${err('title')}`}
          {...register('title', {
            required: 'Title is required.',
            maxLength: { value: 500, message: 'Title cannot exceed 500 characters.' },
          })} />
        {msg('title')}
      </div>

      <div className="col-md-4">
        <label className="form-label">SKU{!isEdit && <Req />}</label>
        <input className={`form-control ${err('sku')}`} readOnly={isEdit}
          {...register('sku', isEdit ? {} : {
            required: 'SKU is required.',
            maxLength: { value: 100, message: 'SKU cannot exceed 100 characters.' },
          })} />
        {msg('sku')}
        {isEdit && <div className="form-text">Cannot be changed after creation.</div>}
      </div>

      <Section title={<>Identifiers <span className="text-muted fw-normal">— at least one</span></>} />

      {/* At least one identifier, not specifically an ASIN.
          CreateProductRequest asks only for sku and title; the rule exists so
          no product is unsearchable. An ASIN was the wrong way to state it —
          eBay has no ASINs, so an eBay-only seller could not add a product at
          all. Any identifier the search can key on satisfies it. */}
      <div className="col-12" ref={idNoteRef}>
        <div className={`field-note ${missingId ? 'field-note--warn' : ''}`} style={{ maxWidth: 'none' }}
          role={missingId ? 'alert' : undefined}>
          {missingId ? <FiAlertTriangle size={15} /> : <FiInfo size={15} />}
          <span>
            {missingId && <strong>Add at least one identifier before saving. </strong>}
            Any one of these is enough — an ASIN, an eBay item number, or a barcode (UPC, EAN or
            GTIN). An MPN on its own is not: no marketplace can look one up.
          </span>
        </div>
      </div>
      <div className="col-md-4">
        <div className="d-flex align-items-center justify-content-between gap-2">
          <label className="form-label mb-0">ASIN</label>
          {/* Only on edit: suggest-asin is keyed on a product that exists.
              This is where the 17 fabricated ASINs get cleaned up. */}
          {isEdit && (
            <button type="button" className="btn btn-sm btn-link p-0 text-decoration-none"
              onClick={() => setFindingAsin(true)}>Find ASIN</button>
          )}
        </div>
        <input className={`form-control mt-1 ${err('asin')} ${idWarn('asin')}`}
          {...register('asin', {
            // Runs only once something is typed; trims so spaces aren't accepted.
            validate: identifierValidator('ASIN'),
          })} />
        {msg('asin')}
      </div>
      {/* eBay's own listing number, stored as MARKETPLACE_ID. Unvalidated on
          purpose: it arrives bare (365412789012) or composite (v1|365…|0), and
          a format check would reject one of the two shapes people paste. */}
      <div className="col-md-4">
        <label className="form-label">eBay item number</label>
        <input className={`form-control ${idWarn('ebayItemId')}`} maxLength={100} {...register('ebayItemId')} />
      </div>
      <div className="col-md-4">
        <label className="form-label">MPN</label>
        <input className="form-control" maxLength={200} {...register('mpn')} />
      </div>
      <div className="col-md-4">
        <label className="form-label">EAN</label>
        <input className={`form-control ${err('ean')} ${idWarn('ean')}`} inputMode="numeric"
          {...register('ean', { validate: identifierValidator('EAN') })} />
        {msg('ean')}
      </div>
      <div className="col-md-4">
        <label className="form-label">UPC</label>
        <input className={`form-control ${err('upc')} ${idWarn('upc')}`} inputMode="numeric"
          {...register('upc', { validate: identifierValidator('UPC') })} />
        {msg('upc')}
      </div>
      <div className="col-md-4">
        <label className="form-label">GTIN</label>
        <input className={`form-control ${err('gtin')} ${idWarn('gtin')}`} inputMode="numeric"
          {...register('gtin', { validate: identifierValidator('GTIN') })} />
        {msg('gtin')}
      </div>

      <Section title={<>Details <span className="text-muted fw-normal">(optional)</span></>} />

      {/* Suggestions come from Master Data, but the field stays free text —
          brand and category are plain strings on the API, and forcing a picker
          would block a genuinely new value mid-entry. */}
      <div className="col-md-6">
        <label className="form-label" htmlFor="p-brand">Brand</label>
        <Controller name="brand" control={control}
          render={({ field }) => (
            <ComboInput id="p-brand" value={field.value || ''} onChange={field.onChange}
              options={brands.map((b) => b.name)} maxLength={150} placeholder="Type or choose…" />
          )} />
      </div>
      <div className="col-md-6">
        <label className="form-label" htmlFor="p-category">Category</label>
        <Controller name="category" control={control}
          render={({ field }) => (
            <ComboInput id="p-category" value={field.value || ''} onChange={field.onChange}
              options={categories.map((c) => c.name)} maxLength={150} placeholder="Type or choose…" />
          )} />
      </div>

      <div className="col-12">
        <label className="form-label">Description</label>
        <textarea className={`form-control ${err('description')}`} rows={2} maxLength={2000}
          {...register('description', {
            maxLength: { value: 2000, message: 'Description cannot exceed 2000 characters.' },
          })} />
        {msg('description')}
      </div>

      {/* Four to a row, so the image sits level with the fields beside it
          instead of dropping onto a row of its own. */}
      <div className="col-6 col-md-3">
        {/* Lifecycle status — FR-PROD-003. Blank => let the backend default. */}
        <label className="form-label">Status</label>
        <select className="form-select" {...register('status')}>
          <option value="">Default</option>
          {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>
      <div className="col-6 col-md-3">
        {/* Item condition. Sets what a competitor listing is compared against —
            a used listing is not a like-for-like match for a new product. */}
        <label className="form-label">Condition</label>
        <select className="form-select" {...register('condition')}>
          <option value="">Default</option>
          {CONDITIONS.map((c) => <option key={c} value={c}>{c[0] + c.slice(1).toLowerCase()}</option>)}
        </select>
      </div>
      <div className="col-6 col-md-3">
        {/* Our current selling price. NOT a cost — costs live on the cost
            profile, and margin/ROI are calculated from the two together.
            Profitability falls back to this when no ?price= is supplied. */}
        <label className="form-label">Our selling price</label>
        <input type="number" min="0" step="0.01" className="form-control" {...register('ourPrice')} />
        <div className="form-text">Used as the anchor for margin and ROI.</div>
      </div>
      <div className="col-6 col-md-3">
        <label className="form-label">Product image</label>
        <Controller name="imageUrl" control={control}
          render={({ field }) => (
            <ImageInput value={field.value} onChange={field.onChange} maxDim={512} height={86} />
          )} />
      </div>

      <Section title="Variant attributes" />

      <div className="col-md-4">
        <label className="form-label">Colour</label>
        <input className="form-control" {...register('color')} />
      </div>
      <div className="col-md-4">
        <label className="form-label">Size</label>
        <input className="form-control" {...register('size')} />
      </div>
      <div className="col-md-4">
        <label className="form-label">Material</label>
        <input className="form-control" {...register('material')} />
      </div>
      <div className="col-md-4">
        <label className="form-label">Model</label>
        <input className="form-control" {...register('model')} />
      </div>
      <div className="col-md-4">
        <label className="form-label">Variant</label>
        <input className="form-control" {...register('variant')} />
      </div>
      <div className="col-md-4">
        <label className="form-label">Country</label>
        <input className="form-control" {...register('country')} />
      </div>

      <Section title="Packaging & dimensions" />

      <div className="col-md-6">
        <label className="form-label">Pack quantity</label>
        <input type="number" min="1" step="1" className="form-control" {...register('packQuantity')} />
      </div>
      {/* The API stores these as bare numbers with no unit field. Backend has
          standardised on metric by convention: weight kg, dimensions cm.
          If per-product units are added later (weightUnit/dimensionUnit), these
          labels must become dynamic. */}
      <div className="col-md-6">
        <label className="form-label">Weight (kg)</label>
        <input type="number" min="0" step="0.01" className="form-control" {...register('weight')} />
      </div>
      <div className="col-md-4">
        <label className="form-label">Length (cm)</label>
        <input type="number" min="0" step="0.01" className="form-control" {...register('length')} />
      </div>
      <div className="col-md-4">
        <label className="form-label">Width (cm)</label>
        <input type="number" min="0" step="0.01" className="form-control" {...register('width')} />
      </div>
      <div className="col-md-4">
        <label className="form-label">Height (cm)</label>
        <input type="number" min="0" step="0.01" className="form-control" {...register('height')} />
      </div>

      <div className="col-12 d-flex justify-content-end gap-2 mt-2 pt-3 border-top-soft">
        <Button variant="light" type="button" onClick={onCancel}>Cancel</Button>
        <Button type="submit" loading={submitting}>Save Product</Button>
      </div>
    </form>
  )
}
