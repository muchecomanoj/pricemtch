import { useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  FiArrowLeft, FiCreditCard, FiBarChart2, FiCpu, FiClock, FiUsers,
  FiRotateCw, FiSlash, FiPause, FiPlay, FiDollarSign, FiCheck, FiArrowUp, FiArrowDown,
  FiActivity, FiShield,
} from 'react-icons/fi'
import { normalizePlans, planPeriodLabel } from '../../utils/plans'
import Breadcrumb from '../../components/common/Breadcrumb'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Modal from '../../components/common/Modal'
import Loader from '../../components/common/Loader'
import StatusBadge from '../../components/common/StatusBadge'
import DataTable from '../../components/tables/DataTable'
import { clientService } from '../../services/clientService'
import { planService } from '../../services/planService'
import { useNotification } from '../../context/NotificationContext'
import { formatNumber, formatCurrency, daysUntil, formatDate } from '../../utils/format'

const TABS = [
  { key: 'subscription', label: 'Subscription', icon: FiCreditCard },
  { key: 'payments', label: 'Payments', icon: FiDollarSign },
  { key: 'stats', label: 'Stats', icon: FiBarChart2 },
  { key: 'usage', label: 'Usage', icon: FiCpu },
  { key: 'history', label: 'History', icon: FiClock },
]

// Subscription history actions, in words. EXPIRED and TRIAL_ENDED are new: the
// backend now records when a plan lapses and when a trial rolls over, rather
// than leaving the history silent about the two events that change access.
const HISTORY_ACTION = {
  ASSIGN: 'Plan assigned', ASSIGNED: 'Plan assigned',
  UPGRADE: 'Upgraded', UPGRADED: 'Upgraded',
  DOWNGRADE: 'Downgraded', DOWNGRADED: 'Downgraded',
  EXTEND: 'Extended', EXTENDED: 'Extended',
  RENEW: 'Renewed', RENEWED: 'Renewed',
  CANCEL: 'Cancelled', CANCELLED: 'Cancelled',
  SUSPEND: 'Suspended', SUSPENDED: 'Suspended',
  RESUME: 'Resumed', RESUMED: 'Resumed',
  EXPIRED: 'Expired',
  TRIAL_ENDED: 'Trial ended',
}
// Payment types in words. RENEWAL is new: a company renewing its own plan.
const PAYMENT_TYPE = {
  RENEWAL: 'Renewal', UPGRADE: 'Upgrade', DOWNGRADE: 'Downgrade', SIGNUP: 'Sign-up', NEW: 'New subscription',
}

const historyAction = (a) => HISTORY_ACTION[String(a || '').toUpperCase()]
  || String(a || '—').toLowerCase().replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase())

export default function ClientDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { notify } = useNotification()
  const [tab, setTab] = useState('subscription')

  const { data: client, isLoading } = useQuery({
    queryKey: ['client', id],
    queryFn: () => clientService.get(id),
  })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['client', id] })
    qc.invalidateQueries({ queryKey: ['clients'] })
  }

  const setStatus = async (action) => {
    try { await clientService.setStatus(id, action); notify.success(`Client ${action}d`); refresh() }
    catch (e) { notify.error(e.message || 'Action failed') }
  }

  if (isLoading) return <Loader />
  if (!client) return <Card><p className="text-muted mb-0">Client not found.</p></Card>

  const suspended = !/active/i.test(client.status || '')

  return (
    <>
      <div className="mb-3">
        <Breadcrumb items={[
          { label: 'Home', to: '/dashboard' },
          { label: 'Platform' },
          { label: 'Clients', to: '/platform/clients' },
          { label: client.companyName },
        ]} />
      </div>

      {/* Client hero */}
      <Card className="mb-3">
        <div className="d-flex flex-wrap align-items-center gap-3">
          <div className="d-grid rounded-4 text-white fw-bold flex-shrink-0"
            style={{ width: 56, height: 56, placeItems: 'center', fontSize: '1.15rem', background: heroGrad(client.companyName) }}>
            {heroInitials(client.companyName)}
          </div>
          <div style={{ minWidth: 0 }}>
            <div className="d-flex align-items-center gap-2 flex-wrap">
              <h3 className="fw-bold mb-0" style={{ fontFamily: 'Plus Jakarta Sans' }}>{client.companyName}</h3>
              <StatusBadge status={suspended ? 'warning' : 'success'} label={client.status || '—'} />
            </div>
            <div className="text-muted small text-truncate">
              {client.companyCode}{client.companyEmail ? ` · ${client.companyEmail}` : ''}
            </div>
          </div>
          <div className="ms-auto d-flex gap-2">
            <Button variant="light" icon={FiArrowLeft} onClick={() => navigate('/platform/clients')}>Back</Button>
            {suspended
              ? <Button variant="success" icon={FiPlay} onClick={() => setStatus('resume')}>Resume</Button>
              : <Button variant="light" icon={FiPause} onClick={() => setStatus('suspend')}>Suspend</Button>}
          </div>
        </div>
      </Card>

      {/* Summary strip */}
      <div className="row g-3 mb-4">
        <SummaryTile icon={FiCreditCard} label="Plan" grad={client.subscriptionPlan ? 'primary' : 'warning'}
          value={client.subscriptionPlan || 'Not selected'}
          sub={planPeriodLabel(client)} />
        <SummaryTile icon={FiActivity} label="Subscription" grad="info"
          value={client.subscriptionStatus || 'Awaiting onboarding'} />
        <SummaryTile icon={FiShield} label="Tenant status" grad={suspended ? 'warning' : 'success'}
          value={client.status || '—'} />
        <SummaryTile icon={FiUsers} label="Max users" grad="primary" value={client.maxUsers ?? '—'} />
      </div>

      {/* Tabs */}
      <div className="ob-tabs d-flex flex-wrap gap-1 mb-3">
        {TABS.map((t) => (
          <button key={t.key}
            className={`btn btn-sm d-inline-flex align-items-center gap-2 ${tab === t.key ? 'btn-primary' : 'btn-light'}`}
            onClick={() => setTab(t.key)}>
            <t.icon size={15} /> {t.label}
          </button>
        ))}
      </div>

      {tab === 'subscription' && <SubscriptionPanel client={{ ...client, id }} onDone={refresh} />}
      {tab === 'payments' && <PaymentsPanel id={id} />}
      {tab === 'stats' && <StatsPanel id={id} />}
      {tab === 'usage' && <UsagePanel id={id} />}
      {tab === 'history' && <HistoryPanel id={id} />}
    </>
  )
}

