import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { FiPlus, FiTrash2, FiBell, FiInfo, FiZap, FiPause, FiPlay } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import DataTable from '../../components/tables/DataTable'
import StatusBadge from '../../components/common/StatusBadge'
import ComboInput from '../../components/forms/ComboInput'
import { productIntelService } from '../../services/productIntelService'
import { productService } from '../../services/productService'
import { useNotification } from '../../context/NotificationContext'
import { useAuth, useWriteLock } from '../../context/AuthContext'
import AlertRuleModal from '../../components/alerts/AlertRuleModal'
import { ChannelPills, conditionLabel, thresholdUnit } from '../../components/alerts/alertRules'
import { formatDate, formatRelativeTime } from '../../utils/format'

// GET/POST /alerts/rules · DELETE /alerts/rules/{id} · POST /alerts/rules/evaluate
//
// The engine runs hourly on the backend. A fired rule creates an
// ALERT_TRIGGERED notification, so fired alerts appear in the header bell —
// there is no separate feed endpoint.
//
// thresholdValue means different things per condition, hence the per-condition
// hint and unit below.
export default function Alerts() {
  // Plain write buttons lock themselves while the plan is read-only.
  const writeLock = useWriteLock()
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()
  const { can } = useAuth()
  const canManage = can('MANAGE_PRICING')

  const [filterProduct, setFilterProduct] = useState('')   // '' = all products
  // Adding is a dialog, shared with the Products list so one rule across many
  // products and one rule on one product are the same form.
  const [adding, setAdding] = useState(false)

  // Rules picked for a bulk action, held as ids rather than rows so a refresh
  // mid-selection cannot carry a rule someone else has since deleted.
  const [selected, setSelected] = useState(() => new Set())

  const productsQ = useQuery({
    queryKey: ['products', 'selector'],
    queryFn: () => productService.list({ page: 1, size: 100 }),
  })
  const products = useMemo(() => productsQ.data?.content ?? [], [productsQ.data])
  // Rules carry productId but not the title, so resolve names locally.
  const titleFor = useMemo(() => {
    const map = new Map(products.map((p) => [p.id, p.title]))
    return (id) => map.get(id) || `#${id}`
  }, [products])

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['alertRules', filterProduct || 'all'],
    queryFn: () => productIntelService.alertRules(filterProduct || undefined),
  })

  const refresh = () => qc.invalidateQueries({ queryKey: ['alertRules'] })

  const remove = useMutation({
    mutationFn: (id) => productIntelService.deleteAlertRule(id),
    onSuccess: () => { notify.success('Rule removed'); refresh() },
    onError: (err) => notify.error(err?.message || 'Could not remove the rule.'),
  })

  const evaluate = useMutation({
    mutationFn: () => productIntelService.evaluateAlertRules(),
    onSuccess: (res) => {
      const fired = res?.fired ?? 0
      if (fired > 0) notify.success(`${fired} alert${fired === 1 ? '' : 's'} fired — check the bell`)
      else notify.info('No alerts triggered right now.')
      refresh()
      // Firing creates notifications, so the bell badge may have moved.
      qc.invalidateQueries({ queryKey: ['notifications'] })
    },
    onError: (err) => notify.error(err?.message || 'Could not run the alert check.'),
  })

  const onDelete = async (row) => {
    const ok = await confirm({
      title: 'Remove this rule?',
      text: `${conditionLabel(row.condition)} will no longer be monitored.`,
      confirmText: 'Remove',
    })
    if (ok) remove.mutate(row.id)
  }

  const rules = data ?? []
  const clearSelection = () => setSelected(new Set())
  const toggleRow = (id) => setSelected((s) => {
    const n = new Set(s); n.has(id) ? n.delete(id) : n.add(id); return n
  })
  const toggleAllRows = () => setSelected((s) => {
    const ids = rules.map((r) => r.id)
    const all = ids.length > 0 && ids.every((id) => s.has(id))
    const n = new Set(s)
    ids.forEach((id) => (all ? n.delete(id) : n.add(id)))
    return n
  })

  // Pausing keeps the rule and its history and is undone with one click, so it
  // is offered first and without a confirmation. Deleting is the one that
  // throws the firing history away, so only that one asks.
  const setActive = useMutation({
    mutationFn: ({ id, active }) => productIntelService.setAlertRuleActive(id, active),
    onSuccess: (_res, { active }) => { notify.success(active ? 'Rule resumed' : 'Rule paused'); refresh() },
    onError: (err) => notify.error(err?.message || 'Could not change the rule.'),
  })

  const bulkActive = useMutation({
    mutationFn: ({ ids, active }) => productIntelService.bulkSetAlertRuleActive(ids, active),
    onSuccess: (res, { ids, active }) => {
      // Rules that have gone since the list loaded are ignored by the backend
      // rather than refused, so a lower count means the screen is stale.
      const changed = res?.changed ?? ids.length
      notify.success(`${changed} rule${changed === 1 ? '' : 's'} ${active ? 'resumed' : 'paused'}`)
      clearSelection(); refresh()
    },
    onError: (err) => notify.error(err?.message || 'Could not change those rules.'),
  })

  const bulkRemove = useMutation({
    mutationFn: (ids) => productIntelService.bulkDeleteAlertRules(ids),
    onSuccess: (res, ids) => {
      const deleted = res?.deleted ?? ids.length
      notify.success(`${deleted} rule${deleted === 1 ? '' : 's'} removed`)
      clearSelection(); refresh()
    },
    onError: (err) => notify.error(err?.message || 'Could not remove those rules.'),
  })

  const onBulkDelete = async () => {
    const ids = [...selected]
    if (!ids.length) return
    const ok = await confirm({
      title: `Remove ${ids.length} rule${ids.length === 1 ? '' : 's'}?`,
      text: 'Their firing history goes with them. Pause instead if you only want them to stop firing.',
      confirmText: 'Remove',
    })
    if (ok) bulkRemove.mutate(ids)
  }

  const columns = [
    {
      key: 'product',
      header: 'Product',
      render: (r) => (
        <Link to={`/products/${r.productId}`} className="fw-semibold text-decoration-none text-truncate d-block"
          style={{ maxWidth: 220 }}>
          {titleFor(r.productId)}
        </Link>
      ),
    },
    { key: 'condition', header: 'Condition', render: (r) => conditionLabel(r.condition) },
    {
      key: 'threshold',
      header: 'Threshold',
      render: (r) => (r.thresholdValue == null
        ? <span className="text-muted small">Any occurrence</span>
        : <span className="fw-semibold">{r.thresholdValue}{thresholdUnit(r)}</span>),
    },
    {
      key: 'triggerCount',
      header: 'Fired',
      render: (r) => (r.triggerCount
        ? <span className="mr-pill mr-pill--orange">{r.triggerCount}×</span>
        : <span className="text-muted small">Never</span>),
    },
    {
      key: 'lastFiredAt',
      header: 'Last fired',
      render: (r) => <span className="text-muted small">{r.lastFiredAt ? formatRelativeTime(r.lastFiredAt) : '—'}</span>,
    },
    {
      key: 'channels',
      header: 'Sends to',
      className: 'text-nowrap',
      // Without this you could set a rule to post to Slack and have no way to
      // confirm it does — the only recourse was deleting and recreating it.
      render: (r) => <ChannelPills channels={r.channels} />,
    },
    { key: 'note', header: 'Note', render: (r) => r.note || <span className="text-muted">—</span> },
    {
      key: 'active',
      header: 'Status',
      // A switch, not a badge: "stop telling me about this" is the commonest
      // thing to want here, and it used to need deleting the rule and its
      // history to get it.
      render: (r) => (canManage ? (
        <div className="form-check form-switch d-flex align-items-center gap-2 mb-0 ps-0">
          <input className="form-check-input m-0" type="checkbox" role="switch"
            aria-label={r.active ? 'Pause this rule' : 'Resume this rule'}
            title={r.active ? 'Firing — switch off to pause' : 'Paused — switch on to resume'}
            checked={!!r.active} disabled={setActive.isPending}
            onChange={() => setActive.mutate({ id: r.id, active: !r.active })} {...writeLock} />
          <span className="small text-muted">{r.active ? 'Active' : 'Paused'}</span>
        </div>
      ) : <StatusBadge status={r.active ? 'ACTIVE' : 'INACTIVE'} />),
    },
    {
      key: 'createdAt',
      header: 'Created',
      render: (r) => <span className="text-muted small">{r.createdAt ? formatDate(r.createdAt) : '—'}</span>,
    },
    {
      key: 'actions',
      header: '',
      className: 'text-end',
      render: (r) => (canManage ? (
        <button className="icon-btn text-danger" title="Remove rule" disabled={remove.isPending}
          onClick={() => onDelete(r)} {...writeLock}><FiTrash2 /></button>
      ) : null),
    },
  ]

  return (
    <>
      <PageHeader
        title="Alert Rules"
        subtitle="Conditions monitored across your catalog. Fired alerts appear in the bell."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Alerts' }]}
        actions={canManage && (
          <>
            <Button write variant="light" icon={FiZap} loading={evaluate.isPending}
              onClick={() => evaluate.mutate()}>Check now</Button>
            <Button write icon={FiPlus} onClick={() => setAdding(true)}>Add rule</Button>
          </>
        )}
      />

      <div className="d-flex align-items-center gap-2 text-muted small mb-3">
        <FiInfo className="flex-shrink-0" />
        Rules are evaluated automatically every hour. Use <strong className="mx-1">Check now</strong> to run them immediately.
      </div>

      <Card
        title={`Rules (${data?.length ?? 0})`}
        actions={(
          // Data-driven list, so a searchable ComboInput rather than a select.
          <div style={{ width: 240 }}>
            <ComboInput value={products.find((p) => String(p.id) === String(filterProduct))?.title || ''}
              onChange={(v) => {
                setFilterProduct(products.find((p) => p.title === v)?.id ?? '')
                // Otherwise a bulk action could act on rules scrolled out of
                // sight by the filter that was just applied.
                clearSelection()
              }}
              options={products.map((p) => p.title)}
              placeholder="All products" emptyLabel="All products"
              disabled={productsQ.isLoading} strict clearable />
          </div>
        )}
      >
        {isError ? (
          <div className="alert alert-danger py-2 small mb-0">
            {error?.message || 'Could not load the alert rules.'}
          </div>
        ) : (
          <>
            {canManage && selected.size > 0 && (
              <div className="d-flex flex-wrap align-items-center gap-2 mb-3 px-2 py-1 rounded-3"
                style={{ background: 'var(--hover-soft)', border: '1px solid var(--border-soft)' }}>
                <span className="small fw-semibold">{selected.size} selected</span>
                <button className="btn btn-sm d-inline-flex align-items-center gap-1 fw-semibold"
                  style={{ background: 'var(--card-bg)', border: '1px solid var(--border-soft)' }}
                  disabled={bulkActive.isPending}
                  onClick={() => bulkActive.mutate({ ids: [...selected], active: false })} {...writeLock}>
                  <FiPause size={13} /> Pause
                </button>
                <button className="btn btn-sm d-inline-flex align-items-center gap-1 fw-semibold"
                  style={{ background: 'var(--card-bg)', border: '1px solid var(--border-soft)' }}
                  disabled={bulkActive.isPending}
                  onClick={() => bulkActive.mutate({ ids: [...selected], active: true })} {...writeLock}>
                  <FiPlay size={13} /> Resume
                </button>
                <button className="btn btn-sm d-inline-flex align-items-center gap-1 fw-semibold"
                  style={{ background: 'rgba(239,68,68,0.1)', color: 'var(--danger)', border: '1px solid rgba(239,68,68,0.25)' }}
                  disabled={bulkRemove.isPending} onClick={onBulkDelete} {...writeLock}>
                  <FiTrash2 size={13} /> Delete
                </button>
                <button className="btn btn-sm btn-link text-decoration-none p-0 px-1 text-muted"
                  onClick={clearSelection}>Clear</button>
              </div>
            )}
            <DataTable columns={columns} rows={rules} loading={isLoading}
              emptyMessage="No rules yet. Add one to start monitoring."
              selected={canManage ? selected : undefined}
              onToggleRow={canManage ? toggleRow : undefined}
              onToggleAll={canManage ? toggleAllRows : undefined} />
          </>
        )}
      </Card>

      <AlertRuleModal show={adding} onClose={() => setAdding(false)} />

      {!isLoading && (data?.length ?? 0) === 0 && !canManage && (
        <Card className="mt-3">
          <div className="text-center text-muted py-3">
            <FiBell size={26} className="mb-2 opacity-50" />
            <div className="small">A manager can add alert rules for your products.</div>
          </div>
        </Card>
      )}
    </>
  )
}
