import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import { useState, useMemo } from 'react'
import { FiCheck, FiX, FiZap, FiArrowRight, FiUploadCloud, FiRotateCcw,
} from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Pagination from '../../components/common/Pagination'
import EmptyState from '../../components/common/EmptyState'
import StatusBadge from '../../components/common/StatusBadge'
import { BoundNote, recStatusLabel } from '../../components/common/RecommendationBound'
import { SkeletonCard } from '../../components/common/Skeleton'
import ComboInput from '../../components/forms/ComboInput'
import { recommendationService } from '../../services/recommendationService'
import { productIntelService } from '../../services/productIntelService'
import { productService } from '../../services/productService'
import { useNotification } from '../../context/NotificationContext'
import { useAuth } from '../../context/AuthContext'
import { formatCurrency, formatRelativeTime } from '../../utils/format'

// GET /recommendations?status=DRAFT — the pending queue.
// POST /recommendations/generate | /{id}/approve | /{id}/reject
const PAGE_SIZE = 12
const RISK_TONE = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' }
// GET /recommendations ?status= — the full lifecycle, so approved and published
// recommendations stay reachable instead of vanishing once decided.
// PENDING_APPROVAL sits between DRAFT and APPROVED: submitted by whoever drafted
// it, awaiting someone with authority to approve. Omitting it here hid every
// recommendation currently waiting on a decision.
const STATUSES = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PUBLISHED', 'REJECTED', 'EXPIRED']
const PAST_TENSE = {
  approve: 'approved', publish: 'published', reject: 'rejected', rollback: 'rolled back',
}

