import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  FiExternalLink, FiArrowRight, FiChevronDown, FiChevronUp, FiAlertCircle,
  FiBookmark, FiHelpCircle, FiCompass, FiPackage,
} from 'react-icons/fi'
import Button from '../../components/common/Button'
import AttachToProductModal from '../MarketplaceTools/AttachToProductModal'
import SaveAsProductModal from '../../components/products/SaveAsProductModal'
import { ItemIdentifiers } from '../../components/common/ListingCells'
import { shortItemId } from '../../utils/marketplaces'
import { scoreTone } from '../../utils/listingToProduct'
import { useAuth } from '../../context/AuthContext'
import MarketplaceLogo from '../../components/common/MarketplaceLogo'
import { formatCurrency } from '../../utils/format'

// Step 6 of the waterfall, on screen — the verdict the backend returned for
// every listing it found.
//
// EQUIVALENT must never look like MATCH. "Apple AirPods 4 (Renewed)" is the
// same product in a different condition — read as a like-for-like competitor,
// a cheap refurbished unit looks like being undercut when it isn't. This is
// exactly the case FR-MATCH-001 keeps judging on for identifier searches too.
const DECISION = {
  MATCH: { tone: 'green', label: 'Match' },
  EQUIVALENT: { tone: 'blue', label: 'Equivalent — different condition or pack' },
  UNCERTAIN: { tone: 'orange', label: 'Unsure' },
  NOT_MATCH: { tone: 'red', label: 'Not a match' },
}
const decisionOf = (d) => DECISION[d] || { tone: 'slate', label: d || 'Unjudged' }

// What a verdict may be saved as. FR-MATCH-003 auto-accepts "only when score
// and deterministic rules meet tenant thresholds" and routes everything
// borderline to review, so only the two confident decisions carry through —
// and missingEvidence overrides even those, because a verdict the model could
// not fully check is by definition not one to act on.
function saveAs(r) {
  if (r.missingEvidence?.length) return undefined      // attaches as CANDIDATE
  return { MATCH: 'MATCHED', EQUIVALENT: 'EQUIVALENT' }[r.decision]
}

// matchedAttributes is a list of ATTRIBUTE NAMES — ["brand", "model"] — with no
// values attached, so a bare chip reading "brand" looks like a field that
// failed to load rather than "brand was compared and agreed". The label is what
// makes them mean anything, so it is not optional.
function Attributes({ label, items, className, title }) {
  if (!items?.length) return null
  return (
    <div className="d-flex flex-wrap align-items-center gap-1 mt-2">
      <span className="text-muted small me-1">{label}</span>
      {items.map((v, i) => <span key={i} className={className} title={title}>{v}</span>)}
    </div>
  )
}

// Conflicts now carry the two values, which is the difference between knowing
// WHERE to look and knowing what you would find:
//
//   before   [ condition ]
//   after    condition  New → Renewed
//
// That second form is what decides whether a listing is comparable at all — a
// renewed unit priced against a new one is not being undercut. Falls back to
// the bare field name when the model answered without values, and to the plain
// `conflicts` array when conflictDetails is absent entirely.
function Conflicts({ conflicts, details }) {
  const rows = details?.length
    ? details
    : (conflicts || []).map((field) => ({ field }))
  if (!rows.length) return null

  return (
    <div className="d-flex flex-wrap align-items-center gap-2 mt-2">
      <span className="text-muted small">Differs on</span>
      {rows.map((c, i) => (
        <span key={i} className="mr-pill mr-pill--red d-inline-flex align-items-center gap-1"
          title="This attribute disagrees with what you searched for">
          {c.field}
          {c.productValue != null && c.candidateValue != null && (
            <span className="fw-semibold">
              {c.productValue} <FiArrowRight size={11} /> {c.candidateValue}
            </span>
          )}
        </span>
      ))}
    </div>
  )
}

