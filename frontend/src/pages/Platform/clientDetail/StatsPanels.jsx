import { useQuery } from '@tanstack/react-query'
import { FiCpu, FiUsers } from 'react-icons/fi'
import Card from '../../../components/common/Card'
import Loader from '../../../components/common/Loader'
import { clientService } from '../../../services/clientService'
import { formatNumber } from '../../../utils/format'
import Field from './Field'

// ── Stats — GET /admin/clients/{id}/stats ──
export function StatsPanel({ id }) {
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
export function UsagePanel({ id }) {
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
