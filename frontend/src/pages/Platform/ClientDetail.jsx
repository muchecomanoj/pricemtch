import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  FiArrowLeft, FiCreditCard, FiBarChart2, FiCpu, FiClock, FiUsers,
  FiPause, FiPlay, FiDollarSign, FiActivity, FiShield,
} from 'react-icons/fi'
import { planPeriodLabel } from '../../utils/plans'
import Breadcrumb from '../../components/common/Breadcrumb'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Loader from '../../components/common/Loader'
import StatusBadge from '../../components/common/StatusBadge'
import { clientService } from '../../services/clientService'
import { useNotification } from '../../context/NotificationContext'
import SubscriptionPanel from './clientDetail/SubscriptionPanel'
import { StatsPanel, UsagePanel } from './clientDetail/StatsPanels'
import { PaymentsPanel, HistoryPanel } from './clientDetail/PaymentsAndHistory'
import { errorMessage } from '../../services/apiError'

const TABS = [
  { key: 'subscription', label: 'Subscription', icon: FiCreditCard },
  { key: 'payments', label: 'Payments', icon: FiDollarSign },
  { key: 'stats', label: 'Stats', icon: FiBarChart2 },
  { key: 'usage', label: 'Usage', icon: FiCpu },
  { key: 'history', label: 'History', icon: FiClock },
]

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
    catch (e) { notify.error(errorMessage(e, 'Action failed')) }
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