export function JudgedRow({ r, onSave, onAddToCatalog, onFindSimilar, similarBusy, readOnly = false }) {
  const d = decisionOf(r.decision)
  const needsReview = !!r.missingEvidence?.length

  return (
    <div className="result-card">
      <div className="flex-grow-1" style={{ minWidth: 0 }}>
        <div className="d-flex align-items-center flex-wrap gap-2">
          {/* First in the row: where it is from is read before the verdict. */}
          <MarketplaceLogo marketplace={r.marketplace} storefront={r.storefront} />
          <span className={`mr-pill mr-pill--${d.tone}`}>{d.label}</span>

          {/* Two different questions, so never two bare percentages side by
              side. Score is how alike the products are; confidence is how much
              evidence there was to judge on. A bare title can score high on
              almost nothing, and a reviewer who conflates the two will trust
              exactly the verdicts they should not. */}
          {/* Scores are real numbers now — 12 and 95 are different answers, so
              they no longer read as the same grey text. */}
          {r.score != null && (
            <span className={`small ${scoreTone(r.score)}`}
              title="How alike this listing is to what you searched for — not to a product in your catalogue, which is judged again when the listing is attached.">
              {r.score}% match to your search
            </span>
          )}
          {r.confidence != null && (
            <span className="text-muted small d-inline-flex align-items-center gap-1"
              title="How much evidence the model had to judge on — not how close the match is">
              · {Math.round(r.confidence * 100)}% confidence <FiHelpCircle size={12} />
            </span>
          )}
          {/* Both lists now hold only listings the model actually ruled on, so
              this should never fire. Kept as a tell: if it ever does, the row
              is a string comparison wearing a verdict's clothes. */}
          {r.judgedBy === 'RULES' && (
            <span className="text-muted small">· scored by rules, AI verdict unavailable</span>
          )}
        </div>

        <div className="fw-semibold clamp-2 mt-2">{r.title || 'Untitled listing'}</div>
        <div className="text-muted small mt-1 d-flex flex-wrap gap-2">
          {/* The marketplace name moved to the logo at the top of the card. */}
          {r.marketplaceItemId && (
            <span className="font-monospace" title={r.marketplaceItemId}>
              {shortItemId(r.marketplaceItemId)}
            </span>
          )}
          {r.seller && <span>· {r.seller}</span>}
          {/* Amazon used to report "NEW" for (Renewed) units. It reports null
              now, and null is unknown — never quietly the safe-looking value. */}
          <span>· {r.condition || 'condition not stated'}</span>
        </div>
        <ItemIdentifiers identifiers={r.identifiers} />

        <div className="mt-1">
          {/* A result with no price is still a result: it confirms the product,
              carries the barcode and links to the page. Only the figure is
              missing, and the reason is worth stating rather than leaving a
              blank that reads as a fault. */}
          {r.price != null
            ? <strong>{formatCurrency(r.price, r.currency || 'USD')}</strong>
            : (
              <span className="text-muted small"
                title="Amazon prices by the visitor's location, so this storefront quotes an import price rather than its shelf price.">
                Price unavailable from this region
              </span>
            )}
          {r.shipping > 0 && (
            <span className="text-muted small ms-2">
              + {formatCurrency(r.shipping, r.currency || 'USD')} shipping
            </span>
          )}
        </div>

        {/* Conflicts first: on a rejection this is the whole answer, and it is
            more specific than the sentence underneath it. */}
        <Conflicts conflicts={r.conflicts} details={r.conflictDetails} />
        <Attributes label="Matches on" items={r.matchedAttributes} className="mr-pill mr-pill--slate"
          title="This attribute was compared and agreed" />

        {/* FR-MATCH-003: image-only and unverifiable matches go to a human. */}
        {needsReview && (
          <div className="field-note field-note--warn mt-2" style={{ maxWidth: 'none' }}>
            <FiAlertCircle size={15} />
            <span>
              <strong>Needs a person to confirm.</strong>{' '}
              Not verified: {r.missingEvidence.join(', ')}. Saves for review rather than as a
              confirmed match.
            </span>
          </div>
        )}

        {/* The model's own sentence, and the reusable part of the card — it is
            what lets someone decide in two seconds instead of opening the
            marketplace. Labelled for the same reason the attribute chips are:
            unlabelled, a fragment like "renewed condition with ANC" reads as
            leftover text rather than as the reason for the verdict above it. */}
        {r.reason && (
          <p className="ai-verdict__reason mb-0">
            {/* The colon and an explicit space, not a margin. `me-1` put a gap
                on screen but nothing between the words, so "Why" and a reason
                starting mid-sentence ran together as "Whyidentical title" —
                and a reason is usually a fragment, so it needs the punctuation
                to read as an answer rather than as part of the label. */}
            <span className="text-muted">Why:</span>{' '}
            <span className="text-body">{r.reason}</span>
          </p>
        )}

        <div className="d-flex flex-wrap gap-1 mt-2">
          {/* Two different claims about the same listing, so two buttons.
              "It is mine" creates a product; "it is theirs" attaches it to one
              I already have. Folding them into one would let a product be
              priced against its own listing. */}
          {onAddToCatalog && r.marketplaceItemId && (
            <Button write variant="light" icon={FiPackage} onClick={() => onAddToCatalog(r)}
              title="Create this as one of your own products">
              Add to catalog
            </Button>
          )}
          {onSave && r.marketplaceItemId && (
            <Button write variant="light" icon={FiBookmark} onClick={() => onSave(r)}
              title="Attach it to one of your products as a competitor">
              Save as competitor
            </Button>
          )}
          {/* An identifier search can only ever return this one product. This
              turns the answer into the next question — what else is on the
              market — using the title the marketplace just gave us.

              It carries a loading state because the search behind it runs a
              full AI judging pass and takes close to a minute. A button that
              looks inert for that long reads as broken — which is exactly what
              it was reported as. */}
          {onFindSimilar && r.title && (
            <Button write variant="light" icon={FiCompass} loading={similarBusy}
              onClick={() => onFindSimilar(r)}>
              Find competitors
            </Button>
          )}
          {r.marketplaceItemId && readOnly && (
            // The listing page reads the marketplace live, which a lapsed plan
            // no longer covers — so it is shown, locked, rather than a link
            // that opens onto a refused request.
            <Button write size="sm" variant="light">Sellers &amp; history</Button>
          )}
          {r.marketplaceItemId && !readOnly && (
            <Link className="btn btn-sm btn-light d-inline-flex align-items-center gap-1"
              // An ASIN is one product, so this card is the only result there
              // can be. The several sellers competing ON it live on the detail
              // page — named here so nobody reads one card as "one competitor".
              title="Price history, competing sellers and fees for this listing"
              to={`/marketplace-tools/${String(r.marketplace).toLowerCase()}/${encodeURIComponent(r.marketplaceItemId)}`}>
              Sellers &amp; history <FiArrowRight />
            </Link>
          )}
          {r.url && (
            <a className="btn btn-sm btn-light d-inline-flex align-items-center gap-1"
              href={r.url} target="_blank" rel="noreferrer">
              Open on marketplace <FiExternalLink size={13} />
            </a>
          )}
        </div>
      </div>
    </div>
  )
}

