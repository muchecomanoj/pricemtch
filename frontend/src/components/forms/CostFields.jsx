// The cost inputs shared by BOTH cost forms:
//   • Cost Management      → PUT /cost-model            (tenant defaults)
//   • Product Costs & Profit → PUT /products/{id}/cost-profile
//
// The two payloads are field-for-field identical (the product one adds validFrom
// and note), which is what makes the tenant→product fallback a genuine cascade.
// Keeping the markup in one place means they cannot drift apart.

// Flat per-unit amounts, in the profile's currency.
export const UNIT_COSTS = [
  ['cogs', 'COGS'],
  ['inboundFreight', 'Inbound freight'],
  ['duty', 'Duty'],
  ['prep', 'Prep'],
  ['fulfilmentFee', 'Fulfilment fee'],
  ['storage', 'Storage'],
  ['advertising', 'Advertising'],
  ['overheadAllocation', 'Overhead allocation'],
  ['outboundShipping', 'Outbound shipping'],
]

// Percentage-of-price rates.
// How the tax rate relates to the listed price. The default is US sales tax:
// added at checkout, collected on the seller's behalf, never part of profit.
// That is wrong wherever the price already includes the tax — a UK price of
// £120 including 20% VAT earns £100, not £120, so margin, break-even and every
// recommendation for a VAT market were overstated by the whole VAT rate.
//
// NONE is deliberately distinct from a zero rate somebody forgot to fill in.
export const TAX_TREATMENTS = [
  ['PASS_THROUGH', 'Added at checkout — the listed price is what we receive (US sales tax)'],
  ['INCLUSIVE', 'Already in the price — VAT / GST. We receive the price less the tax'],
  ['NONE', 'No sales tax applies'],
]

export const RATES = [
  ['referralRatePct', 'Referral / commission %'],
  ['paymentFeeRatePct', 'Payment fee %'],
  ['returnsAllowancePct', 'Returns allowance %'],
  ['taxRatePct', 'Tax %'],
]

export default function CostFields({ register, disabled = false, showMeta = false }) {
  return (
    <>
      <div className="col-md-4">
        <label className="form-label">Currency</label>
        <input className="form-control" maxLength={3} placeholder="USD"
          disabled={disabled} {...register('currency')} />
      </div>
      {showMeta && (
        <div className="col-md-8">
          <label className="form-label">These costs apply from</label>
          <input type="date" className="form-control" disabled={disabled} {...register('validFrom')} />
          {/* A product now has one profile per effective date. Without this, a
              margin approved last quarter is recomputed with this quarter's
              freight and can no longer be explained. */}
          <div className="form-text">
            Saving against a date that already has a profile corrects that version. A new date adds
            one and leaves the earlier costs intact. A future date is allowed and does not affect
            today&apos;s figures.
          </div>
        </div>
      )}

      <div className="col-12">
        <div className="form-label mb-0 pt-2 border-top-soft">Per-unit costs</div>
      </div>
      {UNIT_COSTS.map(([name, label]) => (
        <div className="col-6 col-md-4" key={name}>
          <label className="form-label" htmlFor={`cost-${name}`}>{label}</label>
          <input id={`cost-${name}`} type="number" min="0" step="0.01" className="form-control"
            disabled={disabled} {...register(name)} />
        </div>
      ))}

      <div className="col-12">
        <div className="form-label mb-0 pt-2 border-top-soft">Rates (% of price)</div>
      </div>
      <div className="col-12">
        <label className="form-label" htmlFor="cost-taxTreatment">Tax treatment</label>
        <select id="cost-taxTreatment" className="form-select"
          disabled={disabled} {...register('taxTreatment')}>
          {TAX_TREATMENTS.map(([v, label]) => <option key={v} value={v}>{label}</option>)}
        </select>
        <div className="form-text">
          Decides whether the tax rate below comes out of what we receive. The same price gives very
          different margins under the two treatments.
        </div>
      </div>

      {RATES.map(([name, label]) => (
        <div className="col-6 col-md-3" key={name}>
          <label className="form-label" htmlFor={`cost-${name}`}>{label}</label>
          <input id={`cost-${name}`} type="number" min="0" step="0.01" className="form-control"
            disabled={disabled} {...register(name)} />
        </div>
      ))}

      {showMeta && (
        <div className="col-12">
          <label className="form-label">Note</label>
          <input className="form-control" disabled={disabled} {...register('note')} />
        </div>
      )}
    </>
  )
}

// Shared payload builder — an untouched number input yields '', which the API
// rejects; send undefined instead.
const num = (n) => {
  if (n === '' || n == null) return undefined
  const v = Number(n)
  return Number.isFinite(v) ? v : undefined
}

export function toCostPayload(values, { includeMeta = false } = {}) {
  const out = {
    currency: values.currency?.trim() || 'USD',
    // Always sent: an omitted treatment silently falls back to pass-through,
    // which is the one value that can overstate a margin.
    taxTreatment: values.taxTreatment || 'PASS_THROUGH',
  }
  for (const [name] of [...UNIT_COSTS, ...RATES]) out[name] = num(values[name])
  if (includeMeta) {
    if (values.validFrom) out.validFrom = values.validFrom
    if (values.note?.trim()) out.note = values.note.trim()
  }
  return Object.fromEntries(Object.entries(out).filter(([, v]) => v !== undefined))
}
