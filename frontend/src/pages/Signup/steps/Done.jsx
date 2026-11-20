import { FiMail, FiCheck } from 'react-icons/fi'
import SuccessScreen from '../../../components/onboarding/SuccessScreen'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'

/* ── Step 6: Done + welcome email ────────────────────────────── */
// Uses the final SelfRegisterResponse — or, on a free plan, the checkout's
// { mode: FREE, trialDays, note }, since that call is what created the account.
// The welcome email is sent server-side either way.
export default function Done({ email, plan, free, result, onGo }) {
  const a = result || {}
  const days = a.trialDays ?? plan.trialDays
  const rows = [
    ['Plan', a.subscriptionPlan || plan.name],
    ['Company code', a.companyCode],
    ['Status', a.subscriptionStatus],
    ['Free for', free && days > 0 ? `${days} days` : null],
    ['Trial ends', a.trialEndDate],
    ['Renews', free ? null : a.subscriptionEndDate],
  ].filter(([, v]) => v)

  return (
    <div className="ob-narrow">
      <SuccessScreen title={free ? 'You’re all set 🎉' : 'Welcome aboard 🎉'}
        message={free
          ? (a.note || 'No payment needed on the Free plan — the account is ready. Sign in to start.')
          : `Your ${a.subscriptionPlan || plan.name} workspace is ready.`}>
        <div className="d-flex justify-content-center mb-4">
          <Button icon={FiCheck} onClick={onGo}>Go to sign in</Button>
        </div>
      </SuccessScreen>

      <div className="d-flex align-items-center gap-3 p-3 rounded-3 mt-2"
        style={{ background: 'rgba(34,197,94,0.06)', border: '1px solid rgba(34,197,94,0.25)' }}>
        <div className="icon-box icon-grad-success flex-shrink-0"><FiMail /></div>
        <div>
          <div className="fw-semibold">A welcome email is on its way</div>
          <div className="text-muted small">
            {free ? 'Your login details were' : 'Your receipt and login details were'} emailed to <strong>{email}</strong>.
          </div>
        </div>
      </div>

      {rows.length > 0 && (
        <Card title="Subscription details" className="mt-3">
          <div className="row g-2 small">
            {rows.map(([k, v]) => (
              <div className="col-12 d-flex justify-content-between border-bottom py-1" key={k}>
                <span className="text-muted">{k}</span><span className="fw-semibold">{v}</span>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}
