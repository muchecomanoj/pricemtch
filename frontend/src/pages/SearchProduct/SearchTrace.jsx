import { FiCheckCircle, FiMinusCircle, FiXCircle } from 'react-icons/fi'
import { mpLabel } from '../../utils/marketplaces'
import { formatDateTime } from '../../utils/format'

// Fixed vocabulary, so an empty result is explainable rather than ambiguous.
// The distinction that matters: RESTRICTED_ACCESS and RATE_LIMITED mean we were
// stopped from looking, which is NOT evidence the product is absent.
const OUTCOME = {
  SUCCESS: { tone: 'green', label: 'Found' },
  CACHED: { tone: 'green', label: 'From cache' },
  NO_RESULT: { tone: 'slate', label: 'Not there' },
  UNPRICED: { tone: 'blue', label: 'No price' },
  SKIPPED: { tone: 'slate', label: 'Skipped' },
  RESTRICTED_ACCESS: { tone: 'red', label: 'Access refused' },
  RATE_LIMITED: { tone: 'orange', label: 'Rate limited' },
  AUTHENTICATION_ERROR: { tone: 'red', label: 'Auth failed' },
  PROVIDER_ERROR: { tone: 'red', label: 'Provider error' },
}
const outcomeOf = (o) => OUTCOME[o] || { tone: 'slate', label: o || 'Unknown' }

// Outcomes where the channel was never actually searched. The backend writes
// a plain sentence into a failed step's `detail`, shown as-is; this is only
// for a failed step that arrives without one.
const STOPPED = {
  PROVIDER_ERROR: (mp) => `${mp} couldn't be reached or didn't answer properly, so it wasn't checked.`,
  RATE_LIMITED: (mp) => `${mp} asked us to slow down, so it wasn't checked.`,
  RESTRICTED_ACCESS: (mp) => `${mp} refused access, so it wasn't checked.`,
  AUTHENTICATION_ERROR: (mp) => `Signing in to ${mp} failed, so it wasn't checked.`,
}
const isStoppedStep = (st) => !!STOPPED[st?.outcome] && st.marketplace !== 'AI'

// A trace line. `marketplace: "AI"` is not a channel — it is step 6 (judging)
// or the vision pass on an image, and reading it as a marketplace would print
// "Ai by MATCH", which says nothing.
const AI_STEP = { MATCH: 'AI matching', IMAGE: 'Image' }

export const fmtTime = (iso) => {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString()
}

function StepRow({ st }) {
  const o = outcomeOf(st.outcome)
  const isAi = st.marketplace === 'AI'
  return (
    <div className="d-flex align-items-center flex-wrap gap-2 small">
      <span className={`mr-pill mr-pill--${o.tone}`}>{o.label}</span>
      <span className="fw-semibold">
        {isAi ? (AI_STEP[st.identifierType] || 'AI') : mpLabel(st.marketplace)}
      </span>
      <span className="text-muted">
        {!isAi && `by ${st.identifierType || 'keyword'}`}
        {/* A channel step's count is listings FOUND. The AI step finds nothing
            — its count is how many it labelled a match — so it is never
            printed as "results", which would credit the model with the
            discovery and is the one thing this panel exists to disambiguate. */}
        {!isAi && st.resultCount != null && ` · ${st.resultCount} result${st.resultCount === 1 ? '' : 's'}`}
        {st.fromCache && ' · cached'}
        {/* When the marketplace actually observed this, which on a cached step
            is NOT when the search ran. A price can be hours old while the card
            above says "prices as at" the moment you pressed Search. */}
        {st.sourceTimestamp && ` · seen ${formatDateTime(st.sourceTimestamp)}`}
        {st.durationMs != null && `${isAi ? '' : ' · '}${st.durationMs} ms`}
      </span>
      {/* What was actually sent to the marketplace. When a search finds
          nothing, the first question is whether it asked for the right thing —
          and until now the trace said which TYPE was used but never the value,
          so a mis-sent query was indistinguishable from an absent product. */}
      {st.query && (
        <span className="text-muted font-monospace" style={{ fontSize: 11 }}>
          “{st.query}”
        </span>
      )}
      {/* On an image step this is what the vision model said it saw — the
          single most useful line in the trace when the results look wrong. */}
      {isStoppedStep(st) && !st.detail ? (
        <span className="text-muted">— {STOPPED[st.outcome](mpLabel(st.marketplace))}</span>
      ) : st.detail && <span className="text-muted">— {st.detail}</span>}
    </div>
  )
}

// The trace answers "why was that empty" — refused, rate limited and
// genuinely absent are indistinguishable without it.
export function ChannelTrace({ channel }) {
  return (
    <div className="mt-3 pt-3 border-top-soft">
      <div className="form-label mb-2">How this was searched</div>
      <div className="d-flex flex-column gap-1">
        {channel.steps.map((st, i) => <StepRow key={i} st={st} />)}
      </div>
      {/* FR-SRCH-003 also asks for a correlation ID retained across web,
          marketplace, AI and database operations — it is what support
          needs to find this exact search in the backend logs. */}
      {channel.correlationId && (
        <div className="text-muted small mt-2 font-monospace">
          ref {channel.correlationId}
          {channel.finishedAt && ` · ${fmtTime(channel.finishedAt)}`}
        </div>
      )}
    </div>
  )
}

const TRACE_ICON = {
  MATCHED: <FiCheckCircle className="text-success" />,
  NO_MATCH: <FiXCircle className="text-muted" />,
  SKIPPED: <FiMinusCircle className="text-muted" />,
}

export function TraceRow({ t }) {
  return (
    <div className="d-flex align-items-center gap-2 small">
      <span className="flex-shrink-0">{TRACE_ICON[t.status] || <FiMinusCircle className="text-muted" />}</span>
      <span className="fw-semibold" style={{ width: 64 }}>{t.stage}</span>
      <span className={t.status !== 'MATCHED' ? 'text-muted' : ''}>
        {t.note}{t.matchCount ? ` (${t.matchCount})` : ''}
      </span>
    </div>
  )
}
