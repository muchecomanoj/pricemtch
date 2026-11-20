import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { FiZap, FiUploadCloud, FiX, FiInfo, FiAlertTriangle, FiRotateCcw } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import DropdownMenu from '../../components/common/DropdownMenu'
import EmptyState from '../../components/common/EmptyState'
import StatusBadge from '../../components/common/StatusBadge'
import { BoundNote, recStatusLabel } from '../../components/common/RecommendationBound'
import { productIntelService } from '../../services/productIntelService'
import { useNotification } from '../../context/NotificationContext'
import { money, pctOf, Stat } from './shared'
import { formatDate, formatRelativeTime } from '../../utils/format'
import { errorMessage } from '../../services/apiError'

// Submit exists to separate who proposes a price from who authorises it. With
// one admin in the account there is nobody to submit to, so it is a detour that
// ends where it started — and today every tenant has exactly one user.
// TODO: derive from the team once the user count is on the client; with a
// separate approver, Submit becomes the proposer's primary action.
const canSubmit = false

// The backend refuses submit / approve / publish on these; reject still works
// so a dead card can always be cleared.
const ACTIONABLE = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED']

// A null margin / break-even means no cost is recorded (a profile saved with
// every field zero now comes back null, not "100%"). Say why it is missing and
// offer the fix, rather than a bare dash.
function NoCost({ onOpenCosts }) {
  return (
    <span className="text-muted fw-normal small">
      No cost recorded
      {onOpenCosts && (
        <> — <button type="button" className="btn btn-link btn-sm p-0 align-baseline" onClick={onOpenCosts}>add costs</button></>
      )}
    </span>
  )
}

const PAST_TENSE = {
  rollback: 'rolled back', submit: 'submitted for approval', approve: 'approved',
  publish: 'published', approvePublish: 'approved and published', reject: 'rejected',
}

// One click for someone who can both authorise and apply a price: the two
// existing steps in order. If the publish is refused, the approval stands.
async function approveThenPublish(id) {
  await productIntelService.approveRecommendation(id)
  try {
    return await productIntelService.publishRecommendation(id)
  } catch (e) {
    e.approvedOnly = true
    throw e
  }
}

