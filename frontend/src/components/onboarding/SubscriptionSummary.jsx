import { FiCheck, FiUsers, FiCloud, FiCpu, FiLifeBuoy, FiGrid } from 'react-icons/fi'
import Card from '../common/Card'
import { formatCurrency } from '../../utils/format'
import { BILLING_CYCLES, TAX_RATE } from '../../mock/onboarding'
import { isFreePlan } from '../../utils/plans'

// Pure pricing math for a plan + cycle. Exported so the payment step reuses it.
// The yearly total and any discount come from the plan's own prices (backend);
// no discount rate is assumed here. Monthly = the list price; yearly = the
// plan's yearly price, with the discount being however much cheaper that is
// than paying monthly for 12 months.
export function priceBreakdown(plan, cycleCode) {
  const cycle = BILLING_CYCLES.find((c) => c.code === cycleCode) || BILLING_CYCLES[0]
  const monthly = plan?.price?.monthly || 0
  const base = +(monthly * cycle.months).toFixed(2)
  const subtotal = cycle.code === 'YEARLY'
    ? +(plan?.price?.yearly ?? base).toFixed(2)
    : base
  const discount = +(base - subtotal).toFixed(2)
  const discountPercent = base > 0 && discount > 0 ? Math.round((discount / base) * 100) : 0
  const tax = +(subtotal * TAX_RATE).toFixed(2)
  const total = +(subtotal + tax).toFixed(2)
  return { cycle, base, discount, discountPercent, subtotal, tax, total }
}

// "What you get" panel for the Review Plan step.
export default function SubscriptionSummary({ plan, cycleCode, onChangePlan }) {
  if (!plan) return null
  const { cycle, subtotal, discount, discountPercent, tax, total } = priceBreakdown(plan, cycleCode || 'MONTHLY')

  // The live plan catalog only carries a seat cap, so storage, AI credits,
  // modules and support usually come back empty. Rendering the label with a
  // blank beside it reads as a broken page rather than an absent quota, so a
  // row without a value is dropped — the same rule PricingCard already uses.
  const rows = [
    { icon: FiUsers, label: 'Users', value: plan.users != null ? `Up to ${plan.users}` : null },
    { icon: FiCloud, label: 'Storage', value: plan.storage },
    { icon: FiCpu, label: 'AI Credits', value: plan.aiCredits },
    { icon: FiGrid, label: 'Modules', value: plan.modules?.length ? plan.modules.join(', ') : null },
    { icon: FiLifeBuoy, label: 'Support', value: plan.support },
  ].filter((r) => r.value !== null && r.value !== undefined && r.value !== '')

  return (
    <div className="row g-3">
      <div className="col-12 col-lg-7">
        <Card title="Your plan" actions={
          onChangePlan && <button className="btn btn-sm btn-light" onClick={onChangePlan}>Change plan</button>
        }>
          <div className="d-flex align-items-center gap-3 mb-3">
            <div className={`icon-box icon-grad-${plan.color}`}><FiCheck /></div>
            <div>
              <div className="h5 mb-0 fw-bold">{plan.name}</div>
              <div className="text-muted small">{plan.tagline}</div>
            </div>
            {plan.recommended && <span className="badge text-bg-primary ms-auto">Recommended</span>}
          </div>
          <hr />
          <div className="row g-3">
            {rows.map((r) => (
              <div className="col-12 col-sm-6" key={r.label}>
                <div className="d-flex align-items-center gap-2">
                  <r.icon className="text-primary" size={15} />
                  <div>
                    <div className="text-muted small">{r.label}</div>
                    <div className="fw-semibold small">{r.value}</div>
                  </div>
                </div>
              </div>
            ))}
          </div>
          <hr />
          <div className="small">
            <div className="fw-semibold mb-2">Included features</div>
            <div className="row g-1">
              {plan.features.map((f) => (
                <div className="col-12 col-sm-6" key={f}>
                  <span className="d-flex align-items-center gap-2 text-muted">
                    <FiCheck className="text-success" size={12} /> {f}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </Card>
      </div>

      <div className="col-12 col-lg-5">
        {/* A free plan is its trial: no cycle, nothing billed. "Free × 12
            months · Billed yearly" read as a free year. */}
        {isFreePlan(plan) ? (
          <Card title="Billing">
            <div className="d-flex justify-content-between align-items-center">
              <span className="fw-bold">Free</span>
              <span className="h5 mb-0 fw-bold">{formatCurrency(0, plan.currency)}</span>
            </div>
            <div className="text-muted small mt-2">
              {plan.trialDays > 0 ? `${plan.trialDays} days, ` : ''}no card needed. Choose a paid plan any time — your data carries over.
            </div>
          </Card>
        ) : (
        <Card title="Billing">
          <Line label={`${plan.name} × ${cycle.months} month${cycle.months > 1 ? 's' : ''}`} value={formatCurrency(subtotal + discount, plan.currency)} />
          {discount > 0 && <Line label={`${cycle.label} discount (${discountPercent}%)`} value={`− ${formatCurrency(discount, plan.currency)}`} success />}
          {tax > 0 && <Line label={`Tax (${Math.round(TAX_RATE * 100)}%)`} value={formatCurrency(tax, plan.currency)} />}
          <hr />
          <div className="d-flex justify-content-between align-items-center">
            <span className="fw-bold">Total</span>
            <span className="h5 mb-0 fw-bold">{formatCurrency(total, plan.currency)}</span>
          </div>
          <div className="text-muted small mt-2">Billed {cycle.label.toLowerCase()} · cancel anytime</div>
        </Card>
        )}
      </div>
    </div>
  )
}

function Line({ label, value, success }) {
  return (
    <div className="d-flex justify-content-between py-1 small">
      <span className="text-muted">{label}</span>
      <span className={`fw-semibold ${success ? 'text-success' : ''}`}>{value}</span>
    </div>
  )
}
