import { motion } from 'framer-motion'
import { FiArrowRight, FiClock } from 'react-icons/fi'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'

/* ── Step 4: Welcome ─────────────────────────────────────────── */
export default function Welcome({ invite, plan, onNext }) {
  const expires = new Date(invite.expiresAt)
  return (
    <Card className="text-center p-2">
      <motion.div className="mx-auto mb-3 d-grid rounded-4 text-white fw-bold"
        initial={{ scale: 0.7, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}
        style={{ width: 72, height: 72, placeItems: 'center', background: 'var(--grad-primary)', fontSize: 24 }}>
        {invite.companyName?.[0] || 'A'}
      </motion.div>
      <h3 className="fw-bold mb-1">Welcome, {invite.companyName}</h3>
      <p className="text-muted mb-4">
        You’ve been invited to set up your workspace. This takes about 3 minutes.
      </p>

      <div className="row g-2 text-start mb-4">
        <Detail label="Company code" value={invite.companyCode} />
        <Detail label="Contact" value={invite.contactPerson} />
        <Detail label="Email" value={invite.email} />
        {plan && <Detail label="Assigned plan" value={plan.name} badge={plan.color} />}
      </div>

      <div className="alert alert-warning py-2 small d-inline-flex align-items-center gap-2">
        <FiClock /> Invitation expires {expires.toLocaleDateString()} at {expires.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
      </div>

      <Button className="w-100 justify-content-center py-2 mt-2" icon={FiArrowRight} onClick={onNext}>
        Continue
      </Button>
    </Card>
  )
}

function Detail({ label, value, badge }) {
  return (
    <div className="col-6">
      <div className="p-2 rounded-3" style={{ background: 'var(--app-bg)' }}>
        <div className="text-muted" style={{ fontSize: 11 }}>{label}</div>
        {badge
          ? <span className={`badge text-bg-${badge}`}>{value}</span>
          : <div className="fw-semibold small text-truncate">{value}</div>}
      </div>
    </div>
  )
}
