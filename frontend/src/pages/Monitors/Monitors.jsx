import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  FiPlus, FiEdit2, FiTrash2, FiRadio, FiAlertTriangle, FiClock, FiPause, FiPlay,
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
  const [bulkResult, setBulkResult] = useState(null) // { created, skipped } from a multi-product create
  const closeModal = () => { setEditing(null); setBulkResult(null); save.reset() }

  const { data, isLoading } = useQuery({
    queryKey: ['monitors'],
    queryFn: () => monitorService.list(),
  })
  const monitors = data ?? []

  // The cadence floor is a plan property, so any existing monitor carries it.
  // Passed to the create form, which has no monitor of its own to read it from.
  const floor = monitors.find((m) => m.minIntervalMinutes != null)?.minIntervalMinutes ?? null

  const refresh = () => qc.invalidateQueries({ queryKey: ['monitors'] })

  // Products that already have a monitor, so the create checklist can mark
  // them rather than offer a tick the backend would refuse.
  const monitoredIds = new Set(monitors.map((m) => m.productId))

  const save = useMutation({
    mutationFn: ({ productIds, ...values }) => (editing?.id
      ? monitorService.update(editing.id, values)
      : productIds
        ? monitorService.createMany(productIds, values)
        : monitorService.create(values)),
    onSuccess: (res) => {
      // Several products: what started and what was skipped are both the
      // answer, so the dialog shows them together instead of a toast.
      if (res?.created) {
        setBulkResult(res)
        if (res.created.length) refresh()
        return
      }
      notify.success(editing?.id ? 'Monitor updated' : 'Monitoring started')
      setEditing(null)
      refresh()
    },
    // No toast: the backend's message (cadence below the plan floor, already
    // monitored, over 200 products…) is shown inside the dialog, which stays
    // open with its values — nothing was saved, so one field can be corrected.
  })

  const toggle = useMutation({
    mutationFn: ({ id, enabled }) => monitorService.update(id, { enabled }),
    onSuccess: (_d, { enabled }) => { notify.success(enabled ? 'Monitoring resumed' : 'Monitoring paused'); refresh() },
    onError: (err) => notify.error(err?.message || 'Could not change the monitor.'),
  })

  // Monitors picked for a bulk action, held as ids so a refresh can't swap the
  // rows underneath the selection. Ids that have gone since are dropped.
  const [selected, setSelected] = useState(() => new Set())
  const clearSelection = () => setSelected(new Set())
  const toggleRow = (id) => setSelected((s) => {
    const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n
  })
  const toggleAllRows = () => setSelected((s) => {
    const ids = monitors.map((m) => m.id)
    const all = ids.length > 0 && ids.every((id) => s.has(id))
    const n = new Set(s)
    ids.forEach((id) => (all ? n.delete(id) : n.add(id)))
    return n
  })
  const picked = monitors.filter((m) => selected.has(m.id))

  // Reports the backend's count, not the selection size. They differ when
  // monitors went away while the page was open — those are skipped, not
  // refused — and saying so is more honest than claiming all of them.
  // Always refetched afterwards: resuming staggers the next runs, so the
  // "Next run" column would otherwise show times that are simply wrong.
  const reportBulk = (count, sent, verb) => {
    const n = (k) => `${k} monitor${k === 1 ? '' : 's'}`
    if (count >= sent) notify.success(`${n(count)} ${verb}`)
    else if (count > 0) notify.success(`${count} of ${sent} ${verb} — the other ${sent - count} ${sent - count === 1 ? 'was' : 'were'} already gone.`)
    else notify.info(`Nothing ${verb} — ${sent === 1 ? 'that monitor was' : 'those monitors were'} already gone.`)
    clearSelection(); refresh()
  }

  // Pausing keeps the monitor and its settings and is undone in one click, so
  // it doesn't ask. Deleting does. Only monitors whose state would actually
  // change are sent.
  const bulkEnabled = useMutation({
    mutationFn: ({ ids, enabled }) => monitorService.setEnabledMany(ids, enabled),
    onSuccess: (count, { ids, enabled }) => reportBulk(count, ids.length, enabled ? 'resumed' : 'paused'),
    onError: (err) => notify.error(err?.message || 'Could not change those monitors.'),
  })
  const bulkRemove = useMutation({
    mutationFn: (ids) => monitorService.removeMany(ids),
    onSuccess: (count, ids) => reportBulk(count, ids.length, 'deleted'),
    onError: (err) => notify.error(err?.message || 'Could not delete those monitors.'),
  })
  // Only monitors whose state would actually change are sent.
  const setPickedEnabled = (enabled) => {
    const ids = picked.filter((m) => m.enabled !== enabled).map((m) => m.id)
    if (ids.length) bulkEnabled.mutate({ ids, enabled })
  }
  // Deleting throws the check history away; pausing keeps everything. The
  // dialog says so, since "stop checking for now" is the far commoner intent.
  const onBulkDelete = async () => {
    const ids = picked.map((m) => m.id)
    if (!ids.length) return
    const ok = await confirm({
      title: `Delete ${ids.length} monitor${ids.length === 1 ? '' : 's'}?`,
      text: 'Their check history will be deleted too. To stop them running without losing anything, pause them instead.',
      confirmText: 'Delete',
    })
    if (ok) bulkRemove.mutate(ids)
  }
  const bulkBusy = bulkEnabled.isPending || bulkRemove.isPending
  const anyRunning = picked.some((m) => m.enabled)
  const anyPaused = picked.some((m) => !m.enabled)

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
          <Button write icon={FiPlus} onClick={() => setEditing({})}>Monitor products</Button>
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
          <>
            {canManage && picked.length > 0 && (
              <div className="d-flex flex-wrap align-items-center gap-2 m-2 px-2 py-1 rounded-3"
                style={{ background: 'var(--hover-soft)', border: '1px solid var(--border-soft)' }}>
                <span className="small fw-semibold">{picked.length} selected</span>
                <button className="btn btn-sm d-inline-flex align-items-center gap-1 fw-semibold"
                  style={{ background: 'var(--card-bg)', border: '1px solid var(--border-soft)' }}
                  disabled={bulkBusy || !anyRunning} title={anyRunning ? undefined : 'All selected monitors are already paused'}
                  onClick={() => setPickedEnabled(false)} {...writeLock}>
                  <FiPause size={13} /> Pause
                </button>
                <button className="btn btn-sm d-inline-flex align-items-center gap-1 fw-semibold"
                  style={{ background: 'var(--card-bg)', border: '1px solid var(--border-soft)' }}
                  disabled={bulkBusy || !anyPaused} title={anyPaused ? undefined : 'All selected monitors are already running'}
                  onClick={() => setPickedEnabled(true)} {...writeLock}>
                  <FiPlay size={13} /> Resume
                </button>
                <button className="btn btn-sm d-inline-flex align-items-center gap-1 fw-semibold"
                  style={{ background: 'rgba(239,68,68,0.1)', color: 'var(--danger)', border: '1px solid rgba(239,68,68,0.25)' }}
                  disabled={bulkBusy} onClick={onBulkDelete} {...writeLock}>
                  <FiTrash2 size={13} /> Delete
                </button>
                {bulkBusy && <span className="spinner-border spinner-border-sm text-primary" role="status" />}
                <button className="btn btn-sm btn-link text-decoration-none p-0 px-1 text-muted"
                  onClick={clearSelection} disabled={bulkBusy}>Clear</button>
              </div>
            )}
            <DataTable
              columns={columns}
              rows={monitors}
              loading={isLoading}
              emptyMessage="No monitors yet."
              selected={canManage ? selected : undefined}
              onToggleRow={canManage ? toggleRow : undefined}
              onToggleAll={canManage ? toggleAllRows : undefined}
            />
          </>
        ) : (
          <div className="p-3">
            <EmptyState
              icon={FiClock}
              title="Nothing is being monitored yet"
              message="A monitor re-checks one product on a schedule, so competitor prices, trends and
                       recommendations stay current without anyone running a search by hand."
              action={canManage
                ? <Button write icon={FiPlus} onClick={() => setEditing({})}>Monitor products</Button>
                : <span className="text-muted small">Ask an admin or manager to set one up.</span>}
            />
          </div>
        )}
      </Card>

      <MonitorModal
        show={editing !== null}
        onClose={closeModal}
        result={bulkResult}
        error={save.isError ? (save.error?.message || 'Could not save the monitor.') : null}
        onBackFromResult={() => setBulkResult(null)}
        onSave={save.mutate}
        saving={save.isPending}
        monitor={editing?.id ? editing : null}
        minIntervalMinutes={floor}
        multiple
        monitoredIds={monitoredIds}
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
