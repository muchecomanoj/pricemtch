import { useState } from 'react'
import { FiChevronDown, FiChevronUp, FiLayers } from 'react-icons/fi'

// The four product facts the match judge compares, and nothing else.
//
// A product created from a search result used to arrive with none of these,
// and the judge can only flag a difference it has two values for: "Different
// size: 11.5 vs 11" needs an 11.5 on OUR side. With no size stored, a 10.5 and
// an 11.5 both look like a match, and both walk into the price statistics.
// Pack quantity is the same story for multipacks.
//
// A collapsed panel rather than a link and a paragraph: most products have no
// variants, so it stays out of the way, but when it is needed it reads as one
// contained thing with a clear edge. Opened automatically when something is
// already filled, so nothing typed is ever hidden. Weight, dimensions and
// description stay on the full edit form — they affect fees, not matching.
const FIELDS = ['color', 'size', 'model', 'packQuantity']

export default function VariantDetailsFields({ value, onChange, idPrefix = 'vd' }) {
  const filledCount = FIELDS.filter((k) => String(value?.[k] ?? '').trim()).length
  const [open, setOpen] = useState(filledCount > 0)
  const set = (patch) => onChange({ ...value, ...patch })

  return (
    <div className="draft-panel">
      <button type="button" className="draft-panel__head" aria-expanded={open}
        onClick={() => setOpen((v) => !v)}>
        <FiLayers size={15} className="text-muted" />
        <span>Variant &amp; pack details</span>
        <span className="draft-panel__badge">{filledCount ? `${filledCount} set` : 'Optional'}</span>
        <span className="draft-panel__hint">Tells a size 11 from an 11.5</span>
        {open ? <FiChevronUp size={15} /> : <FiChevronDown size={15} />}
      </button>

      {open && (
        <div className="draft-panel__body">
          <div className="text-muted small mb-3">
            Worth filling for shoes, clothing, colours and multipacks — matching compares these, so
            a listing in the wrong size or pack is caught instead of counted.
          </div>
          <div className="row g-3">
            <div className="col-6 col-md-3">
              <label className="form-label" htmlFor={`${idPrefix}-color`}>Colour</label>
              <input id={`${idPrefix}-color`} className="form-control" value={value?.color || ''}
                placeholder="Black" onChange={(e) => set({ color: e.target.value })} />
            </div>
            <div className="col-6 col-md-3">
              <label className="form-label" htmlFor={`${idPrefix}-size`}>Size</label>
              <input id={`${idPrefix}-size`} className="form-control" value={value?.size || ''}
                placeholder="11.5" onChange={(e) => set({ size: e.target.value })} />
            </div>
            <div className="col-6 col-md-3">
              <label className="form-label" htmlFor={`${idPrefix}-model`}>Model</label>
              <input id={`${idPrefix}-model`} className="form-control" value={value?.model || ''}
                placeholder="NJ100GR" onChange={(e) => set({ model: e.target.value })} />
            </div>
            <div className="col-6 col-md-3">
              <label className="form-label" htmlFor={`${idPrefix}-pack`}>Pack quantity</label>
              <input id={`${idPrefix}-pack`} type="number" min="1" step="1" className="form-control"
                value={value?.packQuantity ?? ''} placeholder="1"
                onChange={(e) => set({ packQuantity: e.target.value })} />
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
