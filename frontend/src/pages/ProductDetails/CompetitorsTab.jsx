import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useWriteLock } from '../../context/AuthContext'
import { FiExternalLink, FiCheck, FiX, FiCopy, FiBarChart2, FiAlertTriangle, FiCpu } from 'react-icons/fi'
import MarketplaceLogo from '../../components/common/MarketplaceLogo'
import Card from '../../components/common/Card'
import StatusBadge from '../../components/common/StatusBadge'
import { MatchScoreBadge, EvidenceChips } from '../../components/common/MatchEvidence'
import { MarginCell, LandedPrice } from '../../components/common/ListingCells'
import AiVerdict from '../../components/common/AiVerdict'
import Button from '../../components/common/Button'
import { useAiReview, estimateText } from '../../hooks/useAiReview'
import { SkeletonRows } from '../../components/common/Skeleton'
import { productIntelService } from '../../services/productIntelService'
import { useNotification } from '../../context/NotificationContext'
import { AsOf, NeedsSearch, fetchedAt } from './shared'
import { mpLabel } from '../../utils/marketplaces'
import { useStoredState } from '../../hooks/useStoredState'
import { errorMessage } from '../../services/apiError'

const NO_FILTERS = { q: '', status: '', market: '', ai: '', sort: '' }

const AI_OPTIONS = [
  ['', 'Any AI verdict'], ['MATCH', 'AI: likely match'], ['NOT_MATCH', 'AI: not a match'],
  ['UNCERTAIN', 'AI: unsure'], ['NONE', 'Not judged'],
]

// Prices are compared as numbers, so a list mixing £ and $ sorts by the figure,
// not by value — the currency stays printed on every row.
const SORTS = [
  ['', 'Default order'], ['score', 'Best match first'], ['priceAsc', 'Lowest price first'],
  ['priceDesc', 'Highest price first'], ['margin', 'Highest margin first'],
]

const priceOf = (c) => c.landedPrice ?? c.lastPrice
const distinct = (xs) => [...new Set(xs.filter(Boolean))].sort()
const titleCase = (s) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ')

function passes(c, f) {
  if (f.status && c.matchStatus !== f.status) return false
  if (f.market && c.marketplace !== f.market) return false
  if (f.ai === 'NONE' && c.aiVerdict) return false
  if (f.ai && f.ai !== 'NONE' && c.aiVerdict?.decision !== f.ai) return false
  const q = f.q.trim().toLowerCase()
  if (q && !`${c.title || ''} ${c.seller || ''}`.toLowerCase().includes(q)) return false
  return true
}

// Rows without the figure being sorted on always go last, whichever direction.
function sortRows(rows, sort) {
  const by = {
    score: (c) => c.aiVerdict?.score ?? c.matchScore,
    priceAsc: priceOf, priceDesc: priceOf, margin: (c) => c.estimatedMargin,
  }[sort]
  if (!by) return rows
  const dir = sort === 'priceAsc' ? 1 : -1
  return [...rows].sort((a, b) => {
    const x = by(a); const y = by(b)
    if (x == null) return y == null ? 0 : 1
    if (y == null) return -1
    return (Number(x) - Number(y)) * dir
  })
}

