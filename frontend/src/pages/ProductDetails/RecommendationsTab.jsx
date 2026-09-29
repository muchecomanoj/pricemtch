import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { FiZap, FiCheck, FiUploadCloud, FiX, FiInfo, FiAlertTriangle, FiRotateCcw } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import EmptyState from '../../components/common/EmptyState'
import StatusBadge from '../../components/common/StatusBadge'
import { BoundNote, recStatusLabel } from '../../components/common/RecommendationBound'
import { productIntelService } from '../../services/productIntelService'
import { useNotification } from '../../context/NotificationContext'
import { money, pctOf, Stat } from './shared'
import { formatRelativeTime } from '../../utils/format'

const PAST_TENSE = { rollback: 'rolled back', submit: 'submitted for approval', approve: 'approved', publish: 'published', reject: 'rejected' }

// GET/POST /products/{id}/price-recommendations + approve / publish / reject
export default function RecommendationsTab({
  productId, recommendations, loading, canManagePricing, costConfigured, costSource,
}) {
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()
  const [targetMargin, setTargetMargin] = useState(20)

  const refresh = () => qc.invalidateQueries({ queryKey: ['recommendations', productId] })

  const generate = useMutation({
    mutationFn: () => productIntelService.generateRecommendation(productId, {
      targetMarginPct: Number(targetMargin),
    }),
    onSuccess: () => { notify.success('Recommendation generated'); refresh() },
    onError: (err) => notify.error(err?.message || 'Could not generate a recommendation.'),
  })

  // Generating with a draft already pending REPLACES it — the backend updates
  // the existing record rather than adding a second. So there is nothing to
  // warn about and nothing to confirm: regenerating after costs or competitor
  // prices move is the normal way to use this.
  const onGenerate = () => generate.mutate()

  const act = useMutation({
    mutationFn: ({ id, action }) => {
      if (action === 'submit') return productIntelService.submitRecommendation(id)
      if (action === 'approve') return productIntelService.approveRecommendation(id)
      if (action === 'publish') return productIntelService.publishRecommendation(id)
      if (action === 'rollback') return productIntelService.rollbackRecommendation(id)
      return productIntelService.rejectRecommendation(id)
    },
    onSuccess: (_d, { action }) => {
      notify.success(`Recommendation ${PAST_TENSE[action]}`)
      refresh()
      // Publishing writes the price onto the product, so the hero KPIs and every
      // profit figure derived from ourPrice need refetching.
      if (action === 'publish' || action === 'rollback') {
        qc.invalidateQueries({ queryKey: ['product', productId] })
        qc.invalidateQueries({ queryKey: ['profitability', productId] })
      }
    },
    // A publish can be refused by the cooldown. The backend writes that message
    // for a person to read, so it is shown as-is — and the recommendation is
    // NOT consumed by the refusal: it stays approved and publishable later, so
    // nothing here disables the button.
    onError: (err) => notify.error(err?.message || 'Action failed.'),
  })

  // No REJECTED→DRAFT transition exists, so confirm before discarding.
  const onAction = async (id, action, rec) => {
    if (action === 'rollback') {
      // Naming the price it restores is the whole point of the confirmation —
      // "are you sure" without the number asks nothing answerable.
      const ok = await confirm({
        title: 'Roll back this price change?',
        text: rec?.previousPrice != null
          ? `This will set the price back to ${money(rec.previousPrice, rec.currency)}.`
          : 'This will restore the price that was in place before it was published.',
        confirmText: 'Roll back',
      })
      if (!ok) return
    }
    if (action === 'reject') {
      const ok = await confirm({
        title: 'Reject this recommendation?',
        text: 'You would need to generate a new one to get it back.',
        confirmText: 'Reject',
      })
      if (!ok) return
    }
    act.mutate({ id, action })
  }

  const recs = recommendations || []

  return (
    <>
      {canManagePricing && (
        <Card className="mb-3">
          <div className="d-flex flex-wrap align-items-end gap-3">
            <div style={{ maxWidth: 200 }}>
              <label className="form-label">Target margin %</label>
              <input type="number" min="0" step="0.5" className="form-control"
                value={targetMargin} onChange={(e) => setTargetMargin(e.target.value)} />
            </div>
            <Button write icon={FiZap} loading={generate.isPending} onClick={onGenerate}>
              Generate recommendation
            </Button>
            {/* Shown only when there is genuinely no cost profile. Undefined
                means the profile is still loading — saying "needs one" before
                we know is worse than saying nothing. */}
            {costConfigured === false && (
              <span className="field-note field-note--warn">
                <FiAlertTriangle size={15} />
                <span>
                  <strong>No cost profile yet.</strong> The optimiser validates every figure
                  against your costs — set them on the <em>Costs &amp; Profit</em> tab first.
                </span>
              </span>
            )}
            {/* Inherited costs still produce a recommendation, but the numbers
                move if someone edits the global model, so say which was used. */}
            {costConfigured && costSource === 'TENANT_DEFAULT' && (
              <span className="field-note">
                <FiInfo size={15} />
                <span>Using your global cost defaults — this product has no cost profile of its own.</span>
              </span>
            )}
          </div>
        </Card>
      )}

      {loading ? (
        <Card><div className="text-center py-4"><span className="spinner-border spinner-border-sm text-primary" /></div></Card>
      ) : recs.length === 0 ? (
        <Card>
          <EmptyState icon={FiZap} title="No recommendations yet"
            message={canManagePricing
              ? 'Generate one above once competitors have been matched and costs are set.'
              : 'A manager can generate a price recommendation for this product.'} />
        </Card>
      ) : (
        <div className="row g-3">
          {recs.map((r) => (
            <div className="col-12 col-lg-6" key={r.id}>
              <Card hover className="h-100">
                <div className="d-flex align-items-start justify-content-between gap-2 mb-2">
                  <div>
                    <div className="h3 fw-bold mb-0">{money(r.recommendedPrice, r.currency)}</div>
                    <BoundNote rec={r} />
                    <div className="text-muted" style={{ fontSize: 11 }}>
                      {r.createdAt ? `created ${formatRelativeTime(r.createdAt)}` : null}
                      {r.expiresAt ? ` · expires ${formatRelativeTime(r.expiresAt)}` : null}
                    </div>
                  </div>
                  <div className="text-end">
                    <StatusBadge status={r.status} label={recStatusLabel(r)} />
                    {r.confidence != null && (
                      <div className="text-muted mt-1" style={{ fontSize: 11 }}>{r.confidence}% confidence</div>
                    )}
                  </div>
                </div>

                {/* Measured at the price that will actually be published, not
                    at the one the engine wanted — computing it before the cap
                    defeated the very margin floor that caused the cap. A capped
                    recommendation may therefore sit below target, correctly. */}
                <Stat label="Expected margin" value={pctOf(r.expectedMarginPct)} />
                <Stat label="Competitor median" value={money(r.competitorMedian, r.currency)} />
                <Stat label="Break-even price" value={money(r.breakEvenPrice, r.currency)} />

                {r.rationale && <p className="text-muted small mt-2 mb-2">{r.rationale}</p>}

                {canManagePricing && (
                  <div className="d-flex flex-wrap gap-2 mt-2">
                    {/* A draft is sent for approval before anyone can
                        authorise it — the two are different decisions. */}
                    {r.status === 'DRAFT' && (
                      <Button write size="sm" variant="light" loading={act.isPending}
                        onClick={() => onAction(r.id, 'submit')}>Submit for approval</Button>
                    )}
                    {r.status === 'PENDING_APPROVAL' && (
                      <Button write size="sm" icon={FiCheck} loading={act.isPending}
                        onClick={() => onAction(r.id, 'approve')}>Approve</Button>
                    )}
                    {/* Only when the backend says the publish can be undone.
                        Rolling back ends the record REJECTED with rolledBackAt
                        set, which the label above then reads as "published,
                        then rolled back". */}
                    {r.rollbackable && (
                      <Button write size="sm" variant="light" icon={FiRotateCcw} loading={act.isPending}
                        onClick={() => onAction(r.id, 'rollback', r)}>Roll back</Button>
                    )}
                    {r.status === 'APPROVED' && (
                      <Button write size="sm" icon={FiUploadCloud} loading={act.isPending}
                        onClick={() => onAction(r.id, 'publish')}>Publish</Button>
                    )}
                    {['DRAFT', 'PENDING_APPROVAL', 'APPROVED'].includes(r.status) && (
                      <Button write size="sm" variant="light" icon={FiX} loading={act.isPending}
                        onClick={() => onAction(r.id, 'reject')}>Reject</Button>
                    )}
                  </div>
                )}
              </Card>
            </div>
          ))}
          <div className="col-12">
            <p className="form-text mb-0">
              Publishing sets the recommended price as this product&apos;s selling price. It does
              <strong> not</strong> change the price on the marketplace — automatic publishing is
              out of scope for this release.
            </p>
          </div>
        </div>
      )}
    </>
  )
}