// GET/POST /products/{id}/price-recommendations + approve / publish / reject
export default function RecommendationsTab({
  productId, recommendations, loading, canManagePricing, costConfigured, costSource, onOpenCosts,
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
    onError: (err) => notify.error(errorMessage(err, 'Could not generate a recommendation.')),
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
      if (action === 'approvePublish') return approveThenPublish(id)
      if (action === 'rollback') return productIntelService.rollbackRecommendation(id)
      return productIntelService.rejectRecommendation(id)
    },
    onSuccess: (_d, { action }) => {
      notify.success(`Recommendation ${PAST_TENSE[action]}`)
      refresh()
      // Publishing writes the price onto the product, so the hero KPIs and every
      // profit figure derived from ourPrice need refetching.
      if (action === 'publish' || action === 'approvePublish' || action === 'rollback') {
        qc.invalidateQueries({ queryKey: ['product', productId] })
        qc.invalidateQueries({ queryKey: ['profitability', productId] })
      }
    },
    // A publish can be refused by the cooldown. The backend writes that message
    // for a person to read, so it is shown as-is — and the recommendation is
    // NOT consumed by the refusal: it stays approved and publishable later, so
    // nothing here disables the button.
    onError: (err) => {
      // Approve & publish got halfway: approved, then the publish was refused
      // (the cooldown, usually). Approved is a real state worth keeping, so it
      // is said as such and the list shows it ready to publish later.
      if (err?.approvedOnly) {
        notify.warn(`Approved, but not published yet: ${err.message}`)
        refresh()
        return
      }
      // Verbatim: an expired refusal names the date, the reason and the fix.
      // Refetch too, so a stale tab picks up `expired` and swaps its buttons.
      notify.error(errorMessage(err, 'Action failed.'))
      refresh()
    },
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
          {recs.map((r) => {
            // Still worth reading (price, rationale), just not worth acting on.
            const expired = r.expired && ACTIONABLE.includes(r.status)
            return (
            <div className="col-12 col-lg-6" key={r.id}>
              <Card hover className="h-100">
                <div className="d-flex align-items-start justify-content-between gap-2 mb-2">
                  <div>
                    <div className="h3 fw-bold mb-0">{money(r.recommendedPrice, r.currency)}</div>
                    <BoundNote rec={r} />
                    <div className="text-muted" style={{ fontSize: 11 }}>
                      {r.createdAt ? `created ${formatRelativeTime(r.createdAt)}` : null}
                      {r.expiresAt ? ` · ${r.expired ? 'expired' : 'expires'} ${formatRelativeTime(r.expiresAt)}` : null}
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
                <Stat label="Expected margin" value={r.expectedMarginPct != null
                  ? pctOf(r.expectedMarginPct) : <NoCost onOpenCosts={onOpenCosts} />} />
                <Stat label="Competitor median" value={money(r.competitorMedian, r.currency)} />
                <Stat label="Break-even price" value={r.breakEvenPrice != null
                  ? money(r.breakEvenPrice, r.currency) : <NoCost onOpenCosts={onOpenCosts} />} />

                {r.rationale && <p className="text-muted small mt-2 mb-2">{r.rationale}</p>}

                {expired && (
                  <div className="field-note field-note--warn mt-2">
                    <FiAlertTriangle size={15} />
                    <span>
                      Expired {formatDate(r.expiresAt)} — generate a new one to act on this price.
                    </span>
                  </div>
                )}

                {canManagePricing && (
                  <div className="d-flex flex-wrap align-items-center gap-2 mt-2">
                    {expired && (
                      <>
                        <Button write size="sm" icon={FiZap} loading={generate.isPending} onClick={onGenerate}>
                          Generate recommendation
                        </Button>
                        <Button write size="sm" variant="light" icon={FiX} loading={act.isPending}
                          onClick={() => onAction(r.id, 'reject')}>Reject</Button>
                      </>
                    )}
                    {/* One primary road (approve and apply), Reject beside it as
                        the only other real decision, and the rare variants
                        behind "…". Approve only is a genuine state — signed off,
                        not yet applied. */}
                    {!expired && ['DRAFT', 'PENDING_APPROVAL'].includes(r.status) && (
                      <>
                        <Button write size="sm" icon={FiZap} loading={act.isPending}
                          onClick={() => onAction(r.id, 'approvePublish')}>Approve &amp; publish</Button>
                        <Button write size="sm" variant="light" icon={FiX} loading={act.isPending}
                          onClick={() => onAction(r.id, 'reject')}>Reject</Button>
                        <DropdownMenu write disabled={act.isPending} items={[
                          { label: 'Approve only', hint: 'Sign the price off without applying it',
                            onClick: () => onAction(r.id, 'approve') },
                          canSubmit && r.status === 'DRAFT' && {
                            label: 'Submit for approval', hint: 'Send to an approver instead of deciding now',
                            onClick: () => onAction(r.id, 'submit'),
                          },
                        ]} />
                      </>
                    )}
                    {/* Only when the backend says the publish can be undone.
                        Rolling back ends the record REJECTED with rolledBackAt
                        set, which the label above then reads as "published,
                        then rolled back". */}
                    {r.rollbackable && (
                      <Button write size="sm" variant="light" icon={FiRotateCcw} loading={act.isPending}
                        onClick={() => onAction(r.id, 'rollback', r)}>Roll back</Button>
                    )}
                    {!expired && r.status === 'APPROVED' && (
                      <>
                        <Button write size="sm" icon={FiUploadCloud} loading={act.isPending}
                          onClick={() => onAction(r.id, 'publish')}>Publish</Button>
                        <Button write size="sm" variant="light" icon={FiX} loading={act.isPending}
                          onClick={() => onAction(r.id, 'reject')}>Reject</Button>
                      </>
                    )}
                  </div>
                )}
              </Card>
            </div>
            )
          })}
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
