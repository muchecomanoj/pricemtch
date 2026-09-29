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

// GET /products/{id}/candidates + POST /candidates/{listingId}/review
export default function CompetitorsTab({
  productId, candidates, loading, canManage, onSearch, searching, onSelectListing,
}) {
  // Plain write buttons lock themselves while the plan is read-only.
  const writeLock = useWriteLock()
  const qc = useQueryClient()
  const { notify } = useNotification()

  const review = useMutation({
    mutationFn: ({ listingId, decision }) => productIntelService.review(listingId, decision),
    onSuccess: (_d, { decision }) => {
      notify.success(`Marked ${decision.toLowerCase()}`)
      // Reviewed matches change the stats and the estimate basis.
      qc.invalidateQueries({ queryKey: ['candidates', productId] })
      qc.invalidateQueries({ queryKey: ['marketPrices', productId] })
      qc.invalidateQueries({ queryKey: ['sales', productId] })
    },
    onError: (err) => notify.error(err?.message || 'Could not save the review.'),
  })

  const ai = useAiReview(productId)
  const unjudged = candidates.filter((c) => !c.aiVerdict).length

  if (loading) return <Card><SkeletonRows rows={5} /></Card>
  if (!candidates.length) {
    return <NeedsSearch onSearch={onSearch} canSearch={canManage} busy={searching} />
  }

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
      <div className="table-responsive">
        <table className="table table-hover align-middle mb-0">
          <thead>
            <tr className="text-muted small text-uppercase">
              <th style={{ width: '30%', minWidth: 240 }}>Listing</th>
              <th style={{ minWidth: 230 }}>Match assessment</th>
              <th className="text-nowrap">Marketplace</th>
              <th>Seller</th>
              <th className="text-nowrap">Landed price</th>
              <th className="text-nowrap">Est. margin</th>
              <th className="text-nowrap">Condition</th>
              <th className="text-nowrap">Match</th>
              <th className="text-end">Actions</th>
            </tr>
          </thead>
          <tbody>
            {candidates.map((c) => (
              <tr key={c.id}>
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
                <td><MarketplaceLogo marketplace={c.marketplace} storefront={c.storefront} /></td>
                <td>{c.seller || '—'}</td>
                <td className="text-nowrap"><LandedPrice listing={c} /></td>
                <td className="text-nowrap"><MarginCell listing={c} /></td>
                <td>{c.condition ? <StatusBadge status={c.condition} /> : '—'}</td>
                <td>{c.matchStatus ? <StatusBadge status={c.matchStatus} /> : '—'}</td>
                <td className="text-end">
                  <div className="d-inline-flex gap-1">
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
