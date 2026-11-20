import { FiClock, FiCheck } from 'react-icons/fi'
import SuccessScreen from '../../../components/onboarding/SuccessScreen'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'

/* ── Expired / invalid link ──────────────────────────────────── */
export function ExpiredLink({ onBack }) {
  return (
    <div className="ob-shell">
      <div className="ob-main">
        <div className="ob-body">
          <Card className="ob-narrow text-center p-4">
            <div className="icon-box icon-grad-warning mx-auto mb-3"><FiClock size={22} /></div>
            <h4 className="fw-bold mb-2">Invitation expired</h4>
            <p className="text-muted">This invitation link is invalid or has already been used. Ask your administrator to resend it.</p>
            <Button onClick={onBack}>Go to sign in</Button>
          </Card>
        </div>
      </div>
    </div>
  )
}

// Shown when the hosted (Stripe) checkout redirects back with ?paid=1. The
// backend webhook activates the subscription server-side, so there's nothing
// more for the client to do here except sign in.
export function PaidReturn({ onGo }) {
  return (
    <div className="ob-shell">
      <div className="ob-main">
        <div className="ob-body ob-center">
          <Card className="ob-narrow text-center p-4">
            <SuccessScreen
              title="Payment successful 🎉"
              message="Your subscription is being activated. You can sign in to your workspace now.">
              <Button icon={FiCheck} onClick={onGo}>Go to sign in</Button>
            </SuccessScreen>
          </Card>
        </div>
      </div>
    </div>
  )
}
