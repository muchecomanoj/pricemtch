import { useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FiCreditCard, FiUsers, FiCalendar, FiCheck, FiArrowUp, FiArrowDown, FiDollarSign, FiRefreshCw } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Loader from '../../components/common/Loader'
import StatusBadge from '../../components/common/StatusBadge'
import DataTable from '../../components/tables/DataTable'
import { useAuth } from '../../context/AuthContext'
import { tenantService } from '../../services/tenantService'
import { useNotification } from '../../context/NotificationContext'
import { normalizePlans, isFreePlan, FREE_PLAN_CODE } from '../../utils/plans'
import { formatCurrency, formatDate, daysUntil } from '../../utils/format'

const PAY_STATUS_TONE = { PAID: 'success', PENDING: 'warning', FAILED: 'danger', CANCELLED: 'secondary' }
// Payment types in words. RENEWAL is new, from self-service renewal.
const PAY_TYPE = { RENEWAL: 'Renewal', UPGRADE: 'Upgrade', DOWNGRADE: 'Downgrade', SIGNUP: 'Sign-up', NEW: 'New subscription' }

// Renewing early adds a full period to the current end — no paid days are
// lost; renewing after it ended starts the period today. The confirmation
// says which, because "will I lose the days I paid for?" is the question.
const stillPaid = (endDate) => {
  const end = endDate ? new Date(endDate) : null
  return !!end && end.getTime() > Date.now()
}

// The line under "Paid until". Nothing renews on its own, so the count is
// what tells a customer to act. A free plan can't be renewed, only replaced
// by a paid one, so its nudge says that instead.
function timeLeft(days, free) {
  if (days == null) return null
  const act = free ? 'choose a plan' : 'renew soon'
  if (days < 0) return { text: 'Ended', cls: 'text-danger' }
  if (days === 0) return { text: `Ends today — ${act}`, cls: 'text-warning' }
  const text = `${days} day${days === 1 ? '' : 's'} left`
  return days <= 7 ? { text: `${text} — ${act}`, cls: 'text-warning' } : { text, cls: 'text-muted' }
}