export default function Recommendations() {
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()
  const { can } = useAuth()
  const canManage = can('MANAGE_PRICING')
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState('DRAFT')
  const [productLabel, setProductLabel] = useState('')

  // GET /recommendations has no productId param, but the per-product endpoint
  // returns that product's FULL list — so filtering by product means switching
  // endpoints rather than filtering a page client-side, which would only ever
  // cover the visible rows.
  const productsQ = useQuery({
    queryKey: ['products', 'selector'],
    queryFn: () => productService.list({ page: 1, size: 100 }),
  })
  const products = useMemo(() => productsQ.data?.content ?? [], [productsQ.data])
  const labelFor = (p) => (p.sku ? `${p.title} — ${p.sku}` : p.title)
  const byLabel = useMemo(() => new Map(products.map((p) => [labelFor(p), p.id])), [products])
  const productId = byLabel.get(productLabel)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['recommendations', { page, status, productId }],
    queryFn: async () => {
      if (!productId) return recommendationService.list({ status, page: page - 1, size: PAGE_SIZE })
      // Per-product returns every status unpaged; narrow it to the chosen one
      // and normalise to the same page shape the table expects.
      const all = await productIntelService.recommendations(productId)
      const rows = all.filter((r) => r.status === status)
      return { content: rows, totalElements: rows.length, totalPages: 1, number: 0, size: rows.length }
    },
    placeholderData: keepPreviousData,
  })

  const refresh = () => qc.invalidateQueries({ queryKey: ['recommendations'] })

  const generate = useMutation({
    mutationFn: () => recommendationService.generate(),
    onSuccess: (res) => {
      const n = res?.generated ?? res?.count
      notify.success(n != null ? `${n} recommendation${n === 1 ? '' : 's'} generated` : 'Recommendations generated')
      refresh()
    },
    onError: (err) => notify.error(err?.message || 'Could not generate recommendations.'),
  })

  const decide = useMutation({
    mutationFn: ({ id, action }) => {
      if (action === 'approve') return recommendationService.approve(id)
      if (action === 'publish') return recommendationService.publish(id)
    if (action === 'rollback') return recommendationService.rollback(id)
      return recommendationService.reject(id)
    },
    onSuccess: (_d, { action }) => {
      notify.success(`Recommendation ${PAST_TENSE[action]}`)
      refresh()
      // Publishing writes the recommended price onto the product, so anything
      // derived from ourPrice — margin, break-even, the KPI row — is now stale.
      if (action === 'publish') {
        qc.invalidateQueries({ queryKey: ['product'] })
        qc.invalidateQueries({ queryKey: ['products'] })
        qc.invalidateQueries({ queryKey: ['profitability'] })
      }
    },
    onError: (err) => notify.error(err?.message || 'Action failed.'),
  })

  // Rejecting is the one decision with no way back — there is no REJECTED→DRAFT
  // transition, so confirm it the way product deletion does.
  const onAction = async (id, action) => {
    if (action === 'reject') {
      const ok = await confirm({
        title: 'Reject this recommendation?',
        text: 'It will leave the pending queue. You would need to generate a new one to get it back.',
        confirmText: 'Reject',
      })
      if (!ok) return
    }
    decide.mutate({ id, action })
  }

  const rows = data?.content ?? []

  return (
    <>
      <PageHeader
        title="Recommendations"
        subtitle={`${data?.totalElements ?? 0} pending price recommendation${data?.totalElements === 1 ? '' : 's'}.`}
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Recommendations' }]}
        actions={(
          // flex-nowrap so the button and filter stay on one line — the long
          // subtitle was pushing them onto separate rows.
          <div className="d-flex flex-wrap align-items-center gap-2">
            {canManage && (
              <Button write icon={FiZap} loading={generate.isPending} onClick={() => generate.mutate()}
                className="flex-shrink-0">
                Generate
              </Button>
            )}
            {/* Data-driven list → searchable ComboInput. Status below is a short
                fixed list, so it stays a native select. */}
            <div style={{ width: 230 }}>
              <ComboInput value={productLabel}
                onChange={(v) => { setProductLabel(v); setPage(1) }}
                options={[...byLabel.keys()]}
                placeholder="All products" emptyLabel="All products"
                disabled={productsQ.isLoading} strict clearable />
            </div>
            <select className="form-select flex-shrink-0" style={{ width: 150 }} value={status}
              aria-label="Filter by status"
              onChange={(e) => { setStatus(e.target.value); setPage(1) }}>
              {STATUSES.map((s) => (
                <option key={s} value={s}>{s[0] + s.slice(1).toLowerCase()}</option>
              ))}
            </select>
          </div>
        )}
      />

      {isError ? (
        <Card>
          <div className="alert alert-danger py-2 small mb-0">
            {error?.message || 'Could not load recommendations.'}
          </div>
        </Card>
      ) : isLoading ? (
        <div className="row g-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div className="col-12 col-lg-6" key={i}><SkeletonCard /></div>
          ))}
        </div>
      ) : rows.length === 0 ? (
        <Card>
          <EmptyState icon={FiZap} title={`No ${status.toLowerCase()} recommendations`}
            message={productId
              ? 'Nothing at this status for the selected product — clear the product filter or try another status.'
              : status !== 'DRAFT'
                ? 'Nothing has reached this state yet — switch the filter to see others.'
                : canManage
                  ? 'Generate a batch once products have competitor matches and a cost profile.'
                  : 'A manager can generate price recommendations.'}
            action={canManage && status === 'DRAFT' && !productId && (
              <Button write icon={FiZap} loading={generate.isPending} onClick={() => generate.mutate()}>
                Generate recommendations
              </Button>
            )} />
        </Card>
      ) : (
        <>
          <div className="row g-3">
            {rows.map((r) => (
              <div className="col-12 col-lg-6" key={r.id}>
                <RecommendationCard r={r} canManage={canManage} busy={decide.isPending}
                  onDecide={(action) => onAction(r.id, action)} />
              </div>
            ))}
          </div>

          {(data.totalPages ?? 1) > 1 && (
            <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mt-4">
              <span className="text-muted small">
                Showing <strong>{(page - 1) * PAGE_SIZE + 1}</strong>–
                <strong>{(page - 1) * PAGE_SIZE + rows.length}</strong> of <strong>{data.totalElements}</strong>
              </span>
              <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />
            </div>
          )}

          <p className="form-text mt-3 mb-0">
            Publishing sets the recommended price as the product&apos;s selling price. It does
            <strong> not</strong> change the price on the marketplace — do that in your seller account.
          </p>
        </>
      )}
    </>
  )
}

