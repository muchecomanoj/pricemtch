import { useQuery } from '@tanstack/react-query'
import Card from '../../../components/common/Card'
import StatusBadge from '../../../components/common/StatusBadge'
import DataTable from '../../../components/tables/DataTable'
import PaymentHistoryCard from '../../../components/billing/PaymentHistoryCard'
import { clientService } from '../../../services/clientService'
import { formatCurrency } from '../../../utils/format'

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
  INITIAL: 'New subscription', RENEWAL: 'Renewal', UPGRADE: 'Upgrade', DOWNGRADE: 'Downgrade',
  SIGNUP: 'Sign-up', NEW: 'New subscription',
}

const historyAction = (a) => HISTORY_ACTION[String(a || '').toUpperCase()]
  || String(a || '—').toLowerCase().replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase())

// ── History — GET /admin/clients/{id}/subscription/history ──
// ── Payments — GET /admin/clients/{id}/payments (Stripe records) ──
const PAY_STATUS_TONE = { PAID: 'success', PENDING: 'warning', FAILED: 'danger', CANCELLED: 'secondary' }

export function PaymentsPanel({ id }) {
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
    <PaymentHistoryCard title="Payments" subtitle="Stripe transactions for this client"
      queryKey={['client', id, 'payments']} fetchPage={(params) => clientService.payments(id, params)} columns={columns}
      emptyMessage="No payments yet. Pending payments appear here once the Stripe webhook records them." />
  )
}

export function HistoryPanel({ id }) {
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
