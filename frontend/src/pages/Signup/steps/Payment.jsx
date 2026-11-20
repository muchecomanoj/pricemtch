import { useState } from 'react'
import { FiArrowLeft, FiCreditCard, FiShield } from 'react-icons/fi'
import { priceBreakdown } from '../../../components/onboarding/SubscriptionSummary'
import { Row } from '../../../components/onboarding/StepFields'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'
import { useNotification } from '../../../context/NotificationContext'
import { signupService } from '../../../services/signupService'
import { formatCurrency } from '../../../utils/format'
import { Header } from './Header'
import { errorMessage } from '../../../services/apiError'

/* ── Step 5: Payment (hosted checkout) ───────────────────────── */
export default function Payment({ plan, cycle, token, onPaid, onBack }) {
  const { notify } = useNotification()
  const [busy, setBusy] = useState(false)
  const [session, setSession] = useState(null) // CheckoutResponse (mock mode)
  const b = priceBreakdown(plan, cycle)
  const amount = session?.amount ?? b.total
  const currency = session?.currency ?? plan.currency
  // Paid plans carry no trial now, but the catalog is editable in Plans admin,
  // so read the real length rather than assume either way.
  const trialDays = plan.trialDays > 0 ? plan.trialDays : 0

  const startCheckout = async () => {
    setBusy(true)
    try {
      const res = await signupService.checkout(token)
      if (res?.checkoutUrl) { window.location.assign(res.checkoutUrl); return } // real provider → redirect
      // FREE: nothing was due and the account already exists (the plan list
      // was stale, say). Straight to Done — mock-confirm would find no payment.
      if (res?.mode === 'FREE') { onPaid(res); return }
      setSession(res) // mock provider → confirm button
    } catch (e) { notify.error(errorMessage(e, 'Could not start checkout')) }
    finally { setBusy(false) }
  }

  const confirmMock = async () => {
    setBusy(true)
    try {
      const result = await signupService.mockConfirm(token) // SelfRegisterResponse { completed, loginUrl }
      onPaid(result)
    } catch (e) { notify.error(errorMessage(e, 'Payment failed')) }
    finally { setBusy(false) }
  }

  return (
    <div>
      <Header title="Start your subscription"
        sub={trialDays
          ? `${trialDays}-day free trial — you won’t be charged until it ends.`
          : 'You’ll pay on a secure payment page, and your plan starts straight away.'} />
      <div className="row g-3 justify-content-center">
        <div className="col-12 col-lg-6">
          <Card title="Order summary">
            <Row label={plan.name} value={formatCurrency(b.base, plan.currency)} />
            <Row label={`${b.cycle.label} billing`} value={b.cycle.months > 1 ? `${b.cycle.months} months` : '1 month'} muted />
            {b.discount > 0 && <Row label={`Cycle discount (${b.discountPercent}%)`} value={`− ${formatCurrency(b.discount, plan.currency)}`} success />}
            <hr />
            <div className="d-flex justify-content-between align-items-center mb-1">
              <span className="fw-bold">{trialDays ? 'Due after trial' : 'Due today'}</span>
              <span className="h4 mb-0 fw-bold">{formatCurrency(amount, currency)}</span>
            </div>
            {trialDays ? (
              <div className="text-muted mb-3" style={{ fontSize: 11 }}>
                {formatCurrency(0, currency)} due today · {trialDays}-day free trial
              </div>
            ) : <div className="mb-3" />}

            {!session ? (
              <Button className="w-100 justify-content-center py-2" icon={FiCreditCard} loading={busy} onClick={startCheckout}>
                Proceed to secure checkout
              </Button>
            ) : (
              <>
                <div className="alert alert-info py-2 small mb-3">{session.note || 'Test mode — no real charge. Confirm to start your plan.'}</div>
                <Button className="w-100 justify-content-center py-2" loading={busy} onClick={confirmMock}>
                  {trialDays ? 'Start free trial' : 'Confirm payment'}
                </Button>
              </>
            )}
            <div className="d-flex align-items-center justify-content-center gap-2 mt-3 text-muted" style={{ fontSize: 12 }}>
              <FiShield className="text-success" /> Card details are entered on the provider’s secure page
            </div>
          </Card>
          <Button variant="light" className="mt-3" icon={FiArrowLeft} onClick={onBack}>Back</Button>
        </div>
      </div>
    </div>
  )
}
