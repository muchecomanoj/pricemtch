import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FiPlus, FiTrash2, FiShield, FiInfo, FiAlertCircle } from 'react-icons/fi'
import { useAuth } from '../../context/AuthContext'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Modal from '../../components/common/Modal'
import DataTable from '../../components/tables/DataTable'
import EmptyState from '../../components/common/EmptyState'
import { pricingPolicyService, validatePolicy } from '../../services/pricingPolicyService'
import { productService } from '../../services/productService'
import { useMarketplaceCodes } from '../../hooks/useMarketplaces'
import { useNotification } from '../../context/NotificationContext'
import { mpLabel } from '../../utils/marketplaces'
import { formatCurrency } from '../../utils/format'

// FR-REC-002's bounded automation.
//
// A floor used to travel with a single Generate request, so if nobody sent one
// nothing limited the answer. That is fine while a person reads the result on
// screen and wrong the moment a price can publish itself — there is no one to
// notice a bad number. This screen is what makes automatic repricing safe
// enough to exist.

const EMPTY = {
  productId: null, marketplace: null,
  floorPrice: '', floorMarginPct: '', ceilingPrice: '',
  maxChangePct: '', maxChangeAmount: '', cooldownHours: '',
  autoPublish: false, active: true,
}

// Scope reads as words, never as ids — "Nike Air Max, Amazon" is checkable at a
// glance and "productId 412" is not.
const scopeLabel = (p, productTitle) => {
  const who = p.productId ? (productTitle || `Product #${p.productId}`) : 'All products'
  const where = p.marketplace ? mpLabel(p.marketplace) : 'all channels'
  return `${who}, ${where}`
}

// Number fields go out as numbers or not at all — '' is not zero, and sending
// it would set a floor of nothing.
const num = (v) => (v === '' || v == null ? null : Number(v))

function Field({ label, help, error, children }) {
  return (
    <div className="col-md-6">
      <label className="form-label">{label}</label>
      {children}
      {error
        ? <div className="text-danger small mt-1">{error}</div>
        : help && <div className="form-text">{help}</div>}
    </div>
  )
}

