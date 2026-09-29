import ComboInput from '../forms/ComboInput'
import VariantDetailsFields from './VariantDetailsFields'

// The product being created from a marketplace listing, as one form.
//
// Shared by the Add Product wizard and "Add to catalog" on a search result,
// which ask for exactly the same thing and had drifted into two copies of it.
// Grouped into sections because a flat grid of eight fields with helper text
// of different lengths never lines up: each row ended at a different height
// and the eye had nowhere to rest. The sections say what each group is FOR —
// what the product is, how you sell it, which variant, how it is identified.
const CONDITIONS = [['', 'Not set'], ['NEW', 'New'], ['USED', 'Used'], ['REFURBISHED', 'Refurbished']]

const IDENT_LABELS = [
  ['asin', 'ASIN'], ['upc', 'UPC'], ['ean', 'EAN'], ['gtin', 'GTIN'], ['mpn', 'MPN'], ['ebayItemId', 'eBay item'],
]

export default function ProductDraftFields({ form, setForm, ids = {}, brands = [], categories = [], idPrefix }) {
  const set = (patch) => setForm((f) => ({ ...f, ...patch }))
  const found = IDENT_LABELS.filter(([k]) => ids[k])

  return (
    <div className="draft-form">
      <section className="draft-section">
        <h6 className="draft-section__title">Product</h6>
        <div className="row g-3">
          <div className="col-12">
            <label className="form-label" htmlFor={`${idPrefix}-title`}>Title</label>
            <input id={`${idPrefix}-title`} className="form-control" value={form.title}
              onChange={(e) => set({ title: e.target.value })} />
          </div>
          <div className="col-md-4">
            <label className="form-label" htmlFor={`${idPrefix}-sku`}>SKU</label>
            <input id={`${idPrefix}-sku`} className="form-control font-monospace" value={form.sku}
              title="Your own code — suggested from the brand and title"
              onChange={(e) => set({ sku: e.target.value })} />
          </div>
          <div className="col-md-4">
            <label className="form-label" htmlFor={`${idPrefix}-brand`}>Brand</label>
            <ComboInput id={`${idPrefix}-brand`} value={form.brand} options={brands.map((b) => b.name)}
              onChange={(v) => set({ brand: v })} maxLength={150}
              // eBay sends no brand, so the empty case says why in the box
              // itself rather than in a hint that pushes the row out of line.
              placeholder={form.brand ? 'Type or choose…' : 'Not sent by eBay — type or choose'} />
          </div>
          <div className="col-md-4">
            <label className="form-label" htmlFor={`${idPrefix}-category`}>Category</label>
            <ComboInput id={`${idPrefix}-category`} value={form.category}
              options={categories.map((c) => c.name)} onChange={(v) => set({ category: v })}
              maxLength={150} placeholder="Your own — marketplaces send none" />
          </div>
        </div>
      </section>

      <section className="draft-section">
        <h6 className="draft-section__title">Selling</h6>
        <div className="row g-3">
          <div className="col-md-6">
            <label className="form-label" htmlFor={`${idPrefix}-condition`}>Condition</label>
            <select id={`${idPrefix}-condition`} className="form-select" value={form.condition}
              onChange={(e) => set({ condition: e.target.value })}>
              {CONDITIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
          </div>
          <div className="col-md-6">
            <label className="form-label" htmlFor={`${idPrefix}-price`}>
              Your selling price <span className="text-muted fw-normal">(optional)</span>
            </label>
            <input id={`${idPrefix}-price`} type="number" min="0" step="0.01" className="form-control"
              placeholder="0.00" title="The anchor margin and ROI are calculated from"
              value={form.ourPrice} onChange={(e) => set({ ourPrice: e.target.value })} />
          </div>
        </div>
      </section>

      <section className="draft-section">
        <VariantDetailsFields value={form} onChange={setForm} idPrefix={`${idPrefix}-v`} />
      </section>

      {found.length > 0 && (
        <section className="draft-section">
          <h6 className="draft-section__title">
            Identifiers <span className="draft-section__aside">copied from the listing</span>
          </h6>
          <div className="d-flex flex-wrap gap-2">
            {found.map(([k, label]) => (
              <span key={k} className="ident-chip">
                <span className="ident-chip__type">{label}</span>
                <span className="font-monospace">{ids[k]}</span>
              </span>
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
