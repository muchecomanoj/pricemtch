import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { FiPlus, FiEdit2, FiPackage, FiSlash, FiCheckCircle } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import DataTable from '../../components/tables/DataTable'
import Button from '../../components/common/Button'
import Modal from '../../components/common/Modal'
import StatusBadge from '../../components/common/StatusBadge'
import { planService } from '../../services/planService'
import { useNotification } from '../../context/NotificationContext'
import { formatCurrency } from '../../utils/format'

// Strip values we don't want to send: NaN (blank number inputs), undefined, and
// empty strings. PUT is a partial update, so omitting a field leaves it unchanged
// rather than blanking it. Booleans and 0 are kept.
function cleanPayload(v) {
  const out = {}
  for (const [k, val] of Object.entries(v)) {
    if (val === undefined || val === '' || (typeof val === 'number' && Number.isNaN(val))) continue
    out[k] = val
  }
  return out
}

export default function PlansAdmin() {
  const qc = useQueryClient()
  const { notify } = useNotification()
  const [editing, setEditing] = useState(null)
  const { data, isLoading } = useQuery({ queryKey: ['plans'], queryFn: planService.list })

  const saveMut = useMutation({
    mutationFn: ({ id, values }) => (id ? planService.update(id, values) : planService.create(values)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['plans'] }); notify.success('Plan saved'); setEditing(null) },
    onError: (e) => notify.error(e.message || 'Could not save plan'),
  })

  const toggleActive = (p) => saveMut.mutate({ id: p.id, values: { active: !p.active } })

  const columns = [
    { key: 'code', header: 'Code', render: (p) => <span className="fw-semibold d-inline-flex align-items-center gap-2"><FiPackage className="text-primary" />{p.code}</span> },
    { key: 'name', header: 'Name' },
    { key: 'price', header: 'Monthly', render: (p) => formatCurrency(p.price, p.currency) },
    // Both prices come straight from the API — no client-side discount math.
    { key: 'yearlyPrice', header: 'Yearly', render: (p) => (
      p.yearlyPrice != null
        ? <span>{formatCurrency(p.yearlyPrice, p.currency)}
          {p.yearlyDiscountPercent > 0 && <span className="badge text-bg-success ms-2">−{p.yearlyDiscountPercent}%</span>}</span>
        : '—'
    ) },
    { key: 'trialDays', header: 'Trial', render: (p) => (p.trialDays != null ? `${p.trialDays}d` : '—') },
    { key: 'maxUsers', header: 'Seats', render: (p) => p.maxUsers ?? '—' },
    { key: 'active', header: 'Status', render: (p) => <StatusBadge status={p.active ? 'success' : 'secondary'} label={p.active ? 'Active' : 'Inactive'} /> },
    { key: 'actions', header: 'Actions', render: (p) => (
      <div className="d-flex gap-1 justify-content-end">
        <button className="btn btn-sm btn-light" title="Edit" onClick={() => setEditing(p)}><FiEdit2 /></button>
        <button className={`btn btn-sm ${p.active ? 'btn-light text-danger' : 'btn-light text-success'}`}
          title={p.active ? 'Deactivate' : 'Activate'} onClick={() => toggleActive(p)}>
          {p.active ? <FiSlash /> : <FiCheckCircle />}
        </button>
      </div>
    ) },
  ]

  return (
    <>
      <PageHeader title="Subscription Plans" subtitle="Platform — plan catalog offered to client companies."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Platform' }, { label: 'Plans' }]}
        actions={<Button icon={FiPlus} onClick={() => setEditing({})}>Add Plan</Button>} />
      <Card title={`Plans (${data?.length ?? 0})`}>
        <DataTable columns={columns} rows={data ?? []} loading={isLoading} />
      </Card>

      <Modal show={editing !== null} title={editing?.id ? 'Edit Plan' : 'Add Plan'} onClose={() => setEditing(null)} size="lg">
        {editing !== null && <PlanForm defaultValues={editing} submitting={saveMut.isPending}
          onSubmit={(v) => saveMut.mutate({ id: editing.id, values: cleanPayload(v) })}
          onCancel={() => setEditing(null)} />}
      </Modal>
    </>
  )
}