// GET /products/{id}/candidates + POST /candidates/{listingId}/review
export default function CompetitorsTab({
  productId, candidates, loading, canManage, onSearch, searching, onSelectListing,
}) {
  // Plain write buttons lock themselves while the plan is read-only.
  const writeLock = useWriteLock()
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()

  // Reviewed matches change the stats and the estimate basis.
  const refreshReviewed = () => {
    qc.invalidateQueries({ queryKey: ['candidates', productId] })
    qc.invalidateQueries({ queryKey: ['marketPrices', productId] })
    qc.invalidateQueries({ queryKey: ['sales', productId] })
  }

  const review = useMutation({
    mutationFn: ({ listingId, decision }) => productIntelService.review(listingId, decision),
    onSuccess: (_d, { decision }) => {
      notify.success(`Marked ${decision.toLowerCase()}`)
      refreshReviewed()
    },
    onError: (err) => notify.error(errorMessage(err, 'Could not save the review.')),
  })

  // Ticked rows. Ids that drop out of the list (a re-search) simply stop
  // matching anything, so the selection never needs pruning by hand.
  const [selected, setSelected] = useState(() => new Set())
  const toggle = (id) => setSelected((s) => {
    const next = new Set(s)
    if (next.has(id)) next.delete(id); else next.add(id)
    return next
  })

  // There is no bulk endpoint, so this is the single-row review once per row.
  // allSettled, not all: one refusal must not hide that the others went
  // through, and the rows that failed stay ticked to try again.
  const bulk = useMutation({
    mutationFn: async ({ rows, decision }) => {
      const results = await Promise.allSettled(
        rows.map((c) => productIntelService.review(c.id, decision)))
      return results.map((r, i) => ({ row: rows[i], ok: r.status === 'fulfilled', error: r.reason }))
    },
    onSuccess: (results, { decision }) => {
      refreshReviewed()
      const failed = results.filter((r) => !r.ok)
      const done = results.length - failed.length
      const verb = decision === 'MATCHED' ? 'Accepted' : 'Rejected'
      setSelected(new Set(failed.map((r) => r.row.id)))
      if (!failed.length) notify.success(`${verb} ${done} listing${done === 1 ? '' : 's'}`)
      else {
        notify.warn(`${verb} ${done} of ${results.length}. ${failed.length} failed: `
          + `${errorMessage(failed[0].error, 'could not save the review.')}`)
      }
    },
  })

  // Acts on the ticked rows that are on screen: a filter that hides a ticked
  // row must not let it be rejected unseen.
  const onBulk = async (decision, pool) => {
    const picked = pool.filter((c) => selected.has(c.id))
    // Blocked rows cannot be accepted, and rows already in that state need no call.
    const rows = picked.filter((c) => c.matchStatus !== decision
      && !(decision === 'MATCHED' && c.blockedReason))
    if (!rows.length) {
      notify.info('Nothing to change — the selected listings are already in that state.')
      return
    }
    if (decision === 'REJECTED') {
      const ok = await confirm({
        title: `Reject ${rows.length} listing${rows.length === 1 ? '' : 's'}?`,
        text: 'They stop counting toward price stats and sales estimates. You can accept any of them again later.',
        confirmText: 'Reject',
      })
      if (!ok) return
    }
    bulk.mutate({ rows, decision })
  }

  // The dropdowns and sort are remembered per user; the typed search is not.
  const [kept, setKept] = useStoredState('competitorsTab.filters',
    { status: '', market: '', ai: '', sort: '' })
  const [q, setQ] = useState('')
  const filters = { ...kept, q }
  const setFilter = ({ q: nextQ, ...rest }) => {
    if (nextQ !== undefined) setQ(nextQ)
    if (Object.keys(rest).length) setKept((f) => ({ ...f, ...rest }))
  }
  const setFilters = (all) => setFilter(all)

  const ai = useAiReview(productId)
  const unjudged = candidates.filter((c) => !c.aiVerdict).length

  if (loading) return <Card><SkeletonRows rows={5} /></Card>
  if (!candidates.length) {
    return <NeedsSearch onSearch={onSearch} canSearch={canManage} busy={searching} />
  }

  // Filtered and sorted in the browser — the whole list is already loaded.
  const statuses = distinct(candidates.map((c) => c.matchStatus))
  const markets = distinct(candidates.map((c) => c.marketplace))
  const visible = sortRows(candidates.filter((c) => passes(c, filters)), filters.sort)
  const filtered = visible.length !== candidates.length
  const filtering = JSON.stringify(filters) !== JSON.stringify(NO_FILTERS)

  const pickedCount = visible.filter((c) => selected.has(c.id)).length
  const allPicked = visible.length > 0 && pickedCount === visible.length
  const pickedBlocked = visible.filter((c) => selected.has(c.id) && c.blockedReason).length
  const toggleAll = () => setSelected((s) => {
    const next = new Set(s)
    visible.forEach((c) => (allPicked ? next.delete(c.id) : next.add(c.id)))
    return next
  })

  return (
    <Card title={`Competitor listings (${candidates.length})`}
      subtitle="Accept or reject each candidate — confirmed matches drive price stats and sales estimates."
      actions={canManage && unjudged > 0 && (
        // The ONLY thing that calls the model. Rendering this table never does:
        // each call is metered and takes roughly a minute per 20 pairs.
        <Button write variant="light" icon={FiCpu} loading={ai.running}
          onClick={() => ai.run(Math.min(ai.BATCH, unjudged))}>
          {ai.running
            ? `Judging ${ai.batch}…`
            : unjudged > ai.BATCH ? `AI review ${ai.BATCH} of ${unjudged}` : `AI review (${unjudged})`}
        </Button>
      )}>
      {ai.running && (
        <div className="field-note mb-3" style={{ maxWidth: 'none' }}>
          <FiCpu size={15} />
          <span>
            Judging {ai.batch} candidate{ai.batch === 1 ? '' : 's'} — {estimateText(ai.batch)}.
            Verdicts are advice only; nothing is accepted or rejected automatically.
          </span>
        </div>
      )}
      <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
        <input type="search" className="form-control form-control-sm" style={{ maxWidth: 220 }}
          placeholder="Search title or seller" aria-label="Search title or seller"
          value={filters.q} onChange={(e) => setFilter({ q: e.target.value })} />
        <select className="form-select form-select-sm w-auto" aria-label="Match status"
          value={filters.status} onChange={(e) => setFilter({ status: e.target.value })}>
          <option value="">All statuses</option>
          {statuses.map((s) => <option key={s} value={s}>{titleCase(s)}</option>)}
        </select>
        {markets.length > 1 && (
          <select className="form-select form-select-sm w-auto" aria-label="Marketplace"
            value={filters.market} onChange={(e) => setFilter({ market: e.target.value })}>
            <option value="">All marketplaces</option>
            {markets.map((m) => <option key={m} value={m}>{mpLabel(m)}</option>)}
          </select>
        )}
        <select className="form-select form-select-sm w-auto" aria-label="AI verdict"
          value={filters.ai} onChange={(e) => setFilter({ ai: e.target.value })}>
          {AI_OPTIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        <select className="form-select form-select-sm w-auto" aria-label="Sort by"
          value={filters.sort} onChange={(e) => setFilter({ sort: e.target.value })}>
          {SORTS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
        </select>
        {filtering && (
          <Button size="sm" variant="ghost" onClick={() => setFilters(NO_FILTERS)}>Clear filters</Button>
        )}
        {filtered && (
          <span className="text-muted small ms-auto">Showing {visible.length} of {candidates.length}</span>
        )}
      </div>
      {canManage && pickedCount > 0 && (
        <div className="d-flex flex-wrap align-items-center gap-2 mb-3 p-2 rounded"
          style={{ background: 'var(--hover-soft)' }}>
          <span className="fw-semibold small me-1">{pickedCount} selected</span>
          <Button write size="sm" icon={FiCheck} loading={bulk.isPending}
            disabled={pickedBlocked === pickedCount}
            onClick={() => onBulk('MATCHED', visible)}>Accept as match</Button>
          <Button write size="sm" variant="light" icon={FiX} loading={bulk.isPending}
            onClick={() => onBulk('REJECTED', visible)}>Reject</Button>
          <Button size="sm" variant="ghost" disabled={bulk.isPending}
            onClick={() => setSelected(new Set())}>Clear</Button>
          {/* Said before the click, not discovered after it. */}
          {pickedBlocked > 0 && (
            <span className="text-muted small d-inline-flex align-items-center gap-1">
              <FiAlertTriangle size={13} className="text-warning" />
              {pickedBlocked} can&apos;t be accepted and will be skipped
            </span>
          )}
        </div>
      )}
      <div className="table-responsive">
        <table className="table table-hover table-compact align-middle mb-0">
          <thead>
            <tr className="text-muted small text-uppercase">
              {canManage && (
                <th style={{ width: 36 }}>
                  <input type="checkbox" className="form-check-input" aria-label="Select all listings"
                    checked={allPicked}
                    ref={(el) => { if (el) el.indeterminate = pickedCount > 0 && !allPicked }}
                    onChange={toggleAll} />
                </th>
              )}
              <th style={{ width: '26%', minWidth: 200 }}>Listing</th>
              <th style={{ minWidth: 190 }}>Match assessment</th>
              <th className="text-nowrap">Marketplace / Seller</th>
              <th className="text-nowrap">Landed price</th>
              <th className="text-nowrap">Est. margin</th>
              <th className="text-nowrap">Status</th>
              <th className="text-end col-sticky-end">Actions</th>
            </tr>
          </thead>
          <tbody>
            {!visible.length && (
              <tr>
                <td colSpan={canManage ? 8 : 7} className="text-center text-muted py-4">
                  No listings match these filters.{' '}
                  <button type="button" className="btn btn-link btn-sm p-0 align-baseline"
                    onClick={() => setFilters(NO_FILTERS)}>Clear filters</button>
                </td>
              </tr>
            )}
            {visible.map((c) => (
              <tr key={c.id}>
                {canManage && (
                  <td>
                    <input type="checkbox" className="form-check-input" checked={selected.has(c.id)}
                      aria-label={`Select ${c.title || 'listing'}`} onChange={() => toggle(c.id)} />
                  </td>
                )}
                <td style={{ maxWidth: 0 }}>
                  <div className="fw-semibold clamp-2" title={c.title || ''}>{c.title || '—'}</div>
                  {/* Why this one can't be accepted, stated on the row rather
                      than left for the user to discover via a dead button. */}
                  {c.blockedReason && (
                    <div className="d-flex align-items-start gap-1 text-warning mt-1" style={{ fontSize: 11 }}>
                      <FiAlertTriangle size={12} className="flex-shrink-0 mt-1" />
                      <span>{c.blockedReason}</span>
                    </div>
                  )}
                  <div className="d-flex align-items-center gap-2">
                    <AsOf value={fetchedAt(c)} />
                    {c.url && (
                      <a href={c.url} target="_blank" rel="noreferrer"
                        className="small d-inline-flex align-items-center gap-1">
                        View <FiExternalLink size={11} />
                      </a>
                    )}
                  </div>
                  {/* Why the matcher scored it this way — conflicts always show. */}
                  <div className="mt-1"><EvidenceChips listing={c} max={3} /></div>
                </td>
                <td style={{ maxWidth: 0 }}>
                  <MatchScoreBadge score={c.matchScore} />
                  <div className="mt-1"><AiVerdict verdict={c.aiVerdict} /></div>
                </td>
                {/* The same ASIN can now be attached twice, once per
                    storefront, at two prices in two currencies. Without the
                    country these two rows read as a duplicate bug. */}
                {/* Seller sits under the marketplace, and condition under the
                    match status: nine columns pushed Accept / Reject off the
                    right edge of a laptop screen. */}
                <td>
                  <MarketplaceLogo marketplace={c.marketplace} storefront={c.storefront} />
                  <div className="text-muted small mt-1 text-truncate" style={{ maxWidth: 160 }}
                    title={c.seller || ''}>{c.seller || 'Seller unknown'}</div>
                </td>
                <td className="text-nowrap"><LandedPrice listing={c} /></td>
                <td className="text-nowrap"><MarginCell listing={c} /></td>
                <td>
                  <div className="d-flex flex-column align-items-start gap-1">
                    {c.matchStatus ? <StatusBadge status={c.matchStatus} /> : '—'}
                    {c.condition && <StatusBadge status={c.condition} />}
                  </div>
                </td>
                <td className="text-end col-sticky-end">
                  {/* Two by two: four buttons in a row made the pinned column
                      wide enough to cover Status on a laptop screen. */}
                  <div className="d-inline-grid gap-1" style={{ gridTemplateColumns: 'repeat(2, auto)' }}>
                    <button className="icon-btn" title="Price history"
                      onClick={() => onSelectListing(c)}><FiBarChart2 /></button>
                    {canManage && (
                      <>
                        {/* A blocked candidate cannot be accepted — e.g. a
                            multipack sharing an ASIN family is not comparable
                            to a single unit. Rejecting it is still valid. */}
                        <button className="icon-btn text-success" title={c.blockedReason || 'Accept as match'}
                          disabled={review.isPending || !!c.blockedReason}
                          onClick={() => review.mutate({ listingId: c.id, decision: 'MATCHED' })} {...writeLock}><FiCheck /></button>
                        <button className="icon-btn" title={c.blockedReason || 'Mark equivalent'}
                          disabled={review.isPending || !!c.blockedReason}
                          onClick={() => review.mutate({ listingId: c.id, decision: 'EQUIVALENT' })} {...writeLock}><FiCopy /></button>
                        <button className="icon-btn text-danger" title="Reject"
                          disabled={review.isPending}
                          onClick={() => review.mutate({ listingId: c.id, decision: 'REJECTED' })} {...writeLock}><FiX /></button>
                      </>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  )
}
