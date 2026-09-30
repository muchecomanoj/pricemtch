import { useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  FiPlus, FiEdit2, FiPlay, FiPause, FiCopy, FiSettings,
  FiUsers, FiCheckCircle, FiClock, FiAlertTriangle,
} from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import DataTable from '../../components/tables/DataTable'
import Pagination from '../../components/common/Pagination'
import SearchBar from '../../components/common/SearchBar'
import Button from '../../components/common/Button'
import Modal from '../../components/common/Modal'
import StatusBadge from '../../components/common/StatusBadge'
import { clientService } from '../../services/clientService'
import { useNotification } from '../../context/NotificationContext'
import { daysUntil } from '../../utils/format'
import { planPeriodLabel } from '../../utils/plans'
import { PAGE_SIZE, TIMEZONES, DEFAULT_TIMEZONE, timezoneLabel } from '../../constants'

// Deterministic gradient avatar per company, so rows are easier to scan.
const AVATAR_GRADS = [
  'linear-gradient(135deg,#6366f1,#8b5cf6)', 'linear-gradient(135deg,#0ea5e9,#3fa9f5)',
  'linear-gradient(135deg,#10b981,#0d9488)', 'linear-gradient(135deg,#f59e0b,#d97706)',
  'linear-gradient(135deg,#ec4899,#db2777)', 'linear-gradient(135deg,#14b8a6,#0891b2)',
]
const gradFor = (s = '') => AVATAR_GRADS[[...s].reduce((a, c) => a + c.charCodeAt(0), 0) % AVATAR_GRADS.length]
const initials = (name = '') => name.trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase() || '—'

function CompanyAvatar({ name }) {
  return (
    <div className="d-grid rounded-3 text-white fw-bold flex-shrink-0"
      style={{ width: 40, height: 40, placeItems: 'center', fontSize: '0.85rem', background: gradFor(name) }}>
      {initials(name)}
    </div>
  )
}

function StatTile({ icon: Icon, label, value, grad = 'primary', accent }) {
  return (
    <div className="col-6 col-xl-3">
      <Card hover className="h-100">
        <div className="d-flex align-items-center gap-3">
          <div className={`icon-box icon-grad-${grad} flex-shrink-0`}><Icon /></div>
          <div>
            <div className="h4 fw-bold mb-0">{value}</div>
            <div className="text-muted small">{label}</div>
          </div>
        </div>
        {accent && <div className={`small mt-2 ${accent.tone}`}>{accent.text}</div>}
      </Card>
    </div>
  )
}

