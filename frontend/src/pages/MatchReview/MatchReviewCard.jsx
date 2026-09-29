import { Link } from 'react-router-dom'
import {
  Check, X, Equal, ExternalLink, ArrowDown, Store, Clock, Sparkles, ArrowRight,
} from 'lucide-react'
import { MatchScoreRing, EvidenceChips, splitSignals, scoreLabel, fetchedAt } from '../../components/common/MatchEvidence'
import AiVerdict from '../../components/common/AiVerdict'
import { formatCurrency, formatRelativeTime } from '../../utils/format'

// Soft pill tones — no hard borders anywhere on the card.
const MARKETPLACE_TONE = {
  AMAZON: 'orange', KEEPA: 'orange', EBAY: 'blue', WALMART: 'blue', WEB: 'purple',
}
const CONDITION_TONE = { NEW: 'green', USED: 'slate', REFURBISHED: 'red' }
const pretty = (s) => (s ? s[0] + s.slice(1).toLowerCase() : '')

// One candidate awaiting a decision. Reads top-to-bottom:
//   marketplace listing  →  our product  →  why  →  decide
export default function MatchReviewCard({
  r, canReview, deciding, bulkMode, selected, onToggleSelect, onDecide, index = 0,
}) {
  const { negatives } = splitSignals(r)
  const cls = [
    'mr-card',
    negatives.length ? 'has-conflicts' : '',
    selected ? 'is-selected' : '',
    deciding ? 'is-deciding' : '',
  ].filter(Boolean).join(' ')

  return (
    <article className={cls}
      style={{ animationDelay: `${Math.min(index, 8) * 45}ms` }}
      aria-label={`Candidate ${r.title || r.id}, match score ${r.matchScore ?? 'unknown'}`}>

      {/* ── Top bar: source + price ───────────────────────────── */}
      <div className="d-flex align-items-start justify-content-between gap-3">
        <div className="d-flex align-items-center gap-2 flex-wrap">
          {bulkMode && (
            <input type="checkbox" className="form-check-input flex-shrink-0"
              checked={selected} onChange={() => onToggleSelect(r.id)}
              aria-label={`Select ${r.title || r.id}`} />
          )}
          {r.marketplace && (
            <span className={`mr-pill mr-pill--${MARKETPLACE_TONE[r.marketplace] || 'slate'}`}>
              <Store size={12} />{pretty(r.marketplace)}
            </span>
          )}
          {r.condition && (
            <span className={`mr-pill mr-pill--${CONDITION_TONE[r.condition] || 'slate'}`}>
              {pretty(r.condition)}
            </span>
          )}
        </div>
        <div className="text-end flex-shrink-0">
          <div className="mr-card__price">
            {r.lastPrice == null ? '—' : formatCurrency(r.lastPrice, r.currency || 'USD')}
          </div>
          {r.shipping != null && (
            <div className="mr-card__meta mt-1">
              {r.shipping === 0 ? 'Free shipping' : `+ ${formatCurrency(r.shipping, r.currency || 'USD')} shipping`}
            </div>
          )}
        </div>
      </div>

      {/* ── Score + listing ───────────────────────────────────── */}
      <div className="d-flex align-items-center gap-3 mt-4">
        <MatchScoreRing score={r.matchScore} />
        <div style={{ minWidth: 0 }}>
          <div className="mr-eyebrow mb-1">Marketplace listing</div>
          <div className="mr-card__title">{r.title || '—'}</div>
          <div className="d-flex flex-wrap align-items-center gap-3 mt-2 mr-card__meta">
            {r.seller && <span>{r.seller}</span>}
            {fetchedAt(r) && (
              <span className="d-inline-flex align-items-center gap-1">
                <Clock size={12} />{formatRelativeTime(fetchedAt(r))}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* ── Connector ─────────────────────────────────────────── */}
      <div className="mr-link">
        <span className="mr-link__badge"><ArrowDown size={11} /> matches</span>
      </div>

      <div className="mr-target">
        <div className="flex-grow-1" style={{ minWidth: 0 }}>
          <div className="mr-eyebrow">Your product</div>
          {r.productId ? (
            <Link to={`/products/${r.productId}`}
              className="fw-semibold text-decoration-none d-inline-flex align-items-center gap-1 text-truncate mw-100">
              <span className="text-truncate">{r.productTitle || `#${r.productId}`}</span>
              <ArrowRight size={13} className="flex-shrink-0" />
            </Link>
          ) : (
            <span className="fw-semibold d-block text-truncate">{r.productTitle || '—'}</span>
          )}
        </div>
        {r.url && (
          <a href={r.url} target="_blank" rel="noreferrer" className="icon-btn flex-shrink-0"
            title="Open the source listing" aria-label="Open the source listing">
            <ExternalLink size={15} />
          </a>
        )}
      </div>

      {/* ── AI match analysis ─────────────────────────────────── */}
      <div className="mt-4">
        <div className="d-flex align-items-center gap-2 mb-2">
          <Sparkles size={13} className="text-primary" />
          <span className="mr-eyebrow">Match analysis</span>
          {r.matchScore != null && (
            <span className="mr-card__meta ms-auto">{scoreLabel(r.matchScore)}</span>
          )}
        </div>
        <EvidenceChips listing={r} />

        {/* The model's read, under the rules engine's. They measure different
            things — the reason is what makes a decision fast, so it renders
            on the card rather than behind a tooltip. */}
        {r.aiVerdict && (
          <div className="mt-3 pt-3 border-top-soft">
            <AiVerdict verdict={r.aiVerdict} />
          </div>
        )}
      </div>

      {/* ── Decide ────────────────────────────────────────────────
          Three compact buttons, one row, spaced — small enough that they still
          fit a 3-column card without wrapping. */}
      <div className="mt-auto pt-3">
        {canReview ? (
          <div className="mr-actions">
            <button type="button" className="mr-btn mr-btn--match" disabled={deciding}
              onClick={() => onDecide(r.id, 'MATCHED')}>
              <Check size={14} />Match
            </button>
            <button type="button" className="mr-btn mr-btn--equal" disabled={deciding}
              onClick={() => onDecide(r.id, 'EQUIVALENT')}>
              <Equal size={14} />Equal
            </button>
            <button type="button" className="mr-btn mr-btn--reject" disabled={deciding}
              onClick={() => onDecide(r.id, 'REJECTED')}>
              <X size={14} />Reject
            </button>
          </div>
        ) : (
          // Read-only roles still need to see where the candidate stands.
          <span className="mr-pill mr-pill--slate">{pretty(r.matchStatus || 'CANDIDATE')}</span>
        )}
      </div>
    </article>
  )
}