// Tenant self-service — the logged-in company managing its OWN plan.
//   GET  /client/subscription       current plan/status/dates (+ scheduled downgrade)
//   GET  /client/plans              plans to choose from
//   POST /client/subscription/upgrade?planCode   → Stripe pay link (redirect)
//   POST /client/subscription/downgrade?planCode → scheduled for period-end
export default function MySubscription() {
  const { readOnly, account, refreshUser } = useAuth()
  const { notify, confirm } = useNotification()
  const [busy, setBusy] = useState('')   // plan code currently being actioned
  const [cycle, setCycle] = useState('MONTHLY')   // MONTHLY | YEARLY billing choice

  const subQ = useQuery({ queryKey: ['my-subscription'], queryFn: tenantService.subscription })
  const plansQ = useQuery({ queryKey: ['my-plans'], queryFn: tenantService.plans })
  // Seats in use. totalUsers, not activeUsers: the limit counts every user who
  // isn't deleted, deactivated ones included, so it is the number that decides
  // when "Add user" is refused.
  const dashQ = useQuery({ queryKey: ['client-dashboard'], queryFn: tenantService.dashboard })

  const sub = subQ.data
  const plans = normalizePlans(plansQ.data)
  const currentCode = sub?.subscriptionPlan || ''
  const currentPlan = plans.find((p) => p.code === currentCode)
  const currentMonthly = currentPlan?.price?.monthly ?? -1
  // Free can't be renewed (the backend answers 400); upgrading is the way on.
  // No plan at all counts as free, as it does on the backend. A code missing
  // from the list (normalizePlans drops duplicate tiers) is not assumed free.
  const isFree = !currentCode || currentCode === FREE_PLAN_CODE || (!!currentPlan && isFreePlan(currentPlan))

  // A synchronous guard as well as `busy`: a double-click lands both clicks
  // before React re-renders, and on this page that is two payments.
  const inFlight = useRef(false)

  // Renewal is offered when it matters: read-only, in the grace period, or
  // inside the last week of the period. Otherwise the current plan card keeps
  // its plain "Current plan" — nobody needs a pay button three weeks early.
  const endsIn = daysUntil(sub?.subscriptionEndDate)
  const renewDue = readOnly || account?.graceDaysLeft != null || (endsIn != null && endsIn <= 7)

  const renew = async (plan, price) => {
    if (inFlight.current) return
    const cycleLabel = cycle === 'YEARLY' ? 'Yearly' : 'Monthly'
    const ok = await confirm({
      title: `Renew ${plan.name} (${cycleLabel}) for ${price ? formatCurrency(price, plan.currency) : 'free'}?`,
      text: stillPaid(sub?.subscriptionEndDate)
        ? `You'll be taken to a secure payment page. Your new period starts on ${formatDate(sub.subscriptionEndDate)}, so no days are lost.`
        : "You'll be taken to a secure payment page. Your new period starts today.",
      confirmText: 'Renew — pay now',
    })
    if (!ok) return

    inFlight.current = true
    setBusy(plan.code)
    try {
      const res = await tenantService.renew(cycle)
      if (res?.outcome === 'RENEWAL_PENDING_PAYMENT' && res?.checkoutUrl) {
        window.location.assign(res.checkoutUrl)
        return
      }
      // APPLIED: renewed without a payment step. Re-read the account so the
      // banner goes and the buttons unlock without a reload.
      notify.success(res?.message || 'Your plan has been renewed.')
      subQ.refetch()
      refreshUser().catch(() => {})
    } catch (e) {
      // The backend's refusal for a free plan reads well as sent ("…your data
      // is kept either way"), so it is shown as is.
      notify.error(e.message || 'Could not start the renewal')
    } finally {
      inFlight.current = false
      setBusy('')
    }
  }

  const changePlan = async (plan, direction) => {
    if (inFlight.current) return
    const cycleLabel = cycle === 'YEARLY' ? 'Yearly' : 'Monthly'
    // The app's own confirmation dialog, not the browser's.
    const ok = await confirm({
      title: direction === 'up' ? `Upgrade to ${plan.name}?` : `Downgrade to ${plan.name}?`,
      text: direction === 'up'
        ? `${cycleLabel} billing. You will be taken to a secure payment page.`
        : `${cycleLabel} billing. It takes effect at the end of your current billing period.`,
      confirmText: direction === 'up' ? 'Upgrade' : 'Downgrade',
    })
    if (!ok) return

    inFlight.current = true
    setBusy(plan.code)
    try {
      const res = direction === 'up'
        ? await tenantService.upgrade(plan.code, cycle)
        : await tenantService.downgrade(plan.code, cycle)

      // Upgrade → pay now on Stripe. Redirect straight to the hosted checkout.
      if (direction === 'up' && res?.checkoutUrl) {
        window.location.assign(res.checkoutUrl)
        return
      }
      notify.success(res?.message || 'Request submitted')
      subQ.refetch()   // reflect scheduled downgrade; plan badge only flips after payment
    } catch (e) {
      notify.error(e.message || 'Could not change your plan')
    } finally { inFlight.current = false; setBusy('') }
  }

  if (subQ.isLoading) return <Loader />

  const used = dashQ.data?.totalUsers
  const maxUsers = dashQ.data?.maxUsers ?? sub?.maxUsers
  const left = timeLeft(endsIn, isFree)
  const toPlans = () => document.getElementById('change-plan')?.scrollIntoView({ behavior: 'smooth', block: 'start' })

  const tiles = [
    { icon: FiCreditCard, label: 'Plan', value: sub?.subscriptionPlan || '—' },
    {
      icon: FiUsers,
      label: 'Team members',
      // Until the count arrives, the limit alone is still worth showing.
      value: used != null && maxUsers != null ? `${used} of ${maxUsers}` : maxUsers != null ? `Up to ${maxUsers}` : '—',
      extra: used != null && maxUsers > 0 && <SeatsBar used={used} max={maxUsers} onUpgrade={toPlans} />,
    },
    {
      icon: FiCalendar,
      label: isFree ? 'Free until' : 'Paid until',
      value: formatDate(sub?.subscriptionEndDate),
      extra: left && <div className={`small mt-1 ${left.cls}`}>{left.text}</div>,
    },
  ]

  return (
    <>
      <PageHeader title="My Subscription" subtitle="Your current plan and billing period."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Subscription' }]} />

      <div className="row g-3 mb-3">
        {tiles.map((t) => (
          <div className="col-md-4" key={t.label}>
            <Card>
              <div className="d-flex align-items-center gap-3">
                <div className="icon-box icon-grad-primary"><t.icon /></div>
                <div className="flex-grow-1" style={{ minWidth: 0 }}>
                  <div className="text-muted small">{t.label}</div>
                  <div className="h5 mb-0">{t.value}</div>
                  {t.extra}
                </div>
              </div>
            </Card>
          </div>
        ))}
      </div>

      {/* Pending downgrade, if the backend scheduled one */}
      {sub?.scheduledPlanCode && (
        <div className="alert alert-info py-2 small">
          Downgrade to <strong>{sub.scheduledPlanCode}</strong>
          {sub.scheduledBillingCycle && <> (<strong>{sub.scheduledBillingCycle === 'YEARLY' ? 'yearly' : 'monthly'}</strong>)</>} scheduled
          {sub.scheduledPlanEffectiveDate ? <> for <strong>{formatDate(sub.scheduledPlanEffectiveDate)}</strong></> : ' for the end of your billing period'}.
          You&apos;ll receive a pay link then.
        </div>
      )}

      <Card title="Subscription details" className="mb-3">
        <div className="row g-3">
          <div className="col-md-4"><div className="text-muted small">Status</div>
            <StatusBadge status={/active/i.test(sub?.subscriptionStatus) ? 'success' : /trial/i.test(sub?.subscriptionStatus) ? 'info' : 'secondary'} label={sub?.subscriptionStatus || '—'} /></div>
          <div className="col-md-4"><div className="text-muted small">Start date</div><div className="fw-semibold">{formatDate(sub?.subscriptionStartDate)}</div></div>
          <div className="col-md-4"><div className="text-muted small">{isFree ? 'Free until' : 'Paid until'}</div><div className="fw-semibold">{formatDate(sub?.subscriptionEndDate)}</div></div>
        </div>
      </Card>

      {/* Change plan — the seats tile's "upgrade" link scrolls here */}
      <div id="change-plan" style={{ scrollMarginTop: 80 }} />
      <Card title="Change your plan" subtitle="Upgrade to pay now, or downgrade at the end of your billing period." className="mb-3">
        {/* Monthly / Yearly toggle — the chosen cycle is sent to the backend and
            drives the price shown (yearly = monthly × 12 × 0.8). */}
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
        {plansQ.isLoading ? <Loader label="Loading plans…" /> : (
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
                    <div className="mt-auto">
                      {isCurrent && isFree && renewDue ? (
                        // Free can't be renewed. The paid cards beside this
                        // one are the way on, so point there instead.
                        <>
                          <Button size="sm" icon={FiArrowUp} className="w-100 justify-content-center"
                            disabled={!!busy} onClick={toPlans}>
                            Choose a plan
                          </Button>
                          <div className="text-muted small text-center mt-1">
                            {endsIn != null && endsIn < 0 ? 'Free trial ended' : 'Free until'} {formatDate(sub?.subscriptionEndDate)}
                          </div>
                        </>
                      ) : isCurrent && renewDue ? (
                        <>
                          <Button size="sm" icon={FiRefreshCw} className="w-100 justify-content-center"
                            loading={busy === p.code} disabled={!!busy} onClick={() => renew(p, shown)}>
                            Renew — pay now
                          </Button>
                          <div className="text-muted small text-center mt-1">
                            {endsIn != null && endsIn < 0 ? 'Ended' : 'Paid until'} {formatDate(sub?.subscriptionEndDate)}
                          </div>
                        </>
                      ) : isCurrent ? (
                        <Button size="sm" variant="light" className="w-100 justify-content-center" disabled>Current plan</Button>
                      ) : readOnly && direction !== 'up' ? (
                        // Read-only: the way back is paying for a plan, and a
                        // downgrade only takes effect at the end of a period
                        // that has already ended. Upgrade stays.
                        <div className="text-muted small text-center">Renew first to change down</div>
                      ) : (
                        <Button size="sm" className="w-100 justify-content-center"
                          variant={direction === 'up' ? 'primary' : 'light'}
                          icon={direction === 'up' ? FiArrowUp : FiArrowDown}
                          loading={busy === p.code} disabled={!!busy}
                          onClick={() => changePlan(p, direction)}>
                          {direction === 'up' ? 'Upgrade' : 'Downgrade'}
                        </Button>
                      )}
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        )}
        <p className="text-muted small mt-3 mb-0">
          Upgrades are charged immediately on a secure Stripe page — your plan changes once payment completes.
        </p>
      </Card>

      <PaymentHistory />
    </>
  )
}