function PlanForm({ defaultValues, onSubmit, onCancel, submitting }) {
  const isEdit = !!defaultValues?.id
  const { register, handleSubmit, watch, formState: { errors } } = useForm({
    defaultValues: {
      active: true, currency: 'USD', billingCycleDays: 30, trialDays: 14, yearlyDiscountPercent: 20,
      ...defaultValues,
    },
  })

  // Live preview only (the API is authoritative once saved): yearly override wins,
  // else monthly × 12 × (1 − discount%).
  const price = watch('price')
  const pct = watch('yearlyDiscountPercent')
  const override = watch('yearlyPrice')
  const previewYearly = (() => {
    if (override > 0) return override
    const m = Number(price)
    const d = Number(pct)
    if (!m || Number.isNaN(m)) return null
    return Math.round(m * 12 * (1 - (Number.isNaN(d) ? 0 : d) / 100) * 100) / 100
  })()

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="plan-form">
      {/* ── Identity ─────────────────────────────────── */}
      <FormSection title="Plan details">
        <div className="row g-3">
          <div className="col-md-4">
            <label className="form-label">Code <span className="text-danger">*</span></label>
            <input className={`form-control ${errors.code ? 'is-invalid' : ''}`} readOnly={isEdit} placeholder="PRO"
              {...register('code', { required: true, setValueAs: (v) => (v ? v.toUpperCase() : v) })} />
            <div className="form-text">{isEdit ? "Can't be changed after creation." : 'Unique, uppercase.'}</div>
          </div>
          <div className="col-md-8">
            <label className="form-label">Name <span className="text-danger">*</span></label>
            <input className={`form-control ${errors.name ? 'is-invalid' : ''}`} placeholder="Professional"
              {...register('name', { required: true })} />
          </div>
          <div className="col-12">
            <label className="form-label">Description</label>
            <input className="form-control" placeholder="Short summary shown to customers" {...register('description')} />
          </div>
        </div>
      </FormSection>

      {/* ── Pricing ──────────────────────────────────── */}
      <FormSection title="Pricing">
        <div className="row g-3">
          <div className="col-md-4">
            <label className="form-label">Monthly price</label>
            <div className="input-group">
              <span className="input-group-text">$</span>
              <input type="number" step="0.01" min="0" className="form-control" placeholder="0.00" {...register('price', { valueAsNumber: true })} />
            </div>
          </div>
          <div className="col-md-4">
            <label className="form-label">Yearly discount</label>
            <div className="input-group">
              <input type="number" min="0" max="100" className="form-control" {...register('yearlyDiscountPercent', { valueAsNumber: true, min: 0, max: 100 })} />
              <span className="input-group-text">%</span>
            </div>
            <div className="form-text">Default 20. Yearly = monthly × 12 × (1 − %).</div>
          </div>
          <div className="col-md-4">
            <label className="form-label">Yearly price override</label>
            <div className="input-group">
              <span className="input-group-text">$</span>
              <input type="number" step="0.01" min="0" className="form-control" placeholder="Auto" {...register('yearlyPrice', { valueAsNumber: true })} />
            </div>
            <div className="form-text">Optional — wins over the discount.</div>
          </div>
          {previewYearly != null && (
            <div className="col-12">
              <div className="rounded-3 px-3 py-2 small d-flex align-items-center gap-2"
                style={{ background: 'var(--hover-soft)', border: '1px solid var(--border-soft)' }}>
                <span className="text-muted">Yearly price</span>
                <span className="fw-semibold">{formatCurrency(previewYearly, watch('currency') || 'USD')}/yr</span>
                {!override && pct > 0 && <span className="badge text-bg-success">−{pct}%</span>}
                <span className="text-muted ms-auto">Server confirms the final value on save.</span>
              </div>
            </div>
          )}
        </div>
      </FormSection>

      {/* ── Limits & trial ───────────────────────────── */}
      <FormSection title="Limits & trial">
        <div className="row g-3">
          <div className="col-6 col-md-3">
            <label className="form-label">Currency</label>
            <input className="form-control" {...register('currency')} />
          </div>
          <div className="col-6 col-md-3">
            <label className="form-label">Billing cycle</label>
            <div className="input-group">
              <input type="number" className="form-control" {...register('billingCycleDays', { valueAsNumber: true })} />
              <span className="input-group-text">days</span>
            </div>
          </div>
          <div className="col-6 col-md-3">
            <label className="form-label">Trial</label>
            <div className="input-group">
              <input type="number" className="form-control" {...register('trialDays', { valueAsNumber: true })} />
              <span className="input-group-text">days</span>
            </div>
          </div>
          <div className="col-6 col-md-3">
            <label className="form-label">Max users</label>
            <input type="number" className="form-control" placeholder="Seats" {...register('maxUsers', { valueAsNumber: true })} />
          </div>
        </div>
      </FormSection>

      {/* ── Features & visibility ────────────────────── */}
      <FormSection title="Features & visibility" last>
        <div className="row g-3">
          <div className="col-12">
            <label className="form-label">Features</label>
            <input className="form-control" placeholder="Comma-separated, e.g. Feature A, Feature B" {...register('features')} />
          </div>
          <div className="col-12">
            <div className="form-check form-switch">
              <input type="checkbox" role="switch" className="form-check-input" id="active" {...register('active')} />
              <label className="form-check-label" htmlFor="active">
                {watch('active') ? 'Active' : 'Inactive'}
                <span className="text-muted small ms-2">
                  {watch('active')
                    ? 'Shown to clients and available to assign.'
                    : 'Retired — hidden from clients and can’t be assigned.'}
                </span>
              </label>
            </div>
          </div>
        </div>
      </FormSection>

      <div className="d-flex justify-content-end gap-2 pt-3 mt-1 border-top">
        <Button variant="light" type="button" onClick={onCancel}>Cancel</Button>
        <Button type="submit" loading={submitting}>{isEdit ? 'Save changes' : 'Create plan'}</Button>
      </div>
    </form>
  )
}

// Grouped section with a small heading and a divider, so the form reads in blocks.
function FormSection({ title, children, last }) {
  return (
    <div className={last ? 'mb-3' : 'mb-4'}>
      <div className="text-uppercase fw-semibold text-muted mb-2" style={{ fontSize: '0.72rem', letterSpacing: '0.06em' }}>{title}</div>
      {children}
    </div>
  )
}
