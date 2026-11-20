import { useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FiClock, FiRotateCw, FiSlash, FiPlay, FiCheck, FiArrowUp, FiArrowDown } from 'react-icons/fi'
import { normalizePlans } from '../../../utils/plans'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'
import Modal from '../../../components/common/Modal'
import { clientService } from '../../../services/clientService'
import { planService } from '../../../services/planService'
import { useNotification } from '../../../context/NotificationContext'
import { formatCurrency, daysUntil, formatDate } from '../../../utils/format'
import Field from './Field'
import { errorMessage } from '../../../services/apiError'

// Matches the backend ChangeSubscriptionStatusRequest enum exactly.
const SUBSCRIPTION_STATUSES = ['TRIAL', 'ACTIVE', 'SUSPENDED', 'EXPIRED', 'CANCELLED']

// ── Trial period ────────────────────────────────────────────────────────────
// The backend has no separate trial window: a client is on trial while
// subscriptionStatus === 'TRIAL', and subscriptionEndDate is when it lapses.
// So managing a trial = extending that date (POST .../subscription/extend)
// and flipping the status (PATCH .../subscription/status).
function TrialPanel({ client, busy, run }) {
  const [customDays, setCustomDays] = useState(14)
  const [confirmExtend, setConfirmExtend] = useState(false)
  const statusUpper = String(client.subscriptionStatus || '').toUpperCase()
  const onTrial = statusUpper === 'TRIAL'
  const isActive = statusUpper === 'ACTIVE'
  const left = daysUntil(client.subscriptionEndDate)
  const expired = left != null && left < 0

  const tone = !onTrial ? 'secondary' : expired ? 'danger' : left <= 3 ? 'warning' : 'success'
  const headline = !onTrial
    ? 'This client is not on a trial'
    : expired ? `Trial expired ${Math.abs(left)} day${Math.abs(left) === 1 ? '' : 's'} ago`
      : left === 0 ? 'Trial ends today'
        : `${left} day${left === 1 ? '' : 's'} left on trial`

  const doExtend = async () => {
    await run(
      () => clientService.extend(client.id, customDays),
      `Trial extended by ${customDays} day${customDays === 1 ? '' : 's'}`,
    )
    setConfirmExtend(false)
  }

  // Preview the new end date so the confirmation is concrete.
  const newEnd = (() => {
    const base = client.subscriptionEndDate ? new Date(client.subscriptionEndDate) : new Date()
    if (Number.isNaN(base.getTime())) return null
    base.setDate(base.getDate() + customDays)
    return base
  })()

  return (
    <Card title="Trial period" className="h-100">
      <div className="d-flex flex-wrap align-items-center gap-3 mb-3">
        <div className={`icon-box icon-grad-${tone === 'secondary' ? 'info' : tone} flex-shrink-0`}><FiClock /></div>
        <div className="flex-grow-1">
          <div className="d-flex align-items-center gap-2">
            <span className="fw-semibold">{headline}</span>
            <span className={`badge text-bg-${tone}`}>{onTrial ? 'TRIAL' : client.subscriptionStatus || 'UNKNOWN'}</span>
          </div>
          <div className="text-muted small">
            {onTrial
              ? <>Ends {formatDate(client.subscriptionEndDate)} · started {formatDate(client.subscriptionStartDate)}</>
              : <>Put the client on a trial to give them time-limited access without billing.</>}
          </div>
        </div>
      </div>

      <label className="form-label">{onTrial ? 'Extend the trial' : 'Extend validity'}</label>
      <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
        {/* These now SELECT a duration; nothing changes until Extend is confirmed. */}
        {[7, 14, 30].map((d) => (
          <Button key={d} size="sm" variant={customDays === d ? 'primary' : 'light'} onClick={() => setCustomDays(d)}>+{d} days</Button>
        ))}
        <span className="text-muted small mx-1">or</span>
        <input type="number" min={1} className="form-control" style={{ maxWidth: 110 }}
          value={customDays} onChange={(e) => setCustomDays(Math.max(1, +e.target.value || 1))} />
        <Button size="sm" icon={FiRotateCw} disabled={busy} onClick={() => setConfirmExtend(true)}>Extend</Button>
      </div>

      <Modal show={confirmExtend} title="Extend trial" onClose={() => setConfirmExtend(false)}
        footer={(
          <>
            <Button variant="light" onClick={() => setConfirmExtend(false)}>Cancel</Button>
            <Button icon={FiRotateCw} loading={busy} onClick={doExtend}>Extend by {customDays} days</Button>
          </>
        )}>
        <p className="mb-2">
          Extend the {onTrial ? 'trial' : 'subscription'} for <strong>{client.companyName}</strong> by <strong>{customDays} day{customDays === 1 ? '' : 's'}</strong>?
        </p>
        {newEnd && (
          <div className="small text-muted">
            New end date: <strong className="text-body">{formatDate(newEnd)}</strong>
            {client.subscriptionEndDate && <> (was {formatDate(client.subscriptionEndDate)})</>}
          </div>
        )}
      </Modal>

      <hr />
      {/* Actions adapt to status so there's never a dead-end:
          TRIAL   → Convert to active · End trial now
          ACTIVE  → Move to trial
          other   → Start trial · Activate   */}
      <div className="d-flex flex-wrap gap-2">
        {onTrial && (
          <Button size="sm" variant="success" icon={FiPlay} loading={busy}
            onClick={() => run(() => clientService.changeStatus(client.id, 'ACTIVE'), 'Trial converted to a paid subscription')}>
            Convert to active
          </Button>
        )}
        {onTrial && (
          <Button size="sm" variant="danger" icon={FiSlash} loading={busy}
            onClick={() => run(() => clientService.changeStatus(client.id, 'EXPIRED'), 'Trial ended')}>
            End trial now
          </Button>
        )}
        {!onTrial && (
          <Button size="sm" variant="light" icon={FiClock} loading={busy}
            onClick={() => run(() => clientService.changeStatus(client.id, 'TRIAL'), 'Client moved to trial')}>
            Start trial
          </Button>
        )}
        {/* When not on a trial and not already active, allow direct activation. */}
        {!onTrial && !isActive && (
          <Button size="sm" variant="success" icon={FiPlay} loading={busy}
            onClick={() => run(() => clientService.changeStatus(client.id, 'ACTIVE'), 'Client activated')}>
            Activate
          </Button>
        )}
      </div>
    </Card>
  )
}

