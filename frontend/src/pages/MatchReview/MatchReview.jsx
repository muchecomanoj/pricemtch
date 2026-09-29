import { useCallback, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import {
  Check, X, Equal, CheckSquare, Square, Sparkles, Layers, ShieldCheck, AlertTriangle,
  ArrowUpDown, ArrowLeft,
} from 'lucide-react'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Pagination from '../../components/common/Pagination'
import EmptyState from '../../components/common/EmptyState'
import { SkeletonCard } from '../../components/common/Skeleton'
import MatchReviewCard from './MatchReviewCard'
import ComboInput from '../../components/forms/ComboInput'
import { competitorListingService } from '../../services/competitorListingService'
import { productIntelService } from '../../services/productIntelService'
import { productService } from '../../services/productService'
import { useNotification } from '../../context/NotificationContext'
import { useAuth } from '../../context/AuthContext'

// Cross-product review queue — FR-MATCH-003. GET /match-review returns only
// pending candidates, already scored, so the reviewer works a ranked list
// instead of opening products one at a time.
//   Decision: POST /candidates/{listingId}/review { decision }
const PAGE_SIZE = 12
const SORTS = [
  ['score', 'Best match'],
  ['lowest', 'Riskiest'],
  ['recent', 'Newest'],
]

export default function MatchReview() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { notify } = useNotification()
  const { can } = useAuth()
  const canReview = can('AI_MATCH') || can('MANAGE_PRODUCTS')

  const [page, setPage] = useState(1)   // UI 1-based, API 0-based
  const [sort, setSort] = useState('score')
  const [productLabel, setProductLabel] = useState('')

  // /match-review has no free-text search, but it does accept productId — so a
  // searchable product picker gives the same result server-side, and keeps the
  // ranking that a text search over /competitor-listings would lose.
  const productsQ = useQuery({
    queryKey: ['products', 'selector'],
    queryFn: () => productService.list({ page: 1, size: 100 }),
  })
  const products = useMemo(() => productsQ.data?.content ?? [], [productsQ.data])
  const labelFor = (p) => (p.sku ? `${p.title} — ${p.sku}` : p.title)
  const byLabel = useMemo(() => new Map(products.map((p) => [labelFor(p), p.id])), [products])
  const productId = byLabel.get(productLabel)
  const [bulkMode, setBulkMode] = useState(false)
  const [selected, setSelected] = useState(() => new Set())
  const [deciding, setDeciding] = useState(() => new Set()) // ids mid-flight

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['matchReview', { page, sort, productId }],
    queryFn: () => competitorListingService.reviewQueue({
      page: page - 1, size: PAGE_SIZE, sort, productId,
    }),
    placeholderData: keepPreviousData,
  })

  const rows = useMemo(() => data?.content ?? [], [data])

  const refresh = useCallback(() => {
    // The decided item leaves the queue and every match-derived stat shifts.
    qc.invalidateQueries({ queryKey: ['matchReview'] })
    qc.invalidateQueries({ queryKey: ['competitorListings'] })
    qc.invalidateQueries({ queryKey: ['candidates'] })
    qc.invalidateQueries({ queryKey: ['marketPrices'] })
  }, [qc])

  const decide = useMutation({
    mutationFn: ({ listingId, decision }) => productIntelService.review(listingId, decision),
    onMutate: ({ listingId }) => setDeciding((s) => new Set(s).add(listingId)),
    onSuccess: (_d, { decision }) => { notify.success(`Marked ${decision.toLowerCase()}`); refresh() },
    onError: (err) => notify.error(err?.message || 'Could not save the decision.'),
    onSettled: (_d, _e, { listingId }) => setDeciding((s) => {
      const n = new Set(s); n.delete(listingId); return n
    }),
  })

  // Bulk is N sequential calls — there is no batch review endpoint. Report what
  // actually succeeded rather than assuming the whole set went through.
  const bulk = useMutation({
    mutationFn: async (decision) => {
      const ids = [...selected]
      const results = await Promise.allSettled(
        ids.map((id) => productIntelService.review(id, decision)),
      )
      return { decision, ok: results.filter((r) => r.status === 'fulfilled').length, total: ids.length }
    },
    onSuccess: ({ decision, ok, total }) => {
      setSelected(new Set())
      if (ok === total) notify.success(`${ok} marked ${decision.toLowerCase()}`)
      else notify.error(`${ok} of ${total} saved — ${total - ok} failed.`)
      refresh()
    },
    onError: (err) => notify.error(err?.message || 'Bulk update failed.'),
  })

  const runDecide = useCallback((listingId, decision) => {
    decide.mutate({ listingId, decision })
  }, [decide])

  const toggleSelect = useCallback((id) => setSelected((s) => {
    const n = new Set(s)
    n.has(id) ? n.delete(id) : n.add(id)
    return n
  }), [])

  const bands = useMemo(() => ({
    strong: rows.filter((r) => r.matchScore >= 85).length,
    medium: rows.filter((r) => r.matchScore >= 70 && r.matchScore < 85).length,
    weak: rows.filter((r) => r.matchScore != null && r.matchScore < 70).length,
  }), [rows])

  const allSelected = rows.length > 0 && rows.every((r) => selected.has(r.id))

  return (
    <div className="mr-page">
      {/* ── Header + KPI tiles ─────────────────────────────────── */}
      <div className="d-flex flex-wrap align-items-end justify-content-between gap-3 mb-4">
        <div className="d-flex align-items-start gap-3">
          {/* This page uses a custom header, so the back control PageHeader
              normally provides has to be wired here. */}
          <button type="button" className="icon-btn flex-shrink-0 mt-1"
            onClick={() => (window.history.state?.idx > 0 ? navigate(-1) : navigate('/dashboard'))}
            title="Go back" aria-label="Go back">
            <ArrowLeft size={16} />
          </button>
          <div>
            <div className="mr-eyebrow mb-2">Catalog intelligence</div>
            <h1 className="mr-title mb-1">Match Review</h1>
            <p className="text-muted mb-0" style={{ fontSize: '0.95rem' }}>
              Candidate listings awaiting a decision, ranked by match confidence.
            </p>
          </div>
        </div>
      </div>

      <div className="row g-3 mb-4">
        <Kpi icon={Layers} tone="blue" value={data?.totalElements ?? 0} label="Pending review" />
        <Kpi icon={ShieldCheck} tone="green" value={bands.strong} label="Strong on this page" />
        <Kpi icon={Sparkles} tone="orange" value={bands.medium} label="Needs a look" />
        <Kpi icon={AlertTriangle} tone="red" value={bands.weak} label="Weak on this page" />
      </div>

      {/* ── Sticky glass toolbar ───────────────────────────────── */}
      <div className="mr-toolbar">
        <div className="d-flex flex-wrap align-items-center justify-content-between gap-2">
          <div className="d-flex flex-wrap align-items-center gap-2">
            <div style={{ width: 250 }}>
              <ComboInput value={productLabel}
                onChange={(v) => { setProductLabel(v); setPage(1) }}
                options={[...byLabel.keys()]}
                placeholder="All products"
                emptyLabel="All products"
                disabled={productsQ.isLoading}
                strict clearable />
            </div>
            <ArrowUpDown size={14} className="text-muted d-none d-sm-block" />
            <div className="mr-segment" role="group" aria-label="Sort order">
              {SORTS.map(([v, label]) => (
                <button key={v} type="button" aria-pressed={sort === v}
                  className={`mr-segment__btn ${sort === v ? 'is-active' : ''}`}
                  onClick={() => { setSort(v); setPage(1) }}>
                  {label}
                </button>
              ))}
            </div>
          </div>

          {canReview && rows.length > 0 && (
            <Button variant="light" size="sm" icon={bulkMode ? CheckSquare : Square}
              onClick={() => { setBulkMode((b) => !b); setSelected(new Set()) }}>
              {bulkMode ? 'Exit bulk' : 'Bulk select'}
            </Button>
          )}
        </div>

        {/* Bulk action bar — only while bulk mode is on. */}
        {bulkMode && (
          <div className="d-flex flex-wrap align-items-center gap-2 mt-2 pt-2 border-top-soft">
            <button type="button" className="btn btn-sm btn-ghost"
              onClick={() => setSelected(allSelected ? new Set() : new Set(rows.map((r) => r.id)))}>
              {allSelected ? 'Clear all' : `Select all ${rows.length}`}
            </button>
            <span className="mr-pill mr-pill--blue">{selected.size} selected</span>
            <div className="d-flex flex-wrap gap-2 ms-auto">
              <Button write size="sm" variant="success" icon={Check} loading={bulk.isPending}
                disabled={!selected.size} onClick={() => bulk.mutate('MATCHED')}>Match</Button>
              <Button write size="sm" variant="light" icon={Equal} loading={bulk.isPending}
                disabled={!selected.size} onClick={() => bulk.mutate('EQUIVALENT')}>Equivalent</Button>
              <Button write size="sm" variant="light" icon={X} loading={bulk.isPending} className="text-danger"
                disabled={!selected.size} onClick={() => bulk.mutate('REJECTED')}>Reject</Button>
            </div>
          </div>
        )}
      </div>

      {/* ── Queue ──────────────────────────────────────────────── */}
      {isError ? (
        <Card>
          <div className="alert alert-danger py-2 small mb-0">
            {error?.message || 'Could not load the review queue.'}
          </div>
        </Card>
      ) : isLoading ? (
        <div className="row g-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div className="col-12 col-xl-6 col-xxl-4" key={i}><SkeletonCard /></div>
          ))}
        </div>
      ) : rows.length === 0 ? (
        <Card>
          <EmptyState icon={Sparkles}
            title={productId ? 'Nothing pending for this product' : "You're all caught up"}
            message={productId
              ? 'Clear the product filter to see candidates across the rest of your catalog.'
              : 'Every candidate has been reviewed. Run a search on a product to find more competitors.'} />
        </Card>
      ) : (
        <>
          <div className="row g-4">
            {rows.map((r, i) => (
              <div className="col-12 col-xl-6 col-xxl-4" key={r.id}>
                <MatchReviewCard
                  r={r}
                  index={i}
                  canReview={canReview}
                  deciding={deciding.has(r.id) || bulk.isPending}
                  bulkMode={bulkMode}
                  selected={selected.has(r.id)}
                  onToggleSelect={toggleSelect}
                  onDecide={runDecide}
                />
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
        </>
      )}
    </div>
  )
}

// Premium stat tile — icon, large number, subtitle.
const KPI_TONE = {
  blue: { bg: 'rgba(63,169,245,0.12)', fg: '#3fa9f5' },
  green: { bg: 'rgba(16,185,129,0.14)', fg: '#10b981' },
  orange: { bg: 'rgba(245,158,11,0.16)', fg: '#f59e0b' },
  red: { bg: 'rgba(239,68,68,0.12)', fg: '#ef4444' },
}

function Kpi({ icon: Icon, tone, value, label }) {
  const t = KPI_TONE[tone] || KPI_TONE.blue
  return (
    <div className="col-6 col-lg-3">
      <div className="mr-kpi">
        <span className="mr-kpi__icon" style={{ background: t.bg, color: t.fg }}>
          <Icon size={21} />
        </span>
        <div style={{ minWidth: 0 }}>
          <div className="mr-kpi__value">{value}</div>
          <div className="mr-kpi__label text-truncate">{label}</div>
        </div>
      </div>
    </div>
  )
}
