import { Link } from 'react-router-dom'
import { FiArrowRight } from 'react-icons/fi'
import PricingCard from '../../../components/onboarding/PricingCard'
import Button from '../../../components/common/Button'
import { catalogDiscountPercent, isFreePlan } from '../../../utils/plans'
import { Header } from './Header'

/* ── Step 1: Choose plan (landing) ───────────────────────────── */
export default function ChoosePlan({ plans, loading, selected, cycle, onPlan, onCycle, onNext }) {
  // Only the free plan has a trial now; paid plans are charged from day one.
  const freeDays = plans.find((p) => isFreePlan(p) && p.trialDays > 0)?.trialDays
  const yearlyDiscount = catalogDiscountPercent(plans)
  return (
    <div>
      <Header title="Choose the plan that fits your business"
        sub={freeDays
          ? `Try free for ${freeDays} days. Paid plans start as soon as you subscribe.`
          : 'Paid plans start as soon as you subscribe.'} />

      {/* The billing cycle makes no difference to a free plan. Hidden, not
          removed, so the space doesn't jump as plans are clicked. */}
      <div className="d-flex justify-content-center mb-4"
        style={{ visibility: !loading && isFreePlan(selected) ? 'hidden' : 'visible' }}>
        <div className="btn-group">
          {[['MONTHLY', 'Monthly'], ['YEARLY', 'Yearly']].map(([c, label]) => (
            <button key={c} className={`btn btn-sm ${cycle === c ? 'btn-primary' : 'btn-light'}`} onClick={() => onCycle(c)}>
              {label}{c === 'YEARLY' && yearlyDiscount > 0 && (
                <span className="badge text-bg-success ms-2">Save {yearlyDiscount}%</span>
              )}
            </button>
          ))}
        </div>
      </div>

      <div className="row g-3 mb-4">
        {loading
          ? Array.from({ length: 4 }).map((_, i) => (
            <div className="col-12 col-sm-6 col-xl-3" key={i}>
              <div className="skeleton" style={{ height: 320 }} />
            </div>
          ))
          : plans.map((p) => (
            <div className={`col-12 col-sm-6 ${plans.length > 3 ? 'col-xl-3' : 'col-xl-4'}`} key={p.code}>
              <PricingCard plan={p} cycle={cycle === 'YEARLY' ? 'yearly' : 'monthly'} billingHint
                selected={selected?.code === p.code} onSelect={onPlan} />
            </div>
          ))}
      </div>

      <div className="d-flex justify-content-between align-items-center">
        <Link to="/login" className="text-decoration-none small">Already have an account? Sign in</Link>
        <Button onClick={onNext} disabled={loading}>Continue with {selected?.name} <FiArrowRight /></Button>
      </div>
    </div>
  )
}
