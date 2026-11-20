import { FiMail, FiCheck } from 'react-icons/fi'
import SuccessScreen from '../../../components/onboarding/SuccessScreen'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'
import { BILLING_CYCLES } from '../../../mock/onboarding'

/* ── Step 11/12: Activated ───────────────────────────────────── */
// Uses ActivationCompleteResponse. The welcome/receipt email is sent
// server-side after payment, so there's no client email call here.
// On a free plan `activation` is the checkout's { mode: FREE, trialDays, note }
// — that call is what activated it — so there is no cycle, renewal or receipt.
export default function Activated({ activation, plan, cycle, free, email, onGo }) {
  const a = activation || {}
  const cyc = BILLING_CYCLES.find((c) => c.code === cycle)?.label || cycle
  const days = a.trialDays ?? plan?.trialDays
  const rows = free ? [
    ['Plan', a.subscriptionPlan || plan?.name],
    ['Free for', days > 0 ? `${days} days` : null],
    ['Trial ends', a.trialEndDate],
  ] : [
    ['Plan', a.subscriptionPlan || plan?.name],
    ['Billing cycle', a.billingCycle || cyc],
    ['Status', a.subscriptionStatus || 'ACTIVE'],
    ['Starts', a.subscriptionStartDate],
    ['Renews', a.subscriptionEndDate],
    ['Trial ends', a.trialEndDate],
  ]
  const shown = rows.filter(([, v]) => v)

  return (
    <div>
      <SuccessScreen
        title={free ? 'You’re all set 🎉' : 'Subscription activated 🎉'}
        message={free
          ? (a.note || `Your ${plan?.name || 'Free'} workspace is ready. Sign in to start.`)
          : `Your ${a.subscriptionPlan || plan?.name} plan is live. Welcome to Price Intelligence!`}
      >
        <div className="d-flex justify-content-center gap-2 mb-4">
          <Button icon={FiCheck} onClick={onGo}>Go to sign in</Button>
        </div>
      </SuccessScreen>

      {/* Server sends a welcome + receipt email — informational note only. */}
      <div className="d-flex align-items-center gap-3 p-3 rounded-3 mt-2"
        style={{ background: 'rgba(34,197,94,0.06)', border: '1px solid rgba(34,197,94,0.25)' }}>
        <div className="icon-box icon-grad-success flex-shrink-0"><FiMail /></div>
        <div>
          <div className="fw-semibold">A welcome email is on its way</div>
          <div className="text-muted small">
            We’ve emailed your {free ? '' : 'receipt and '}login details to <strong>{a.email || email}</strong>.
          </div>
        </div>
      </div>

      {shown.length > 0 && (
        <Card title="Subscription details" className="mt-3">
          <div className="row g-2 small">
            {shown.map(([k, v]) => (
              <div className="col-12 col-sm-6 d-flex justify-content-between border-bottom py-1" key={k}>
                <span className="text-muted">{k}</span>
                <span className="fw-semibold">{v}</span>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}
