import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { FiCreditCard, FiAlertTriangle, FiArrowRight, FiRefreshCw } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Loader from '../../components/common/Loader'
import SuccessScreen from '../../components/onboarding/SuccessScreen'
import { paymentService } from '../../services/paymentService'
import { useAuth } from '../../context/AuthContext'

// PUBLIC route — no auth guard. The client returns here from Stripe after paying
// to upgrade/downgrade; its login token may be gone, so we must never make an
// authenticated call. We read session_id from the URL and confirm the payment
// via the public endpoint (never 403s), then show the success/retry screen.
//   /billing/success?session_id=cs_test_...
//   /billing/cancel
export default function BillingReturn() {
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()
  const { isAuthenticated, loading: authLoading, refreshUser } = useAuth()
  const isCancel = /\/billing\/cancel/.test(location.pathname)
  const sessionId = params.get('session_id')

  // The upgrade usually keeps the client signed in (the token survives the Stripe
  // round-trip). Prefer the reactive auth state; while it's still validating the
  // token, fall back to the stored token so we don't flash the wrong label. Only
  // route to /login when the session was genuinely lost.
  const hasSession = isAuthenticated || (authLoading && !!localStorage.getItem('token'))
  const goOn = () => navigate(hasSession ? '/my-subscription' : '/login')

  // status: confirming | success | notdone | error
  const [status, setStatus] = useState(isCancel ? 'cancel' : 'confirming')
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (isCancel) return
    let alive = true

    if (!sessionId) {
      // No session id to confirm. The backend reconciler still auto-completes any
      // paid session within ~60s, so treat this as a soft success rather than an error.
      setStatus('success')
      setMessage('Your payment is being processed. Your new plan will be active shortly.')
      if (localStorage.getItem('token')) refreshUser().catch(() => {})
      return
    }

    ;(async () => {
      try {
        const res = await paymentService.confirm(sessionId)
        if (!alive) return
        if (res?.success) {
          setStatus('success')
          setMessage(res.message || 'Your new plan is now active.')
          // The account's access level lives on the session user. Without a
          // re-read the read-only banner would outlive the payment that
          // ended it, until the next full reload. Only with a session: this is
          // a public page and must never make an authenticated call without one.
          if (localStorage.getItem('token')) refreshUser().catch(() => {})
        } else {
          // e.g. "Payment is not completed yet."
          setStatus('notdone')
          setMessage(res?.message || 'Payment is not completed yet.')
        }
      } catch (e) {
        if (!alive) return
        setStatus('error')
        setMessage(e.message || 'Could not confirm payment. Please refresh in a moment.')
      }
    })()

    return () => { alive = false }
  }, [isCancel, sessionId, refreshUser])

  const retry = () => { setStatus('confirming'); setMessage(''); window.location.reload() }

  return (
    <div className="d-flex align-items-center justify-content-center" style={{ minHeight: '100vh', padding: 24, background: 'var(--app-bg)' }}>
      <div style={{ maxWidth: 480, width: '100%' }}>
      <Card className="text-center p-4">
        {status === 'confirming' && (
          <div className="py-4">
            <Loader label="Confirming your payment…" />
            <p className="text-muted small mt-3 mb-0">This only takes a moment.</p>
          </div>
        )}

        {status === 'success' && (
          <SuccessScreen title="Payment successful 🎉" message={message}>
            <Button icon={FiArrowRight} onClick={goOn}>
              {hasSession ? 'View my subscription' : 'Continue to sign in'}
            </Button>
            {!hasSession && (
              <p className="text-muted small mt-3 mb-0">Sign in again if your session expired during checkout.</p>
            )}
          </SuccessScreen>
        )}

        {status === 'notdone' && (
          <>
            <div className="icon-box icon-grad-warning mx-auto mb-3"><FiCreditCard size={22} /></div>
            <h4 className="fw-bold mb-2">Payment not completed</h4>
            <p className="text-muted mb-4">{message}</p>
            <div className="d-flex gap-2 justify-content-center">
              <Button variant="light" onClick={goOn}>{hasSession ? 'Back to subscription' : 'Back to sign in'}</Button>
              <Button icon={FiRefreshCw} onClick={retry}>Check again</Button>
            </div>
          </>
        )}

        {status === 'error' && (
          <>
            <div className="icon-box icon-grad-warning mx-auto mb-3"><FiAlertTriangle size={22} /></div>
            <h4 className="fw-bold mb-2">Couldn&apos;t confirm just yet</h4>
            <p className="text-muted mb-4">{message} Your payment is safe — it will finish automatically within a minute.</p>
            <div className="d-flex gap-2 justify-content-center">
              <Button variant="light" onClick={goOn}>{hasSession ? 'Back to subscription' : 'Back to sign in'}</Button>
              <Button icon={FiRefreshCw} onClick={retry}>Try again</Button>
            </div>
          </>
        )}

        {status === 'cancel' && (
          <>
            <div className="icon-box icon-grad-warning mx-auto mb-3"><FiCreditCard size={22} /></div>
            <h4 className="fw-bold mb-2">Checkout canceled</h4>
            <p className="text-muted mb-4">Your payment was not completed and you have not been charged. Your plan is unchanged — you can try again anytime from your subscription page.</p>
            <div className="d-flex gap-2 justify-content-center">
              <Button variant="light" onClick={() => navigate(hasSession ? '/dashboard' : '/login')}>{hasSession ? 'Back to dashboard' : 'Back to sign in'}</Button>
              <Button onClick={() => navigate('/my-subscription')}>Go to subscription</Button>
            </div>
          </>
        )}
      </Card>
      </div>
    </div>
  )
}
