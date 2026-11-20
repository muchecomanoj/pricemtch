import { useState } from 'react'
import { FiArrowLeft, FiCreditCard, FiShield } from 'react-icons/fi'
import { priceBreakdown } from '../../../components/onboarding/SubscriptionSummary'
import { Row } from '../../../components/onboarding/StepFields'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'
import { useNotification } from '../../../context/NotificationContext'
import { onboardingService } from '../../../services/onboardingService'
import { formatCurrency } from '../../../utils/format'
import { Header } from './Header'
import { errorMessage } from '../../../services/apiError'

/* ── Step 10: Payment (hosted checkout) ──────────────────────── */
// Backend uses a hosted-checkout model: request a session, then either redirect
// to the provider (real) or confirm via mock-confirm (test mode). Card details
// are NEVER entered here — they live on the provider's PCI-compliant page.
export default function Payment({ plan, cycle, token, onPaid, onBack }) {
  const { notify } = useNotification()
  const [busy, setBusy] = useState(false)
  const [session, setSession] = useState(null) // CheckoutResponse when in mock mode
  const b = priceBreakdown(plan, cycle)

  const startCheckout = async () => {
    setBusy(true)
    try {
      const res = await onboardingService.checkout(token)
      if (res?.checkoutUrl) { window.location.assign(res.checkoutUrl); return } // real provider → redirect
      // FREE: nothing to pay, and the backend has already activated the
      // account — mock-confirm would find no payment to confirm.
      if (res?.mode === 'FREE') { onPaid(res); return }
      setSession(res) // mock provider → show confirm button
    } catch (e) { notify.error(errorMessage(e, 'Could not start checkout')) }
    finally { setBusy(false) }
  }

  const confirmMock = async () => {
    setBusy(true)
    try {
      const activation = await onboardingService.mockConfirm(token)
      onPaid(activation)
    } catch (e) { notify.error(errorMessage(e, 'Payment failed')) }
    finally { setBusy(false) }
  }

  const amount = session?.amount ?? b.total
  const currency = session?.currency ?? plan?.currency

  return (
    <div>
      <Header title="Review & pay" sub="Secure checkout powered by our payment provider." />
      <div className="row g-3 justify-content-center">
        <div className="col-12 col-lg-6">
          <Card title="Order summary">
            <Row label={plan?.name} value={formatCurrency(b.base, plan?.currency)} />
            <Row label={`${b.cycle.label} billing`} value={b.cycle.months > 1 ? `${b.cycle.months} months` : '1 month'} muted />
            {b.discount > 0 && <Row label={`Cycle discount (${b.discountPercent}%)`} value={`− ${formatCurrency(b.discount, plan?.currency)}`} success />}
            {b.tax > 0 && <Row label="Tax" value={formatCurrency(b.tax, plan?.currency)} />}
            <hr />
            <div className="d-flex justify-content-between align-items-center mb-1">
              <span className="fw-bold">Total due</span>
              <span className="h4 mb-0 fw-bold">{formatCurrency(amount, currency)}</span>
            </div>
            {/* Before checkout this is an estimate; the checkout session returns the authoritative amount. */}
            {!session && <div className="text-muted mb-3" style={{ fontSize: 11 }}>Final amount confirmed at checkout.</div>}
            {session && <div className="mb-3" />}

            {!session ? (
              <Button className="w-100 justify-content-center py-2" icon={FiCreditCard} loading={busy} onClick={startCheckout}>
                Proceed to secure checkout
              </Button>
            ) : (
              <>
                <div className="alert alert-info py-2 small mb-3">
                  {session.note || 'Test mode — no real charge. Confirm to activate your subscription.'}
                </div>
                <Button className="w-100 justify-content-center py-2" loading={busy} onClick={confirmMock}>
                  Confirm payment
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
