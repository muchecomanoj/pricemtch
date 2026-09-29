import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  FiPlus, FiEdit2, FiTrash2, FiRadio, FiAlertTriangle, FiClock,
} from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import StatusBadge from '../../components/common/StatusBadge'
import EmptyState from '../../components/common/EmptyState'
import DataTable from '../../components/tables/DataTable'
import MonitorModal from './MonitorModal'
import { monitorService, intervalLabel } from '../../services/monitorService'
import { mpLabel } from '../../utils/marketplaces'
import { useNotification } from '../../context/NotificationContext'
import { useAuth, useWriteLock } from '../../context/AuthContext'
import { formatRelativeTime } from '../../utils/format'

// Scheduled competitor discovery. Without this every product has to be searched
// by hand, which is why most of a catalog never gets checked — and why the trend
// charts and recommendations have nothing to work from on those products.
export default function Monitors() {
  // Plain write buttons lock themselves while the plan is read-only.
  const writeLock = useWriteLock()
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()
  const { can } = useAuth()
  const canManage = can('MANAGE_PRODUCTS')

  const [editing, setEditing] = useState(null)   // null = closed, {} = create, {...} = edit

  const { data, isLoading } = useQuery({
    queryKey: ['monitors'],
    queryFn: () => monitorService.list(),
  })
  const monitors = data ?? []

  // The cadence floor is a plan property, so any existing monitor carries it.
  // Passed to the create form, which has no monitor of its own to read it from.
  const floor = monitors.find((m) => m.minIntervalMinutes != null)?.minIntervalMinutes ?? null

  const refresh = () => qc.invalidateQueries({ queryKey: ['monitors'] })

  const save = useMutation({
    mutationFn: (values) => (editing?.id
      ? monitorService.update(editing.id, values)
      : monitorService.create(values)),
    onSuccess: () => {
      notify.success(editing?.id ? 'Monitor updated' : 'Monitoring started')
      setEditing(null)
      refresh()
    },
    // 400 (cadence below the plan floor) and 409 (already monitored) both carry
    // a useful message from the backend — show it rather than a generic failure.
    onError: (err) => notify.error(err?.message || 'Could not save the monitor.'),
  })

  const toggle = useMutation({
    mutationFn: ({ id, enabled }) => monitorService.update(id, { enabled }),
    onSuccess: (_d, { enabled }) => { notify.success(enabled ? 'Monitoring resumed' : 'Monitoring paused'); refresh() },
    onError: (err) => notify.error(err?.message || 'Could not change the monitor.'),
  })

  const onDelete = async (m) => {
    const ok = await confirm({
      title: 'Stop monitoring this product?',
      text: `${m.productTitle || `Product #${m.productId}`} will no longer be checked automatically. `
        + 'Listings already collected are kept.',
      confirmText: 'Stop monitoring',
    })
    if (!ok) return
    try {
      await monitorService.remove(m.id)
      notify.success('Monitoring stopped')
      refresh()
    } catch (err) {
      notify.error(err?.message || 'Could not stop the monitor.')
    }
  }

  const columns = [
    {
      key: 'product',
      header: 'Product',
      width: '30%',
      minWidth: 220,
      render: (m) => (
        // Wraps to a second line rather than stretching the column. The
        // max-width is what makes the clamp engage — see .clamp-2.
        <Link to={`/products/${m.productId}`}
          className="fw-semibold text-decoration-none clamp-2"
          style={{ maxWidth: 320 }}
          title={m.productTitle || ''}>
          {m.productTitle || `Product #${m.productId}`}
        </Link>
      ),
    },
    {
      key: 'markets',
      header: 'Marketplaces',
      className: 'text-nowrap',
      render: (m) => (m.markets?.length
        ? m.markets.map(mpLabel).join(', ')
        : <span className="text-muted">All connected</span>),
    },
    {
      key: 'intervalMinutes',
      header: 'Checks',
      className: 'text-nowrap',
      render: (m) => intervalLabel(m.intervalMinutes),
    },
    {
      key: 'lastRunAt',
      header: 'Last run',
      className: 'text-nowrap',
      render: (m) => (m.lastRunAt
        ? <span className="text-muted small">{formatRelativeTime(m.lastRunAt)}</span>
        : <span className="text-muted small">Not yet</span>),
    },
    {
      key: 'nextRunAt',
      header: 'Next run',
      className: 'text-nowrap',
      render: (m) => (!m.enabled
        ? <span className="text-muted small">Paused</span>
        : m.nextRunAt
          ? <span className="text-muted small">{formatRelativeTime(m.nextRunAt)}</span>
          : <span className="text-muted small">—</span>),
    },
    {
      key: 'lastStatus',
      header: 'Status',
      render: (m) => <MonitorStatus monitor={m} />,
    },
    {
      key: 'actions',
      header: '',
      className: 'text-end',
      render: (m) => (canManage ? (
        <div className="d-inline-flex align-items-center gap-1">
          <button className="icon-btn" title={m.enabled ? 'Pause monitoring' : 'Resume monitoring'}
            disabled={toggle.isPending}
            onClick={() => toggle.mutate({ id: m.id, enabled: !m.enabled })} {...writeLock}>
            <FiRadio className={m.enabled ? 'text-success' : 'text-muted'} />
          </button>
          <button className="icon-btn" title="Edit" onClick={() => setEditing(m)} {...writeLock}><FiEdit2 /></button>
          <button className="icon-btn text-danger" title="Stop monitoring"
            onClick={() => onDelete(m)} {...writeLock}><FiTrash2 /></button>
        </div>
      ) : null),
    },
  ]

  const failing = monitors.filter((m) => m.lastStatus === 'FAILED').length

  return (
    <>
      <PageHeader
        title="Monitors"
        subtitle="Products checked automatically for new competitor listings."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Monitors' }]}
        actions={canManage && (
          <Button write icon={FiPlus} onClick={() => setEditing({})}>Monitor a product</Button>
        )}
      />

      {failing > 0 && (
        <div className="field-note field-note--warn d-flex mb-3" style={{ maxWidth: 'none' }}>
          <FiAlertTriangle size={15} />
          <span>
            <strong>{failing} monitor{failing === 1 ? '' : 's'} failed on the last run.</strong>{' '}
            {failing === 1 ? 'It is' : 'They are'} still scheduled and will retry — check the
            status column for the reason.
          </span>
        </div>
      )}

      <Card bodyClassName="p-0">
        {isLoading || monitors.length > 0 ? (
          <DataTable
            columns={columns}
            rows={monitors}
            loading={isLoading}
            emptyMessage="No monitors yet."
          />
        ) : (
          <div className="p-3">
            <EmptyState
              icon={FiClock}
              title="Nothing is being monitored yet"
              message="A monitor re-checks one product on a schedule, so competitor prices, trends and
                       recommendations stay current without anyone running a search by hand."
              action={canManage
                ? <Button write icon={FiPlus} onClick={() => setEditing({})}>Monitor a product</Button>
                : <span className="text-muted small">Ask an admin or manager to set one up.</span>}
            />
          </div>
        )}
      </Card>

      <MonitorModal
        show={editing !== null}
        onClose={() => setEditing(null)}
        onSave={save.mutate}
        saving={save.isPending}
        monitor={editing?.id ? editing : null}
        minIntervalMinutes={floor}
      />
    </>
  )
}

// A failed run does not stop the schedule, so the row must not read like it has
// been switched off — it says the last run failed, and why.
function MonitorStatus({ monitor }) {
  if (!monitor.enabled) return <StatusBadge status="INACTIVE" label="Paused" />
  if (monitor.lastStatus === 'FAILED') {
    return (
      <div>
        <StatusBadge status="REJECTED" label="Last run failed" />
        {monitor.lastMessage && (
          <div className="text-muted mt-1" style={{ fontSize: 11, maxWidth: 260 }}>{monitor.lastMessage}</div>
        )}
      </div>
    )
  }
  if (!monitor.lastRunAt) return <StatusBadge status="DRAFT" label="Scheduled" />
  return (
    <div>
      <StatusBadge status="ACTIVE" label="Active" />
      <div className="text-muted mt-1" style={{ fontSize: 11 }}>
        {monitor.lastResultCount ?? 0} listing{monitor.lastResultCount === 1 ? '' : 's'} last run
      </div>
    </div>
  )
}
