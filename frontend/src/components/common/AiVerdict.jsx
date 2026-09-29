import { FiCpu } from 'react-icons/fi'

// Advisory judgement from the model on one candidate pair.
//
// Two rules this component exists to hold:
//
//  1. `score` is how likely the two are the SAME product — 0 certainly
//     different, 100 certainly the same. It is NOT the model's confidence in
//     its own decision. A bare "20%" beside a red badge reads as the opposite
//     of the truth, so the label always says what the number measures.
//
//  2. Nothing here decides anything. NOT_MATCH does not set match_status and
//     the wording must not imply it has — a reviewer still clicks Accept or
//     Reject. "Suggests" rather than "rejected".

const DECISION = {
  MATCH: { tone: 'green', label: 'AI: likely match' },
  NOT_MATCH: { tone: 'red', label: 'AI: not a match' },
  // The model declined to guess, which means a human genuinely has to look —
  // that is useful information, not a failure.
  UNCERTAIN: { tone: 'orange', label: 'AI: unsure' },
}

export function AiVerdictBadge({ verdict }) {
  if (!verdict) {
    return <span className="mr-pill mr-pill--slate">Not judged</span>
  }
  const d = DECISION[verdict.decision] || { tone: 'slate', label: verdict.decision }
  return <span className={`mr-pill mr-pill--${d.tone}`}><FiCpu size={11} />{d.label}</span>
}

// Badge, score and — most importantly — the reason. The decision is a colour;
// the reason is what lets someone act in two seconds instead of opening Amazon,
// so it renders on the row rather than behind a tooltip.
export default function AiVerdict({ verdict, matchScore, compact = false }) {
  if (!verdict) {
    return (
      <span className="text-muted small d-inline-flex align-items-center gap-1">
        <FiCpu size={12} /> Not judged
      </span>
    )
  }

  return (
    <div className="ai-verdict">
      <div className="d-flex align-items-center flex-wrap gap-2">
        <AiVerdictBadge verdict={verdict} />
        {verdict.score != null && (
          <span className="text-muted small">
            {/* Never a bare percentage — it has to say what it measures. */}
            {verdict.score}% match
          </span>
        )}
        {/* Rules and model measure different things, and disagreement is the
            informative case: the rules matched brand and title, the model
            caught that the colour differs. Neither is redundant. */}
        {matchScore != null && (
          <span className="text-muted small">· rules {matchScore}%</span>
        )}
      </div>
      {verdict.reason && !compact && (
        <p className="ai-verdict__reason">{verdict.reason}</p>
      )}
      {verdict.reason && compact && (
        <span className="ai-verdict__reason" title={verdict.reason}>{verdict.reason}</span>
      )}
    </div>
  )
}