function RecommendationCard({ r, canManage, busy, onDecide }) {
  const ccy = r.currency || 'USD'
  const delta = r.currentPrice != null && r.recommendedPrice != null
    ? r.recommendedPrice - r.currentPrice
    : null

  return (
    <Card hover className="h-100">
      <div className="d-flex align-items-start justify-content-between gap-2 mb-3">
        <div style={{ minWidth: 0 }}>
          {r.productId ? (
            <Link to={`/products/${r.productId}`}
              className="fw-semibold text-decoration-none d-inline-flex align-items-center gap-1">
              {r.productTitle || `#${r.productId}`} <FiArrowRight size={13} />
            </Link>
          ) : (
            <span className="fw-semibold">{r.productTitle || '—'}</span>
          )}
          {r.createdAt && (
            <div className="text-muted" style={{ fontSize: 11 }}>{formatRelativeTime(r.createdAt)}</div>
          )}
        </div>
        <div className="text-end d-flex flex-column align-items-end gap-1">
          {r.riskLevel && (
            <StatusBadge status={RISK_TONE[r.riskLevel] || 'secondary'} label={`${r.riskLevel} risk`} />
          )}
          {/* Ends REJECTED so the audit keeps the fact that it went live — the
              list must not then read as though it was simply turned down. */}
          {r.rolledBackAt && (
            <span className="mr-pill mr-pill--orange">{recStatusLabel(r)}</span>
          )}
        </div>
      </div>

      <div className="d-flex flex-wrap gap-4 mb-3">
        <Figure label="Current" value={r.currentPrice == null ? '—' : formatCurrency(r.currentPrice, ccy)} />
        <div>
          <Figure label="Recommended" tone="primary"
            value={r.recommendedPrice == null ? '—' : formatCurrency(r.recommendedPrice, ccy)} />
          <BoundNote rec={r} />
        </div>
        {delta != null && (
          <Figure label="Change" tone={delta >= 0 ? 'success' : 'danger'}
            value={`${delta >= 0 ? '+' : '−'}${formatCurrency(Math.abs(delta), ccy)}`} />
        )}
        <Figure label="Confidence"
          value={r.confidence == null ? '—' : `${Math.round(r.confidence > 1 ? r.confidence : r.confidence * 100)}%`} />
      </div>

      <div className="d-flex flex-wrap gap-3 mb-3 text-muted small">
        {/* At the CAPPED price, not the one the engine wanted — so a capped
            recommendation may sit under target, which is the correct figure. */}
        {r.expectedMarginPct != null && <span>Expected margin <strong>{r.expectedMarginPct.toFixed(1)}%</strong></span>}
        {r.breakEvenPrice != null && <span>Break-even <strong>{formatCurrency(r.breakEvenPrice, ccy)}</strong></span>}
        {r.competitorMedian != null && <span>Competitor median <strong>{formatCurrency(r.competitorMedian, ccy)}</strong></span>}
      </div>

      {r.rationale && <p className="text-muted small mb-3">{r.rationale}</p>}

      {/* Actions follow the lifecycle: approve a DRAFT, publish an APPROVED.
          PUBLISHED / REJECTED / EXPIRED are terminal — nothing to act on. */}
      {canManage && r.rollbackable && (
        <div className="d-flex flex-wrap gap-2 mt-auto">
          <Button write size="sm" variant="light" icon={FiRotateCcw} disabled={busy}
            onClick={() => onDecide('rollback')}>Roll back</Button>
          <span className="text-muted small align-self-center">
            {r.previousPrice != null
              ? `Restores ${formatCurrency(r.previousPrice, ccy)}`
              : 'Restores the price this replaced'}
          </span>
        </div>
      )}

      {canManage && ['DRAFT', 'APPROVED'].includes(r.status) && (
        <div className="d-flex flex-wrap gap-2 mt-auto">
          {r.status === 'DRAFT' && (
            <Button write size="sm" variant="success" icon={FiCheck} disabled={busy}
              onClick={() => onDecide('approve')}>Approve</Button>
          )}
          {r.status === 'APPROVED' && (
            <Button write size="sm" variant="success" icon={FiUploadCloud} disabled={busy}
              onClick={() => onDecide('publish')}>Publish</Button>
          )}
          <Button write size="sm" variant="light" icon={FiX} disabled={busy} className="text-danger"
            onClick={() => onDecide('reject')}>Reject</Button>
        </div>
      )}
    </Card>
  )
}

function Figure({ label, value, tone }) {
  return (
    <div>
      <div className="text-muted small">{label}</div>
      <div className={`h5 mb-0 ${tone ? `text-${tone}` : ''}`}>{value}</div>
    </div>
  )
}