export default function ClientsAdmin() {
  const qc = useQueryClient()
  const { notify } = useNotification()
  const [page, setPage] = useState(1)
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState(null)  // client form
  const [welcome, setWelcome] = useState(null)   // { email, link } after creating a client

  const { data, isLoading } = useQuery({
    queryKey: ['clients', { page, search }],
    queryFn: () => clientService.list({ page, size: PAGE_SIZE, search }),
  })

  // Separate fetch for the summary tiles. size=100 is the backend's max page;
  // the Total tile uses totalElements (always accurate), the breakdown covers
  // the first 100 tenants.
  const statsQ = useQuery({
    queryKey: ['clients', 'stats'],
    queryFn: () => clientService.list({ page: 1, size: 100 }),
    staleTime: 60_000,
  })
  const stats = useMemo(() => {
    const rows = statsQ.data?.content ?? []
    const total = statsQ.data?.totalElements ?? rows.length
    let active = 0, trial = 0, attention = 0
    for (const c of rows) {
      if (/active/i.test(c.subscriptionStatus)) active++
      const onTrial = /trial/i.test(c.subscriptionStatus)
      if (onTrial) trial++
      // Once per company, however many reasons apply. An expired trial that
      // was also suspended used to be counted twice.
      const left = daysUntil(c.subscriptionEndDate)
      const expiringTrial = onTrial && left != null && left <= 3
      const suspended = /suspend/i.test(c.status)
      const expired = /expired/i.test(c.subscriptionStatus)
      if (expiringTrial || suspended || expired) attention++
    }
    return { total, active, trial, attention }
  }, [statsQ.data])

  const invalidate = () => qc.invalidateQueries({ queryKey: ['clients'] })

  const saveMut = useMutation({
    mutationFn: (v) => (v.id ? clientService.update(v.id, v) : clientService.create(v)),
    onSuccess: (data, vars) => {
      invalidate(); setEditing(null)
      if (vars.id) { notify.success('Client saved'); return }
      // New client — a welcome email is sent so they set their own password + plan.
      const email = vars.adminEmail || data?.companyEmail || ''
      const token = btoa(`${data?.id || email}:${email}`).replace(/=+$/, '')
      setWelcome({ email, link: `${window.location.origin}/activate?token=${token}` })
      notify.success('Client created — welcome email sent')
    },
    onError: (e) => notify.error(e.message || 'Save failed'),
  })
  const statusMut = useMutation({
    mutationFn: ({ id, action }) => clientService.setStatus(id, action),
    onSuccess: (_, { action }) => { invalidate(); notify.success(`Client ${action}d`) },
    onError: (e) => notify.error(e.message || 'Action failed'),
  })
  // Clients are never hard-deleted from this screen — use Suspend instead.

  const columns = [
    { key: 'companyName', header: 'Company', render: (c) => (
      <Link to={`/platform/clients/${c.id}`} className="d-flex align-items-center gap-3 text-reset text-decoration-none">
        <CompanyAvatar name={c.companyName} />
        <div style={{ minWidth: 0 }}>
          <div className="fw-semibold text-truncate">{c.companyName}</div>
          <div className="text-muted small text-truncate">{c.companyCode} · {c.companyEmail}</div>
        </div>
      </Link>
    ) },
    { key: 'subscriptionPlan', header: 'Plan', render: (c) => (
      c.subscriptionPlan ? (
        <div>
          <span className="badge rounded-pill text-bg-primary">{c.subscriptionPlan}</span>
          {planPeriodLabel(c) && <div className="text-muted small mt-1 text-capitalize">{planPeriodLabel(c)}</div>}
        </div>
      ) : <span className="badge rounded-pill text-bg-warning" title="The client picks a plan during onboarding">Not selected</span>
    ) },
    { key: 'subscriptionStatus', header: 'Subscription', render: (c) => {
      if (!c.subscriptionStatus) return <StatusBadge status="secondary" label="Awaiting onboarding" />
      const onTrial = /trial/i.test(c.subscriptionStatus)
      const left = onTrial ? daysUntil(c.subscriptionEndDate) : null
      return (
        <div>
          <div>
            <StatusBadge status={/active/i.test(c.subscriptionStatus) ? 'success' : onTrial ? 'info' : 'secondary'} label={c.subscriptionStatus} />
          </div>
          {left != null && (
            <div className={`small mt-1 d-flex align-items-center gap-1 ${left < 0 ? 'text-danger' : left <= 3 ? 'text-warning' : 'text-muted'}`}>
              <FiClock size={11} />{left < 0 ? `Expired ${Math.abs(left)}d ago` : left === 0 ? 'Ends today' : `${left}d left`}
            </div>
          )}
        </div>
      )
    } },
    { key: 'status', header: 'Tenant', render: (c) => {
      const tone = /active/i.test(c.status) ? 'success' : /suspend/i.test(c.status) ? 'warning' : 'secondary'
      const color = tone === 'success' ? 'var(--success)' : tone === 'warning' ? 'var(--warning)' : 'var(--text-secondary)'
      return (
        <span className="d-inline-flex align-items-center gap-2 small fw-semibold text-capitalize">
          <span style={{ width: 8, height: 8, borderRadius: '50%', background: color, display: 'inline-block' }} />
          {(c.status || '—').toLowerCase()}
        </span>
      )
    } },
    { key: 'actions', header: 'Action', className: 'text-end', render: (c) => (
      <div className="d-inline-flex gap-1">
        <button className="icon-btn" title="Edit client" onClick={() => setEditing(c)}><FiEdit2 /></button>
        {/active/i.test(c.status)
          ? <button className="icon-btn text-warning" title="Suspend" onClick={() => statusMut.mutate({ id: c.id, action: 'suspend' })}><FiPause /></button>
          : <button className="icon-btn text-success" title="Resume" onClick={() => statusMut.mutate({ id: c.id, action: 'resume' })}><FiPlay /></button>}
        <Link to={`/platform/clients/${c.id}`} className="btn btn-sm btn-primary d-inline-flex align-items-center"
          title="Manage — subscription, stats, usage & history"><FiSettings /></Link>
      </div>
    ) },
  ]

  return (
    <>
      <PageHeader title="Clients" subtitle="Platform — manage tenant companies and their subscriptions."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Platform' }, { label: 'Clients' }]}
        actions={
          <Link to="/platform/clients/new" className="btn btn-primary d-inline-flex align-items-center gap-2">
            <FiPlus /> Add Client
          </Link>
        } />

      {/* Summary tiles */}
      <div className="row g-3 mb-3">
        <StatTile icon={FiUsers} label="Total clients" value={stats.total} grad="primary" />
        <StatTile icon={FiCheckCircle} label="Active subscriptions" value={stats.active} grad="success" />
        <StatTile icon={FiClock} label="On trial" value={stats.trial} grad="info" />
        <StatTile icon={FiAlertTriangle} label="Needs attention" value={stats.attention} grad="warning"
          accent={stats.attention > 0 ? { tone: 'text-warning', text: 'Expiring, expired or suspended' } : null} />
      </div>

      <Card
        title="All clients"
        subtitle={`${data?.totalElements ?? 0} companies`}
        actions={<SearchBar value={search} onChange={(v) => { setSearch(v); setPage(1) }} placeholder="Search companies…" />}>
        <DataTable columns={columns} rows={data?.content ?? []} loading={isLoading} emptyMessage="No clients found." />
        <div className="d-flex justify-content-between align-items-center mt-3">
          <span className="text-muted small">
            {data?.content?.length ? `Showing ${data.content.length} of ${data.totalElements}` : ''}
          </span>
          <Pagination page={page} totalPages={data?.totalPages ?? 1} onChange={setPage} />
        </div>
      </Card>

      <Modal show={editing !== null} title={editing?.id ? 'Edit Client' : 'Add Client'} onClose={() => setEditing(null)} size="lg">
        {editing !== null && <ClientForm defaultValues={editing} submitting={saveMut.isPending}
          onSubmit={(v) => saveMut.mutate({ ...editing, ...v })} onCancel={() => setEditing(null)} />}
      </Modal>

      <Modal
        show={welcome !== null}
        title="Welcome email sent"
        onClose={() => setWelcome(null)}
        footer={<Button onClick={() => setWelcome(null)}>Done</Button>}
      >
        {welcome && (
          <div>
            <p className="text-muted">
              A welcome email has been sent to <strong>{welcome.email}</strong>. The client will set their
              password and choose a plan via this activation link:
            </p>
            <div className="input-group">
              <input className="form-control" readOnly value={welcome.link} onFocus={(e) => e.target.select()} />
              <Button variant="light" icon={FiCopy}
                onClick={() => { navigator.clipboard?.writeText(welcome.link); notify.success('Link copied') }}>
                Copy
              </Button>
            </div>
            <p className="text-muted small mt-2 mb-0">
              (Shown here for testing — once the backend is live, this link is delivered by email only.)
            </p>
          </div>
        )}
      </Modal>
    </>
  )
}