export default function JudgedListings({
  result, onFindSimilar, similarBusy = false, similarMode = false,
}) {
  // Open by default. These are real listings the search found, and the reason
  // one was turned down — wrong pack, renewed, different model — is often the
  // answer someone came for. Hiding them made the search look emptier than it
  // was; the toggle stays for when the list is long.
  const [showRejected, setShowRejected] = useState(true)
  const { readOnly } = useAuth()
  const [showUnjudged, setShowUnjudged] = useState(true)
  const [saving, setSaving] = useState(null)     // the listing being attached
  const [adding, setAdding] = useState(null)     // the listing being made a product

  // matches and rejected stay separate arrays all the way to the screen. One
  // array with a decision field is a forgotten filter away from presenting a
  // rejected listing as a competitor.
  // Memoised because the unjudged set is derived from them: a fresh [] each
  // render would recompute it on every keystroke elsewhere on the page.
  const matches = useMemo(() => result?.matches ?? [], [result])
  const rejected = useMemo(() => result?.rejected ?? [], [result])

  // Listings the search found that came back in NEITHER verdict array.
  //
  // The page used to render matches and rejected only, so anything missing
  // from both was invisible — a search reporting "Amazon: 20 results" with no
  // Amazon listing anywhere on screen, which reads as the marketplace having
  // failed. They are real listings and they are shown, labelled as unjudged
  // rather than quietly folded in among verdicts they never received.
  const unjudged = useMemo(() => {
    const seen = new Set([...matches, ...rejected]
      .map((r) => `${r.marketplace}:${r.marketplaceItemId}`))
    return (result?.items ?? []).filter(
      (r) => r.marketplaceItemId && !seen.has(`${r.marketplace}:${r.marketplaceItemId}`))
  }, [result, matches, rejected])
  // Rejections are the answer in two cases: nothing matched at all, and — after
  // "Find similar products" — when the whole point was to see OTHER products.
  // Leaving them folded away there hides exactly what was asked for.
  const rejectedOpen = showRejected || similarMode
  const total = matches.length + rejected.length + unjudged.length
  // Every row in one response is judged by the same model, so naming it per row
  // would be noise — but leaving it out entirely makes an AI verdict and a
  // marketplace fact look like the same kind of statement.
  // promptVersion rides on the listing, not the response — take both from the
  // same row so the pair can never describe two different judgements.
  const judge = [...matches, ...rejected].find((r) => r.judgedBy && r.judgedBy !== 'RULES')

  const row = (r, i) => (
    <div className="col-12 col-lg-6" key={r.marketplaceItemId || i}>
      <JudgedRow r={r}
        // Read-only: shown but locked (Button's `write`), so the feature is
        // visibly one renewal away. Listing pages do live
        // marketplace lookups, so the row drops that link too (see JudgedRow).
        onSave={setSaving}
        onAddToCatalog={setAdding}
        readOnly={readOnly}
        onFindSimilar={onFindSimilar} similarBusy={similarBusy} />
    </div>
  )

  return (
    <>
      {adding && (
        <SaveAsProductModal
          show
          onClose={() => setAdding(null)}
          listing={adding}
          // The verdict carries through to the competitor attach, exactly as it
          // does when saving against an existing product.
          decision={saveAs(adding)}
        />
      )}

      {saving && (
        <AttachToProductModal
          show
          onClose={() => setSaving(null)}
          marketplace={saving.marketplace}
          itemId={saving.marketplaceItemId}
          title={saving.title}
          storefront={saving.storefront}
          // Carries the verdict through, so confirming a match is one request
          // instead of attach-then-review. The backend still runs the pack-size
          // and condition rules and refuses with a reason if they fail.
          decision={saveAs(saving)}
        />
      )}

      {result?.message && (
        <div className={`field-note ${result.retryable ? 'field-note--warn' : ''} mb-3`}
          style={{ maxWidth: 'none' }}>
          <FiAlertCircle size={15} />
          <span>{result.message}</span>
        </div>
      )}

      {matches.length > 0 && (
        <>
          <div className="text-muted small mb-2">
            {similarMode
              ? `${matches.length} of ${total} are the same product`
              : `${matches.length} of ${total} listing${total === 1 ? '' : 's'} judged to be your product`}
            {judge && (
              <> · verdicts by <span className="font-monospace">{judge.judgedBy}</span>
                {judge.promptVersion && <> ({judge.promptVersion})</>}
              </>
            )}
          </div>
          <div className="row g-2">{matches.map(row)}</div>
        </>
      )}

      {rejected.length > 0 && (
        <div className={matches.length > 0 ? 'mt-3 pt-3 border-top-soft' : ''}>
          <button type="button"
            className="btn btn-link p-0 text-decoration-none d-flex align-items-center gap-2 fw-semibold"
            onClick={() => setShowRejected((s) => !s)}>
            {rejectedOpen ? <FiChevronUp /> : <FiChevronDown />}
            {similarMode
              // "Rejected" is the right word for "is this my product?" and the
              // wrong one for "what competes with it?" — same listings, and the
              // label has to follow the question that was actually asked.
              ? `${rejected.length} other product${rejected.length === 1 ? '' : 's'} in this market`
              : matches.length === 0
                ? `We found ${rejected.length}, but none looks like your product`
                : `${rejected.length} other listing${rejected.length === 1 ? '' : 's'} found — not judged a match`}
          </button>
          {/* Worth reading even on success: the conflicts tell you whether your
              title is wrong or the product simply is not sold here. */}
          {rejectedOpen && <div className="row g-2 mt-1">{rejected.map(row)}</div>}
        </div>
      )}

      {unjudged.length > 0 && (
        <div className="mt-3 pt-3 border-top-soft">
          <button type="button"
            className="btn btn-link p-0 text-decoration-none d-flex align-items-center gap-2 fw-semibold"
            onClick={() => setShowUnjudged((v) => !v)}>
            {showUnjudged ? <FiChevronUp /> : <FiChevronDown />}
            {unjudged.length} listing{unjudged.length === 1 ? '' : 's'} found but not checked
          </button>
          {/* No verdict is not a rejection. These carry no score and no
              conflicts, so nothing here says whether they are your product —
              that is the reader's call, which is why they are shown at all. */}
          <div className="text-muted small mt-1">
            The AI pass returned no verdict for {unjudged.length === 1 ? 'this one' : 'these'} —
            judge them yourself before treating them as competitors.
          </div>
          {showUnjudged && <div className="row g-2 mt-1">{unjudged.map(row)}</div>}
        </div>
      )}
    </>
  )
}
