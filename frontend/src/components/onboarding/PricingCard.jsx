import { motion } from 'framer-motion'
import { FiCheck, FiZap, FiShield, FiBriefcase, FiCloud, FiUsers, FiCpu } from 'react-icons/fi'
import { formatCurrency } from '../../utils/format'

const ICONS = { rocket: FiCloud, zap: FiZap, briefcase: FiBriefcase, shield: FiShield }

// Selectable plan card with gradient icon, price, feature list and badges.
// `billingHint` adds when a paid plan is charged, so nobody signing up expects
// a trial that paid plans no longer have.
export default function PricingCard({ plan, cycle = 'monthly', selected, onSelect, compact, billingHint }) {
  const Icon = ICONS[plan.icon] || FiZap
  const price = plan.price?.[cycle] ?? plan.price?.monthly
  const perLabel = cycle === 'yearly' ? '/year' : '/month'
  // A free trial is not an annual subscription that costs nothing — showing
  // "$0.00 /year" implies a recurring plan and hides that it expires.
  const isFree = !Number(price)

  return (
    <motion.div
      className={`plan-card ${selected ? 'selected' : ''}`}
      onClick={() => onSelect?.(plan.code)}
      whileHover={{ y: -4 }}
      transition={{ type: 'spring', stiffness: 300, damping: 22 }}
      role="button"
    >
      {plan.recommended && <span className="plan-badge">★ Recommended</span>}
      {selected && (
        <span className="badge text-bg-primary position-absolute" style={{ top: 12, right: 12 }}>
          <FiCheck size={11} /> Selected
        </span>
      )}

      <div className={`icon-box icon-grad-${plan.color} mb-3`}><Icon /></div>
      <h6 className="fw-bold mb-1">{plan.name}</h6>
      <div className="text-muted small mb-3" style={{ minHeight: 34 }}>{plan.tagline}</div>

      <div className="d-flex align-items-baseline gap-1 mb-3">
        {isFree ? (
          <>
            <span className="plan-price">Free</span>
            {plan.trialDays > 0 && (
              <span className="text-muted small">for {plan.trialDays} days</span>
            )}
          </>
        ) : (
          <>
            <span className="plan-price">{formatCurrency(price, plan.currency)}</span>
            <span className="text-muted small">{perLabel}</span>
          </>
        )}
      </div>
      {billingHint && !isFree && (
        <div className="text-muted small mb-3" style={{ marginTop: -8 }}>
          {plan.trialDays > 0 ? `${plan.trialDays}-day free trial, then billed` : 'Billed today'}
        </div>
      )}

      {/* Quotas — the live plan catalog only carries a seat cap, so anything
          the API doesn't send is omitted rather than rendered as "undefined". */}
      <div className="d-flex flex-column gap-1 mb-3 small">
        {plan.users != null && <Quota icon={FiUsers} label={`${plan.users} users`} />}
        {plan.storage && <Quota icon={FiCloud} label={`${plan.storage} storage`} />}
        {plan.aiCredits && <Quota icon={FiCpu} label={`${plan.aiCredits} AI credits`} />}
      </div>

      {!compact && (
        <ul className="list-unstyled mb-0 small">
          {plan.features.map((f) => (
            <li key={f} className="d-flex align-items-start gap-2 py-1">
              <FiCheck className="text-success flex-shrink-0 mt-1" size={13} />
              <span className="text-muted">{f}</span>
            </li>
          ))}
        </ul>
      )}
    </motion.div>
  )
}

function Quota({ icon: Icon, label }) {
  return (
    <span className="d-flex align-items-center gap-2 text-muted">
      <Icon size={13} className="text-primary" /> {label}
    </span>
  )
}