// ── Subscription (assign/upgrade/downgrade/extend/renew/cancel/status) ──
export default function SubscriptionPanel({ client, onDone }) {
  const { notify } = useNotification()
  const { data: plansRaw } = useQuery({ queryKey: ['plans'], queryFn: planService.list })
  const plans = normalizePlans(plansRaw)
  const currentCode = client.subscriptionPlan || ''
  const currentMonthly = plans.find((p) => p.code === currentCode)?.price?.monthly ?? -1
  const [cycle, setCycle] = useState(client.billingCycle === 'MONTHLY' ? 'MONTHLY' : 'YEARLY')
  const [days, setDays] = useState(30)
  const [status, setStatus] = useState(client.subscriptionStatus || 'ACTIVE')
  const [busy, setBusy] = useState(false)
  const [confirmBillExtend, setConfirmBillExtend] = useState(false)
  // A synchronous guard, not just the busy flag. A double-click lands both
  // clicks before React re-renders, so `busy` is still false for the second
  // one — which is how one Renew became two and a client got 60 days, not 30.
  const inFlight = useRef(false)

  const run = async (fn, msg) => {
    if (inFlight.current) return
    inFlight.current = true
    setBusy(true)
    try { await fn(); notify.success(msg); onDone() }
    catch (e) { notify.error(errorMessage(e, 'Action failed')) }
    finally { inFlight.current = false; setBusy(false) }
  }

  // Super-admin plan changes are ALWAYS direct: instant assign, no payment, no
  // email — whether the target plan is higher or lower. (Client self-service
  // upgrade/downgrade with Stripe lives on the client's own My Subscription page.)
  const assign = (p) => run(
    () => clientService.assignPlan(client.id, p.code, cycle),
    `Assigned ${p.name} (${cycle === 'YEARLY' ? 'yearly' : 'monthly'})`,
  )

  const doBillExtend = async () => {
    await run(() => clientService.extend(client.id, days), `Billing period extended by ${days} day${days === 1 ? '' : 's'}`)
    setConfirmBillExtend(false)
  }

  const billNewEnd = (() => {
    const base = client.subscriptionEndDate ? new Date(client.subscriptionEndDate) : new Date()
    if (Number.isNaN(base.getTime())) return null
    base.setDate(base.getDate() + days)
    return base
  })()

  const scheduled = client.scheduledPlanCode && (
    <div className="alert alert-info py-2 small mb-3">
      Downgrade to <strong>{client.scheduledPlanCode}</strong>
      {client.scheduledBillingCycle && <> (<strong>{client.scheduledBillingCycle === 'YEARLY' ? 'yearly' : 'monthly'}</strong>)</>} scheduled
      {client.scheduledPlanEffectiveDate ? <> for <strong>{formatDate(client.scheduledPlanEffectiveDate)}</strong></> : ' for period end'}.
      The client will receive a pay link then.
    </div>
  )

  return (
    <div className="row g-3">
      <div className="col-12 col-xl-6">
        <TrialPanel client={client} busy={busy} run={run} />
      </div>

      <div className="col-12 col-xl-6">
        <Card title="Billing period & status" className="h-100">
          <label className="form-label">Extend by (days)</label>
          <div className="d-flex gap-2 mb-3">
            <input type="number" min={1} className="form-control" style={{ maxWidth: 130 }} value={days} onChange={(e) => setDays(Math.max(1, +e.target.value || 1))} />
            <Button size="sm" variant="light" icon={FiRotateCw} disabled={busy} onClick={() => setConfirmBillExtend(true)}>Extend</Button>
          </div>

          <Modal show={confirmBillExtend} title="Extend billing period" onClose={() => setConfirmBillExtend(false)}
            footer={(
              <>
                <Button variant="light" onClick={() => setConfirmBillExtend(false)}>Cancel</Button>
                <Button icon={FiRotateCw} loading={busy} onClick={doBillExtend}>Extend by {days} days</Button>
              </>
            )}>
            <p className="mb-2">
              Extend the billing period for <strong>{client.companyName}</strong> by <strong>{days} day{days === 1 ? '' : 's'}</strong>?
            </p>
            {billNewEnd && (
              <div className="small text-muted">
                New end date: <strong className="text-body">{formatDate(billNewEnd)}</strong>
                {client.subscriptionEndDate && <> (was {formatDate(client.subscriptionEndDate)})</>}
              </div>
            )}
          </Modal>

          <label className="form-label">Subscription status</label>
          <div className="d-flex gap-2 mb-3">
            <select className="form-select" style={{ maxWidth: 190 }} value={status} onChange={(e) => setStatus(e.target.value)}>
              {SUBSCRIPTION_STATUSES.map((s) => <option key={s}>{s}</option>)}
            </select>
            <Button size="sm" variant="light" loading={busy} onClick={() => run(() => clientService.changeStatus(client.id, status), 'Status changed')}>Set</Button>
          </div>

          <hr />
          <div className="d-flex gap-2">
            <Button size="sm" variant="light" icon={FiRotateCw} loading={busy} onClick={() => run(() => clientService.renew(client.id), 'Renewed')}>Renew</Button>
            <Button size="sm" variant="danger" icon={FiSlash} loading={busy} onClick={() => run(() => clientService.cancel(client.id), 'Cancelled')}>Cancel subscription</Button>
          </div>
        </Card>
      </div>

      <div className="col-12">
        <Card title="Plan" subtitle="Change the client's plan directly — applies immediately, no payment.">
          {scheduled}

          {/* Billing cycle sent with upgrade/downgrade (MONTHLY | YEARLY, 20% off yearly) */}
          <div className="d-flex justify-content-center mb-4">
            <div className="btn-group">
              {[['MONTHLY', 'Monthly'], ['YEARLY', 'Yearly']].map(([c, label]) => (
                <button key={c} type="button" className={`btn btn-sm ${cycle === c ? 'btn-primary' : 'btn-light'}`}
                  onClick={() => setCycle(c)}>
                  {label}{c === 'YEARLY' && <span className="badge text-bg-success ms-2">Save 20%</span>}
                </button>
              ))}
            </div>
          </div>

          <div className="row g-3">
            {plans.map((p) => {
              const isCurrent = p.code === currentCode
              const direction = p.price.monthly > currentMonthly ? 'up' : 'down'
              const shown = cycle === 'YEARLY' ? p.price.yearly : p.price.monthly
              return (
                <div className="col-12 col-sm-6 col-xl-3" key={p.code}>
                  <div className="h-100 p-3 rounded-3 d-flex flex-column"
                    style={{ background: 'var(--card-bg)', border: `1px solid ${isCurrent ? 'var(--primary)' : 'var(--border-soft)'}` }}>
                    <div className="d-flex align-items-center justify-content-between mb-1">
                      <span className="fw-bold">{p.name}</span>
                      {isCurrent && <span className="badge text-bg-primary">Current</span>}
                    </div>
                    <div className="mb-2">
                      <span className="h4 fw-bold">{shown ? formatCurrency(shown, p.currency) : 'Free'}</span>
                      {shown > 0 && <span className="text-muted small"> {cycle === 'YEARLY' ? '/yr' : '/mo'}</span>}
                    </div>
                    <ul className="list-unstyled small text-muted mb-3">
                      {p.users != null && <li className="d-flex gap-2 py-1"><FiCheck className="text-success flex-shrink-0" /> Up to {p.users} users</li>}
                      {p.features.slice(0, 3).map((f) => (
                        <li key={f} className="d-flex gap-2 py-1"><FiCheck className="text-success flex-shrink-0" /> {f}</li>
                      ))}
                    </ul>
                    <div className="mt-auto d-grid gap-2">
                      {isCurrent ? (
                        <Button size="sm" variant="light" disabled>Current plan</Button>
                      ) : (
                        <Button size="sm" variant={direction === 'up' ? 'primary' : 'light'}
                          icon={direction === 'up' ? FiArrowUp : FiArrowDown}
                          className="justify-content-center" loading={busy} disabled={busy}
                          onClick={() => assign(p)}>
                          Assign {direction === 'up' ? '(upgrade)' : '(downgrade)'}
                        </Button>
                      )}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>

          <div className="form-text mt-2">
            Plan changes apply <strong>immediately</strong> — no payment is taken and no email is sent.
            (Clients pay for their own upgrades from their My Subscription page.)
          </div>
        </Card>
      </div>

      <div className="col-12">
        <Card title="Dates">
          <div className="row g-3">
            <Field label="Start date" value={client.subscriptionStartDate} />
            <Field label="End date" value={client.subscriptionEndDate} />
            <Field label="Currency" value={client.currency} />
            <Field label="Timezone" value={client.timezone} />
          </div>
        </Card>
      </div>
    </div>
  )
}