function ClientForm({ defaultValues, onSubmit, onCancel, submitting }) {
  const isEdit = !!defaultValues?.id
  const { register, handleSubmit } = useForm({
    defaultValues: { currency: 'USD', country: 'US', timezone: DEFAULT_TIMEZONE, ...defaultValues },
  })
  return (
    <form onSubmit={handleSubmit(onSubmit)} className="row g-3">
      <div className="col-md-6"><label className="form-label">Company Name</label>
        <input className="form-control" {...register('companyName', { required: true })} /></div>
      {!isEdit && <div className="col-md-6"><label className="form-label">Company Code</label>
        <input className="form-control" {...register('companyCode', { required: true })} /></div>}
      <div className="col-md-6"><label className="form-label">Company Email</label>
        <input type="email" className="form-control" {...register('companyEmail')} /></div>
      <div className="col-md-6"><label className="form-label">Phone</label>
        <input className="form-control" {...register('companyPhone')} /></div>
      <div className="col-12"><label className="form-label">Address</label>
        <input className="form-control" {...register('companyAddress')} /></div>
      <div className="col-md-4"><label className="form-label">City</label><input className="form-control" {...register('city')} /></div>
      <div className="col-md-4"><label className="form-label">State</label><input className="form-control" {...register('state')} /></div>
      <div className="col-md-4"><label className="form-label">Country</label><input className="form-control" {...register('country')} /></div>
      <div className="col-md-4"><label className="form-label">Currency</label><input className="form-control" {...register('currency')} /></div>
      <div className="col-md-4">
        <label className="form-label">Timezone</label>
        <select className="form-select" {...register('timezone')}>
          {TIMEZONES.map((tz) => <option key={tz} value={tz}>{timezoneLabel(tz)}</option>)}
        </select>
      </div>
      <div className="col-md-4"><label className="form-label">Website</label><input className="form-control" {...register('website')} /></div>

      {!isEdit && (
        <>
          <div className="col-12"><hr /><h6 className="fw-semibold mb-0">Client Admin</h6></div>
          <div className="col-md-4"><label className="form-label">Admin First Name</label><input className="form-control" {...register('adminFirstName')} /></div>
          <div className="col-md-4"><label className="form-label">Admin Last Name</label><input className="form-control" {...register('adminLastName')} /></div>
          <div className="col-md-4"><label className="form-label">Admin Email</label>
            <input type="email" className="form-control" {...register('adminEmail', { required: true })} /></div>
          <div className="col-12">
            <div className="alert alert-info py-2 small mb-0">
              A welcome email is sent to the client admin. <strong>They set their own password and choose a plan</strong> —
              no password or plan is needed here.
            </div>
          </div>
        </>
      )}
      <div className="col-12 d-flex justify-content-end gap-2">
        <Button variant="light" type="button" onClick={onCancel}>Cancel</Button>
        <Button type="submit" loading={submitting}>Save Client</Button>
      </div>
    </form>
  )
}
