import { useState } from 'react'
import { FiArrowRight, FiArrowLeft } from 'react-icons/fi'
import OptionCard from '../../../components/onboarding/OptionCard'
import { priceBreakdown } from '../../../components/onboarding/SubscriptionSummary'
import Button from '../../../components/common/Button'
import { useNotification } from '../../../context/NotificationContext'
import { onboardingService } from '../../../services/onboardingService'
import { BILLING_CYCLES } from '../../../mock/onboarding'
import { formatCurrency } from '../../../utils/format'
import { Header } from './Header'
import { errorMessage } from '../../../services/apiError'

/* ── Step 9: Billing cycle ───────────────────────────────────── */
// Confirms plan + billing cycle server-side (choose-plan) before payment.
export default function ChooseBilling({ plan, value, token, onChange, onNext, onBack }) {
  const { notify } = useNotification()
  const [busy, setBusy] = useState(false)

  const proceed = async () => {
    setBusy(true)
    try {
      await onboardingService.choosePlan(token, plan?.code, value) // value is MONTHLY | YEARLY
      onNext()
    } catch (e) { notify.error(errorMessage(e, 'Could not confirm plan')) }
    finally { setBusy(false) }
  }

  return (
    <div>
      <Header title="Choose your billing cycle" sub="Commit longer, save more. Cancel any time." />
      <div className="row g-3 mb-3">
        {BILLING_CYCLES.map((c) => {
          const { total, discount, discountPercent } = priceBreakdown(plan, c.code)
          const renew = new Date(); renew.setMonth(renew.getMonth() + c.months)
          // "Best value" only when yearly is genuinely cheaper than monthly×12.
          return (
            <div className="col-12 col-md-6" key={c.code}>
              <OptionCard
                selected={value === c.code}
                onSelect={() => onChange(c.code)}
                title={c.label}
                subtitle={`Renews ${renew.toLocaleDateString()}`}
                badge={discountPercent > 0 ? `Save ${discountPercent}%` : null}
                right={
                  <>
                    <div className="fw-bold">{formatCurrency(total, plan?.currency)}</div>
                    {discount > 0 && <div className="text-success" style={{ fontSize: 11 }}>save {formatCurrency(discount, plan?.currency)}</div>}
                  </>
                }
              />
            </div>
          )
        })}
      </div>
      <div className="d-flex justify-content-between">
        <Button variant="light" icon={FiArrowLeft} onClick={onBack}>Back</Button>
        <Button loading={busy} onClick={proceed}>Continue to payment <FiArrowRight /></Button>
      </div>
    </div>
  )
}
