import { FiCheck, FiMail, FiShield, FiUsers, FiCloud, FiCpu } from 'react-icons/fi'

// Soft info panel shown beside the short-step cards so they fill the width
// instead of floating in a wide empty band. Copy adapts to the current step.
export default function OnboardAside({ stepKey, invite, plan }) {
  const COPY = {
    welcome: { icon: FiCheck, title: 'Welcome to Price Intelligence', text: 'Let’s get your workspace ready — it only takes a few minutes.' },
    otp: { icon: FiMail, title: 'Verify it’s really you', text: 'We’ve emailed a 6-digit code to keep your account secure.' },
    password: { icon: FiShield, title: 'Secure your account', text: 'Choose a strong password. You can enable 2-factor auth later in settings.' },
  }
  const c = COPY[stepKey] || COPY.welcome
  const Icon = c.icon

  return (
    <aside className="ob-split-aside">
      <div className="position-relative">
        <div className="d-flex align-items-center gap-2 mb-4">
          <div className="d-grid rounded-3 fw-bold" style={{ width: 36, height: 36, placeItems: 'center', background: 'rgba(255,255,255,0.16)' }}>PI</div>
          <span className="fw-semibold" style={{ fontFamily: 'Plus Jakarta Sans' }}>{invite.companyName}</span>
        </div>

        <div className="icon-box mb-3" style={{ background: 'rgba(255,255,255,0.16)' }}><Icon /></div>
        <h4 className="fw-bold mb-2" style={{ fontFamily: 'Plus Jakarta Sans' }}>{c.title}</h4>
        <p className="mb-4" style={{ color: 'rgba(255,255,255,0.82)' }}>{c.text}</p>
      </div>

      {/* Plan quotas — reassurance about what they're getting */}
      <div className="mt-auto position-relative">
        {/* Only shown once a plan is known — quotas the API doesn't send are skipped. */}
        {plan && (
          <div className="rounded-3 p-3" style={{ background: 'rgba(255,255,255,0.1)' }}>
            <div className="d-flex justify-content-between align-items-center mb-2">
              <span className="small" style={{ color: 'rgba(255,255,255,0.75)' }}>Your plan</span>
              <span className="badge" style={{ background: 'rgba(255,255,255,0.22)' }}>{plan.name}</span>
            </div>
            {[
              plan.users != null && [FiUsers, `${plan.users} users`],
              plan.storage && [FiCloud, `${plan.storage} storage`],
              plan.aiCredits && [FiCpu, `${plan.aiCredits} AI credits`],
            ].filter(Boolean).map(([I, label]) => (
              <div key={label} className="d-flex align-items-center gap-2 py-1 small">
                <I size={14} style={{ opacity: 0.85 }} /> {label}
              </div>
            ))}
          </div>
        )}
        <div className="d-flex align-items-center gap-2 mt-3 small" style={{ color: 'rgba(255,255,255,0.7)' }}>
          <FiShield size={13} /> Secured with 256-bit encryption
        </div>
      </div>
    </aside>
  )
}
