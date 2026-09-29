import { FiCheckCircle, FiAlertTriangle } from 'react-icons/fi'

// Shared rendering for FR-MATCH-002 match evidence, so the review queue and the
// product's Competitors tab present the same signals the same way.

// 0-100 confidence bands.
export const scoreTone = (s) => (s == null ? 'secondary' : s >= 85 ? 'success' : s >= 70 ? 'warning' : 'danger')

export function MatchScoreBadge({ score, suffix = '%' }) {
  if (score == null) return <span className="text-muted">—</span>
  return <span className={`badge text-bg-${scoreTone(score)}`}>{score}{suffix}</span>
}

// Gradient stops per band, plus the words a reviewer actually scans for.
const RING = {
  success:   { from: '#34d399', to: '#059669', label: 'Strong match' },
  warning:   { from: '#fbbf24', to: '#d97706', label: 'Needs review' },
  danger:    { from: '#f87171', to: '#dc2626', label: 'Weak match' },
  secondary: { from: '#cbd5e1', to: '#94a3b8', label: 'Unscored' },
}
export const scoreLabel = (s) => (s === 100 ? 'Perfect match' : RING[scoreTone(s)].label)

// Animated gradient dial. SVG rather than a conic-gradient so the stroke can
// carry a real gradient, round caps, and a draw-on transition.
export function MatchScoreRing({ score, size = 76, stroke = 7 }) {
  const tone = scoreTone(score)
  const { from, to } = RING[tone]
  const r = (size - stroke) / 2
  const circumference = 2 * Math.PI * r
  const pct = Math.max(0, Math.min(100, score ?? 0))
  const gradId = `mr-ring-${tone}`

  return (
    <div className="position-relative flex-shrink-0" style={{ width: size, height: size }}
      role="img" aria-label={score == null ? 'No match score' : `Match score ${score} out of 100, ${scoreLabel(score)}`}>
      <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }} aria-hidden="true">
        <defs>
          <linearGradient id={gradId} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor={from} />
            <stop offset="100%" stopColor={to} />
          </linearGradient>
        </defs>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke}
          stroke="currentColor" className="text-muted" opacity="0.16" />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke}
          stroke={`url(#${gradId})`} strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference - (pct / 100) * circumference}
          style={{ transition: 'stroke-dashoffset 0.9s cubic-bezier(0.2, 0.8, 0.2, 1)' }} />
      </svg>
      <div className="position-absolute top-50 start-50 translate-middle text-center lh-1">
        <div style={{ fontSize: size * 0.29, fontWeight: 700, letterSpacing: '-0.02em' }}>
          {score == null ? '—' : score}
        </div>
      </div>
    </div>
  )
}

// Splits a listing's evidence into supporting vs conflicting labels. Prefers the
// structured matchSignals[] and falls back to the plain reasons[]/conflicts[].
export function splitSignals(listing = {}) {
  if (listing.matchSignals?.length) {
    return {
      positives: listing.matchSignals.filter((s) => s.positive).map((s) => s.label),
      negatives: listing.matchSignals.filter((s) => !s.positive).map((s) => s.label),
    }
  }
  return { positives: listing.reasons || [], negatives: listing.conflicts || [] }
}

// Positive signals cycle through three pastels so a long evidence row has
// rhythm rather than reading as one grey block. The hue is decorative — the
// check icon carries the meaning — but it is keyed off the label so a given
// signal always renders the same colour instead of shifting between pages.
const POSITIVE_TONES = ['green', 'blue', 'purple']
const toneFor = (label) => {
  let h = 0
  for (let i = 0; i < label.length; i += 1) h = (h * 31 + label.charCodeAt(i)) >>> 0
  return POSITIVE_TONES[h % POSITIVE_TONES.length]
}

// `max` caps how many chips render before a "+n more" pill — useful in tables.
export function EvidenceChips({ listing, max }) {
  const { positives, negatives } = splitSignals(listing)
  if (!positives.length && !negatives.length) return null

  // Conflicts matter more than confirmations, so they are never truncated away.
  const room = max == null ? positives.length : Math.max(0, max - negatives.length)
  const shownPos = positives.slice(0, room)
  const hidden = positives.length - shownPos.length

  return (
    <div className="d-flex flex-wrap gap-2">
      {negatives.map((label) => (
        <span key={`n-${label}`} className="mr-pill mr-pill--orange">
          <FiAlertTriangle size={12} />{label}
        </span>
      ))}
      {shownPos.map((label) => (
        <span key={`p-${label}`} className={`mr-pill mr-pill--${toneFor(label)}`}>
          <FiCheckCircle size={12} />{label}
        </span>
      ))}
      {hidden > 0 && <span className="mr-pill mr-pill--slate">+{hidden} more</span>}
    </div>
  )
}

// How fresh a competitor listing actually is.
//
// `lastFetchedAt` is when we last pulled this listing from the marketplace.
// `sourceTimestamp` is when the search job ran — so a row the backend served
// from cache carries a brand-new sourceTimestamp while its price may be days
// old. Showing sourceTimestamp would claim a stale row is current, which is
// exactly the wrong answer for a price. It is only the fallback, for rows
// stored before the field existed.
export const fetchedAt = (listing) => listing?.lastFetchedAt || listing?.sourceTimestamp || null
