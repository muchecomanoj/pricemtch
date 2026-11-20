import { useState } from 'react'
import { FiArrowRight, FiArrowLeft } from 'react-icons/fi'
import PricingCard from '../../../components/onboarding/PricingCard'
import SubscriptionSummary from '../../../components/onboarding/SubscriptionSummary'
import Button from '../../../components/common/Button'
import { useNotification } from '../../../context/NotificationContext'
import { onboardingService } from '../../../services/onboardingService'
import { catalogDiscountPercent, isFreePlan } from '../../../utils/plans'
import { Header } from './Header'
import { errorMessage } from '../../../services/apiError'

/* ── Step 8: Review plan ─────────────────────────────────────── */
export default function ReviewPlan({ plan, plans, cycle, token, onChangePlan, onChangeCycle, onNext, onBack, onActivated, onNeedsPayment }) {
  const { notify } = useNotification()
  // With no assigned plan resolved, open straight into the picker — showing an
  // arbitrary plan as "assigned to your organization" would be a lie.
  const [choosing, setChoosing] = useState(!plan)
  const [busy, setBusy] = useState(false)
  const cardCycle = cycle === 'YEARLY' ? 'yearly' : 'monthly'
  const yearlyDiscount = catalogDiscountPercent(plans)
  const free = !!plan && isFreePlan(plan)

  // Free: confirm the plan, then call checkout — on a free plan that is what
  // activates the workspace (the backend answers mode FREE, nothing to pay).
  // Should the server still want payment (the plan list here was out of
  // date), carry on through the normal billing steps instead.
  const activateFree = async () => {
    setBusy(true)
    try {
      await onboardingService.choosePlan(token, plan.code, cycle)
      const res = await onboardingService.checkout(token)
      if (res?.mode === 'FREE') onActivated(res)
      else onNeedsPayment()
    } catch (e) { notify.error(errorMessage(e, 'Could not activate your workspace')) }
    finally { setBusy(false) }
  }
  return (
    <div>
      <Header
        title={plan ? 'Review your plan' : 'Choose your plan'}
        sub={plan
          ? 'This is the plan assigned to your organization. You can upgrade any time.'
          : 'Select the plan that fits your organization. You can upgrade any time.'} />

      {choosing ? (
        <>
          {/* Billing toggle lives with the picker, so plan-card prices switch
              monthly/yearly. The review summary uses the dedicated Billing step
              instead, so no toggle there. */}
          <div className="d-flex justify-content-center mb-4">
            <div className="btn-group">
              {[['MONTHLY', 'Monthly'], ['YEARLY', 'Yearly']].map(([c, label]) => (
                <button key={c} type="button" className={`btn btn-sm ${cycle === c ? 'btn-primary' : 'btn-light'}`}
                  onClick={() => onChangeCycle(c)}>
                  {label}{c === 'YEARLY' && yearlyDiscount > 0 && (
                    <span className="badge text-bg-success ms-2">Save {yearlyDiscount}%</span>
                  )}
                </button>
              ))}
            </div>
          </div>

          <div className="row g-3 mb-3">
            {plans.map((p) => (
              <div className={`col-12 col-sm-6 ${plans.length > 3 ? 'col-xl-3' : 'col-xl-4'}`} key={p.code}>
                <PricingCard plan={p} cycle={cardCycle} selected={p.code === plan?.code}
                  onSelect={(c) => { onChangePlan(c); setChoosing(false) }} />
              </div>
            ))}
          </div>
          <div className="d-flex gap-2">
            <Button variant="light" icon={FiArrowLeft} onClick={onBack}>Back</Button>
            {plan && <Button variant="light" onClick={() => setChoosing(false)}>Cancel</Button>}
          </div>
        </>
      ) : (
        <>
          <SubscriptionSummary plan={plan} cycleCode={cycle} onChangePlan={() => setChoosing(true)} />
          <div className="d-flex justify-content-between mt-3">
            <Button variant="light" icon={FiArrowLeft} onClick={onBack} disabled={busy}>Back</Button>
            {free
              ? <Button loading={busy} onClick={activateFree}>Activate workspace <FiArrowRight /></Button>
              : <Button onClick={onNext}>Choose billing <FiArrowRight /></Button>}
          </div>
        </>
      )}
    </div>
  )
}