// Seats used against the plan's limit. Amber from 80%, red when full — and
// when full, the way out, since "Add user" will be refused.
function SeatsBar({ used, max, onUpgrade }) {
  const ratio = used / max
  const tone = ratio >= 1 ? 'bg-danger' : ratio >= 0.8 ? 'bg-warning' : 'bg-primary'
  return (
    <>
      <div className="progress mt-2" style={{ height: 4 }} role="progressbar"
        aria-label="Team members used" aria-valuenow={used} aria-valuemin={0} aria-valuemax={max}>
        <div className={`progress-bar ${tone}`} style={{ width: `${Math.min(100, ratio * 100)}%` }} />
      </div>
      {ratio >= 1 && (
        <div className="small text-danger mt-1">
          Limit reached —{' '}
          <button type="button" className="btn btn-link btn-sm p-0 align-baseline text-danger fw-semibold" onClick={onUpgrade}>
            upgrade to add more
          </button>
        </div>
      )}
    </>
  )
}

// GET /client/payments
function PaymentHistory() {
  const { data, isLoading } = useQuery({
    queryKey: ['my-payments'],
    queryFn: () => tenantService.payments({ page: 1, size: 20 }),
  })
  const rows = Array.isArray(data) ? data : data?.content || []

  const columns = [
    { key: 'createdAt', header: 'Date', render: (p) => (p.createdAt ? new Date(p.createdAt).toLocaleDateString() : '—') },
    { key: 'planCode', header: 'Plan', render: (p) => p.planCode ? <span className="badge text-bg-primary">{p.planCode}</span> : '—' },
    { key: 'billingCycle', header: 'Cycle', render: (p) => p.billingCycle || '—' },
    { key: 'amount', header: 'Amount', render: (p) => (p.amount != null ? formatCurrency(p.amount, p.currency) : '—') },
    { key: 'type', header: 'Type', render: (p) => PAY_TYPE[p.type] || p.type || '—' },
    { key: 'status', header: 'Status', render: (p) => <StatusBadge status={PAY_STATUS_TONE[p.status] || 'secondary'} label={p.status || '—'} /> },
  ]

  return (
    <Card title="Payment history" actions={<FiDollarSign className="text-muted" />}>
      <DataTable columns={columns} rows={rows} loading={isLoading}
        emptyMessage="No payments yet. Pending payments appear here once completed on Stripe." />
    </Card>
  )
}