// Deterministic gradient + initials for the client hero avatar.
const HERO_GRADS = [
  'linear-gradient(135deg,#6366f1,#8b5cf6)', 'linear-gradient(135deg,#0ea5e9,#3fa9f5)',
  'linear-gradient(135deg,#10b981,#0d9488)', 'linear-gradient(135deg,#f59e0b,#d97706)',
  'linear-gradient(135deg,#ec4899,#db2777)', 'linear-gradient(135deg,#14b8a6,#0891b2)',
]
const heroGrad = (s = '') => HERO_GRADS[[...s].reduce((a, c) => a + c.charCodeAt(0), 0) % HERO_GRADS.length]
const heroInitials = (name = '') => name.trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase() || '—'

function SummaryTile({ icon: Icon, label, value, sub, grad = 'primary' }) {
  return (
    <div className="col-6 col-xl-3">
      <Card hover className="h-100">
        <div className="d-flex align-items-center gap-3">
          <div className={`icon-box icon-grad-${grad} flex-shrink-0`}>{Icon && <Icon />}</div>
          <div style={{ minWidth: 0 }}>
            <div className="text-muted small">{label}</div>
            <div className="fw-bold text-truncate text-capitalize">{typeof value === 'string' ? value.toLowerCase() : value}</div>
            {sub && <div className="text-muted small text-truncate text-capitalize">{sub}</div>}
          </div>
        </div>
      </Card>
    </div>
  )
}

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
function SubscriptionPanel({ client, onDone }) {
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
    catch (e) { notify.error(e.message || 'Action failed') }
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

function Field({ label, value }) {
  return (
    <div className="col-6 col-md-3">
      <div className="text-muted small">{label}</div>
      <div className="fw-semibold">{value || '—'}</div>
    </div>
  )
}

// ── Stats — GET /admin/clients/{id}/stats ──
function StatsPanel({ id }) {
  const { data, isLoading } = useQuery({ queryKey: ['client', id, 'stats'], queryFn: () => clientService.stats(id) })
  if (isLoading) return <Loader />

  const tiles = [
    { icon: FiUsers, label: 'Total Users', value: data?.totalUsers ?? '—', color: 'primary' },
    { icon: FiUsers, label: 'Active Users', value: data?.activeUsers ?? '—', color: 'success' },
    { icon: FiUsers, label: 'Max Users', value: data?.maxUsers ?? '—', color: 'info' },
  ]
  const seats = data?.maxUsers ? Math.min(100, Math.round(((data.totalUsers || 0) / data.maxUsers) * 100)) : 0

  return (
    <>
      <div className="row g-3 mb-3">
        {tiles.map((t) => (
          <div className="col-12 col-md-4" key={t.label}>
            <Card>
              <div className="d-flex align-items-center gap-3">
                <div className={`icon-box icon-grad-${t.color}`}><t.icon /></div>
                <div><div className="text-muted small">{t.label}</div><div className="h4 mb-0 fw-bold">{t.value}</div></div>
              </div>
            </Card>
          </div>
        ))}
      </div>
      <Card title="Seat utilisation" subtitle={`${data?.totalUsers ?? 0} of ${data?.maxUsers ?? '—'} seats used`}>
        <div className="progress" style={{ height: 10 }}>
          <div className={`progress-bar ${seats > 90 ? 'bg-danger' : ''}`} style={{ width: `${seats}%` }} />
        </div>
        <div className="row g-3 mt-2">
          <Field label="Plan" value={data?.subscriptionPlan} />
          <Field label="Subscription" value={data?.subscriptionStatus} />
          <Field label="Tenant status" value={data?.tenantStatus} />
        </div>
      </Card>
    </>
  )
}

// ── Usage — GET /admin/clients/{id}/usage (AI token usage) ──
function UsagePanel({ id }) {
  const { data, isLoading } = useQuery({ queryKey: ['client', id, 'usage'], queryFn: () => clientService.usage(id) })
  if (isLoading) return <Loader />

  const tiles = [
    { label: 'Prompt Tokens', value: data?.promptTokens, color: 'primary' },
    { label: 'Completion Tokens', value: data?.completionTokens, color: 'info' },
    { label: 'Total Tokens', value: data?.totalTokens, color: 'success' },
  ]

  return (
    <div className="row g-3">
      {tiles.map((t) => (
        <div className="col-12 col-md-4" key={t.label}>
          <Card>
            <div className="d-flex align-items-center gap-3">
              <div className={`icon-box icon-grad-${t.color}`}><FiCpu /></div>
              <div>
                <div className="text-muted small">{t.label}</div>
                <div className="h4 mb-0 fw-bold">{typeof t.value === 'number' ? formatNumber(t.value) : '—'}</div>
              </div>
            </div>
          </Card>
        </div>
      ))}
      <div className="col-12">
        <Card><p className="text-muted small mb-0">AI token consumption for this client, from the AI gateway.</p></Card>
      </div>
    </div>
  )
}

// ── History — GET /admin/clients/{id}/subscription/history ──
// ── Payments — GET /admin/clients/{id}/payments (Stripe records) ──
const PAY_STATUS_TONE = { PAID: 'success', PENDING: 'warning', FAILED: 'danger', CANCELLED: 'secondary' }

function PaymentsPanel({ id }) {
  const { data, isLoading } = useQuery({
    queryKey: ['client', id, 'payments'],
    queryFn: () => clientService.payments(id, { page: 1, size: 20 }),
  })
  const rows = Array.isArray(data) ? data : data?.content || []

  const columns = [
    { key: 'createdAt', header: 'Date', render: (p) => (p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '—') },
    { key: 'planCode', header: 'Plan', render: (p) => p.planCode ? <span className="badge text-bg-primary">{p.planCode}</span> : '—' },
    { key: 'billingCycle', header: 'Cycle', render: (p) => p.billingCycle || '—' },
    { key: 'amount', header: 'Amount', render: (p) => (p.amount != null ? formatCurrency(p.amount, p.currency) : '—') },
    { key: 'type', header: 'Type', render: (p) => PAYMENT_TYPE[String(p.type || '').toUpperCase()] || p.type || '—' },
    { key: 'status', header: 'Status', render: (p) => <StatusBadge status={PAY_STATUS_TONE[p.status] || 'secondary'} label={p.status || '—'} /> },
    { key: 'method', header: 'Method', render: (p) => p.method || '—' },
  ]

  return (
    <Card title="Payments" subtitle="Stripe transactions for this client">
      <DataTable columns={columns} rows={rows} loading={isLoading}
        emptyMessage="No payments yet. Pending payments appear here once the Stripe webhook records them." />
    </Card>
  )
}

function HistoryPanel({ id }) {
  const { data, isLoading } = useQuery({ queryKey: ['client', id, 'history'], queryFn: () => clientService.history(id) })
  const rows = Array.isArray(data) ? data : data?.content || []

  const columns = [
    { key: 'action', header: 'Action', render: (h) => <span className="fw-semibold">{historyAction(h.action)}</span> },
    { key: 'planCode', header: 'Plan', render: (h) => h.planCode ? <span className="badge text-bg-primary">{h.planCode}</span> : '—' },
    { key: 'status', header: 'Status', render: (h) => h.status ? <StatusBadge status={/active/i.test(h.status) ? 'success' : 'secondary'} label={h.status} /> : '—' },
    { key: 'startDate', header: 'Start', render: (h) => h.startDate || '—' },
    { key: 'endDate', header: 'End', render: (h) => h.endDate || '—' },
    { key: 'note', header: 'Note', render: (h) => h.note || '—' },
    { key: 'createdAt', header: 'When', render: (h) => (h.createdAt ? new Date(h.createdAt).toLocaleString() : '—') },
  ]

  return (
    <Card title="Subscription history">
      <DataTable columns={columns} rows={rows} loading={isLoading} emptyMessage="No subscription history yet." />
    </Card>
  )
}