export default function PricingPolicies() {
  // Viewable while the plan is expired; nothing here can be changed until it is renewed.
  const { readOnly } = useAuth()
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()
  const { codes: marketplaces } = useMarketplaceCodes()
  const [editing, setEditing] = useState(null)   // policy object, or null
  const [form, setForm] = useState(EMPTY)

  const { data: policies = [], isLoading } = useQuery({
    queryKey: ['pricingPolicies'],
    queryFn: () => pricingPolicyService.list(),
  })

  // Names for the scope column and the picker.
  //
  // productService.list takes a 1-BASED page and subtracts one itself. Passing
  // 0 asked the backend for page -1, which returned nothing — so the dropdown
  // offered only "All products" and the scope column could never resolve a
  // title. Every other caller passes 1; this was the odd one out.
  const {
    data: productPage, isLoading: productsLoading, isError: productsError, error: productsErr,
  } = useQuery({
    queryKey: ['products', 'policy-names'],
    // size 100 matches every other picker in the app; 200 was an outlier with
    // no reason behind it.
    queryFn: () => productService.list({ page: 1, size: 100 }),
  })
  const products = productPage?.content ?? []
  const titleOf = (id) => products.find((p) => p.id === id)?.title

  useEffect(() => { if (editing) setForm({ ...EMPTY, ...editing }) }, [editing])

  const { errors, anyBound } = validatePolicy(form)

  const save = useMutation({
    mutationFn: () => pricingPolicyService.save({
      productId: form.productId || null,
      marketplace: form.marketplace || null,
      floorPrice: num(form.floorPrice),
      floorMarginPct: num(form.floorMarginPct),
      ceilingPrice: num(form.ceilingPrice),
      maxChangePct: num(form.maxChangePct),
      maxChangeAmount: num(form.maxChangeAmount),
      cooldownHours: num(form.cooldownHours),
      autoPublish: !!form.autoPublish,
      active: !!form.active,
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pricingPolicies'] })
      setEditing(null)
      notify.success('Policy saved')
    },
    onError: (e) => notify.error(e?.message || 'The policy could not be saved.'),
  })

  const remove = useMutation({
    mutationFn: (id) => pricingPolicyService.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['pricingPolicies'] })
      notify.success('Policy deleted')
    },
    onError: (e) => notify.error(e?.message || 'The policy could not be deleted.'),
  })

  const set = (patch) => setForm((f) => ({ ...f, ...patch }))

  const columns = [
    {
      key: 'scope',
      header: 'Applies to',
      minWidth: 240,
      render: (p) => (
        <>
          <div className="fw-semibold">{scopeLabel(p, titleOf(p.productId))}</div>
          {!p.active && <span className="text-muted small">Inactive</span>}
        </>
      ),
    },
    {
      key: 'floor',
      header: 'Minimum',
      className: 'text-nowrap',
      render: (p) => (
        <>
          {p.floorPrice != null && <div>{formatCurrency(p.floorPrice)}</div>}
          {p.floorMarginPct != null && <div className="text-muted small">{p.floorMarginPct}% margin</div>}
          {p.floorPrice == null && p.floorMarginPct == null && <span className="text-muted">—</span>}
        </>
      ),
    },
    {
      key: 'ceiling',
      header: 'Maximum',
      className: 'text-nowrap',
      render: (p) => (p.ceilingPrice != null
        ? formatCurrency(p.ceilingPrice) : <span className="text-muted">—</span>),
    },
    {
      key: 'change',
      header: 'Change limit',
      className: 'text-nowrap',
      render: (p) => (
        <>
          {p.maxChangePct != null && <div>{p.maxChangePct}%</div>}
          {p.maxChangeAmount != null && <div className="text-muted small">{formatCurrency(p.maxChangeAmount)}</div>}
          {p.maxChangePct == null && p.maxChangeAmount == null && <span className="text-muted">—</span>}
        </>
      ),
    },
    {
      key: 'cooldown',
      header: 'Cooldown',
      className: 'text-nowrap',
      render: (p) => (p.cooldownHours != null
        ? `${p.cooldownHours}h` : <span className="text-muted">—</span>),
    },
    {
      key: 'auto',
      header: 'Auto-publish',
      className: 'text-nowrap',
      render: (p) => (p.autoPublish
        ? <span className="mr-pill mr-pill--orange">On</span>
        : <span className="text-muted">Off</span>),
    },
    {
      key: 'actions',
      header: '',
      className: 'text-end',
      render: (p) => (
        <button className="icon-btn" disabled={readOnly} title={readOnly ? 'Renew to use this' : 'Delete policy'}
          onClick={async () => {
            const ok = await confirm({
              title: `Delete the policy for ${scopeLabel(p, titleOf(p.productId))}?`,
              text: 'Recommendations it covered will no longer be held to its limits.',
              confirmText: 'Delete',
            })
            if (ok) remove.mutate(p.id)
          }}>
          <FiTrash2 />
        </button>
      ),
    },
  ]

  return (
    <>
      <Card
        title={`Pricing policies (${policies.length})`}
        subtitle="The limits every price recommendation has to land inside."
        actions={<Button icon={FiPlus} disabled={readOnly} title={readOnly ? 'Renew to use this' : undefined} onClick={() => setEditing({ ...EMPTY })}>New policy</Button>}
      >
        {!isLoading && policies.length === 0 ? (
          <EmptyState icon={FiShield} title="No policies yet"
            message="Without a policy nothing limits a recommendation. That is workable while a person
                     reads each one, but a price cannot be published automatically until at least one
                     limit exists." />
        ) : (
          <DataTable columns={columns} rows={policies} loading={isLoading}
            onRowClick={(p) => setEditing(p)} />
        )}

        {policies.length > 0 && (
          <div className="field-note mt-3" style={{ maxWidth: 'none' }}>
            <FiInfo size={15} />
            <span>
              The most specific policy wins: a product on a channel beats that product everywhere,
              which beats the company on that channel, which beats the company default.
            </span>
          </div>
        )}
      </Card>

      <Modal show={!!editing} onClose={() => setEditing(null)} size="lg" scrollable
        title={editing?.id ? 'Edit pricing policy' : 'New pricing policy'}
        footer={(
          <>
            <Button variant="light" onClick={() => setEditing(null)}>Cancel</Button>
            <Button loading={save.isPending} disabled={Object.keys(errors).length > 0}
              onClick={() => save.mutate()}>Save policy</Button>
          </>
        )}>

        <div className="row g-3">
          <div className="col-12">
            <div className="form-label mb-0">Applies to</div>
          </div>

          <Field label="Product"
            help={productsLoading ? 'Loading your catalogue…' : 'Leave blank to apply to every product.'}
            error={productsError
              ? `Products could not be loaded: ${productsErr?.message || 'request failed'}`
              : (!productsLoading && products.length === 0
                ? 'No products found, so only a catalogue-wide policy can be set.'
                : null)}>
            <select className="form-select" value={form.productId || ''}
              disabled={productsLoading}
              onChange={(e) => set({ productId: e.target.value ? Number(e.target.value) : null })}>
              <option value="">All products</option>
              {products.map((p) => (
                <option key={p.id} value={p.id}>{p.title}</option>
              ))}
            </select>
          </Field>

          <Field label="Channel" help="Leave blank to apply to every channel.">
            <select className="form-select" value={form.marketplace || ''}
              onChange={(e) => set({ marketplace: e.target.value || null })}>
              <option value="">All channels</option>
              {marketplaces.map((m) => <option key={m} value={m}>{mpLabel(m)}</option>)}
            </select>
          </Field>

          <div className="col-12 pt-2">
            <div className="form-label mb-0 border-top-soft pt-3">Limits</div>
          </div>

          {/* The price range on ONE row. These are the two fields that bound
              each other, and separating them put "Minimum margin %" where the
              eye expects "Maximum price" — so a range test was typed into the
              wrong box and the validation correctly said nothing. */}
          <Field label="Minimum price" help="Never sell below this amount." error={errors.floorPrice}>
            <input type="number" min="0" step="0.01"
              className={`form-control ${errors.floorPrice ? 'is-invalid' : ''}`}
              value={form.floorPrice} onChange={(e) => set({ floorPrice: e.target.value })} />
          </Field>

          <Field label="Maximum price" help="Never go above this." error={errors.ceilingPrice}>
            <input type="number" min="0" step="0.01"
              className={`form-control ${errors.ceilingPrice ? 'is-invalid' : ''}`}
              value={form.ceilingPrice} onChange={(e) => set({ ceilingPrice: e.target.value })} />
          </Field>

          <Field label="Minimum margin %" help="Never sell below this margin over break-even.">
            <input type="number" min="0" step="0.1" className="form-control"
              value={form.floorMarginPct} onChange={(e) => set({ floorMarginPct: e.target.value })} />
          </Field>

          <div className="col-12">
            <div className="field-note" style={{ maxWidth: 'none' }}>
              <FiInfo size={15} />
              <span>
                Both floors can be set together and the higher one wins at the moment of calculation.
                A fixed floor cannot know today&apos;s costs; a margin floor cannot express
                &ldquo;never below what we paid&rdquo;.
              </span>
            </div>
          </div>

          <Field label="Max change per cycle (%)" help="How far one price move may go.">
            <input type="number" min="0" step="0.1" className="form-control"
              value={form.maxChangePct} onChange={(e) => set({ maxChangePct: e.target.value })} />
          </Field>

          <Field label="Max change per cycle (amount)" help="The same limit as a fixed amount.">
            <input type="number" min="0" step="0.01" className="form-control"
              value={form.maxChangeAmount} onChange={(e) => set({ maxChangeAmount: e.target.value })} />
          </Field>

          <Field label="Cooldown (hours)"
            help="Minimum wait between two price changes on the same product.">
            <input type="number" min="0" step="1" className="form-control"
              value={form.cooldownHours} onChange={(e) => set({ cooldownHours: e.target.value })} />
          </Field>

          <div className="col-12">
            <div className="field-note" style={{ maxWidth: 'none' }}>
              <FiInfo size={15} />
              <span>
                When both change limits are set the tighter one applies. This is not the same
                protection as a floor: a floor stops one bad recommendation, a change limit stops a
                run of individually reasonable ones walking the price down over several days. The
                cooldown stops the system reacting to its own last move before the market has
                responded to it.
              </span>
            </div>
          </div>

          <div className="col-12 pt-2">
            <div className="form-label mb-0 border-top-soft pt-3">Automation</div>
          </div>

          <div className="col-12">
            <div className="form-check">
              {/* Disabled until a bound exists — the backend rejects it with a
                  400, and the reason is worth showing before the save fails. */}
              <input className="form-check-input" type="checkbox" id="pp-auto"
                disabled={!anyBound} checked={!!form.autoPublish}
                onChange={(e) => set({ autoPublish: e.target.checked })} />
              <label className="form-check-label" htmlFor="pp-auto">
                <strong>Publish automatically</strong>
                <div className="form-text mb-0">Recommendations go live without approval.</div>
              </label>
            </div>
            {!anyBound && (
              <div className="d-inline-flex align-items-start gap-1 small mt-2">
                <FiAlertCircle size={14} className="flex-shrink-0 mt-1" style={{ color: 'var(--warning)' }} />
                Set at least one limit before turning this on.
              </div>
            )}
            {errors.autoPublish && <div className="text-danger small mt-2">{errors.autoPublish}</div>}
          </div>

          <div className="col-12">
            <div className="form-check">
              <input className="form-check-input" type="checkbox" id="pp-active"
                checked={!!form.active} onChange={(e) => set({ active: e.target.checked })} />
              <label className="form-check-label" htmlFor="pp-active">
                <strong>Active</strong>
                <div className="form-text mb-0">Uncheck to keep the policy but stop applying it.</div>
              </label>
            </div>
          </div>
        </div>
      </Modal>
    </>
  )
}
