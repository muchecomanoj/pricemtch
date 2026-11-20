import { Link } from 'react-router-dom'
import {
  FiSearch, FiAlertTriangle, FiCheckCircle, FiChevronDown, FiChevronUp, FiInfo, FiX, FiExternalLink,
} from 'react-icons/fi'
import MarketplaceLogo from '../common/MarketplaceLogo'
import ProductDraftFields from './ProductDraftFields'
import { ItemIdentifiers } from '../common/ListingCells'
import { scoreTone, looksLikeMatch, conflictLine } from '../../utils/listingToProduct'
import { REGIONS, mpLabel, shortItemId } from '../../utils/marketplaces'
import { formatCurrency } from '../../utils/format'
import { KEYS, LABEL, GROUP_PREVIEW, rowKey } from './addProductHelpers'

// The four step views of AddProductWizard. The wizard owns all the state;
// these only render it.

// One line of title, one of facts, price right-aligned. The five-line cards
// this replaces turned eight eBay listings into a scroll nobody could hold in
// their head.
// `showMarket` puts the badge on the row itself. Inside a marketplace group
// the heading already says it; the rejected list is not grouped, so without
// this those rows named no marketplace at all.
function ListingRow({ r, showMarket = false, selection }) {
  const { sourceKey, setSourceKey, competitors, toggleCompetitor } = selection
  const id = rowKey(r)
  const picked = id === sourceKey
  const currency = r.currency || 'USD'
  // eBay charges postage separately far more often than Amazon does, and the
  // landed figure is the only one comparable across the two.
  const landed = r.price != null && r.shipping > 0
    ? Math.round((r.price + r.shipping) * 100) / 100
    : null
  return (
    <div className={`d-flex align-items-center gap-2 px-2 py-2 rounded-3 border ${picked ? 'border-primary' : 'border-transparent'}`}
      style={{ background: picked ? 'var(--hover-soft)' : undefined }}>
      <input type="radio" name="add-source" className="form-check-input flex-shrink-0 mt-0"
        checked={picked} onChange={() => setSourceKey(id)}
        aria-label={`Use ${r.title || r.marketplaceItemId} as the product`} />
      <div className="flex-grow-1" style={{ minWidth: 0 }}>
        <div className="d-flex align-items-center gap-2">
          {showMarket && <MarketplaceLogo marketplace={r.marketplace} storefront={r.storefront} />}
          <span className="fw-semibold text-truncate" title={r.title || ''}>
            {r.title || r.marketplaceItemId}
          </span>
        </div>
        <div className="text-muted small text-truncate">
          <span className="font-monospace">{shortItemId(r.marketplaceItemId)}</span>
          {r.seller && <> · {r.seller}</>}
          <> · {r.condition || 'condition not stated'}</>
        </div>
        <div className="small">
          {r.score != null && (
            <span className={scoreTone(r.score)}
              title="How alike this listing is to what you searched for. The product does not exist yet, so its pack size and variant are not part of this score.">
              {r.score}% match to your search
            </span>
          )}
          {/* Why it was turned down, with both values. "Different size: 11.5
              vs 11" is the difference between a rejection someone trusts and
              one they override blindly. */}
          {conflictLine(r) && (
            <span className="text-danger"> · {conflictLine(r)}</span>
          )}
        </div>
        {/* The barcodes, which are what a supplier file is reconciled
            against — and on a rejected row, often the evidence for taking it
            anyway. */}
        <ItemIdentifiers identifiers={r.identifiers} />
      </div>
      <div className="text-end flex-shrink-0" style={{ minWidth: 118 }}>
        {r.price == null ? (
          <span className="text-muted" style={{ fontSize: 11 }}
            title="Amazon prices by the visitor's location, so this storefront quotes an import price rather than its shelf price.">
            no price from this region
          </span>
        ) : (
          <>
            <span className="fw-semibold">{formatCurrency(r.price, currency)}</span>
            {landed != null && (
              <div className="text-muted" style={{ fontSize: 11 }}
                title={`${formatCurrency(r.price, currency)} + ${formatCurrency(r.shipping, currency)} shipping`}>
                {formatCurrency(landed, currency)} landed
              </div>
            )}
          </>
        )}
      </div>
      <label className="d-flex align-items-center gap-1 small text-muted flex-shrink-0 mb-0"
        style={{ cursor: 'pointer', maxWidth: 150 }}
        title="Attach this listing to the new product and follow its price">
        <input type="checkbox" className="form-check-input mt-0"
          checked={competitors.has(id)} onChange={() => toggleCompetitor(r)} />
        <span>Add as competitor</span>
      </label>
      {/* The page itself, because a title and a score do not settle "is this
          my product?" — the photo and the variant on the listing do, and
          that decision is the whole of this step. */}
      {r.url && (
        <a className="icon-btn flex-shrink-0" href={r.url} target="_blank" rel="noreferrer"
          title={`Open this listing on ${mpLabel(String(r.marketplace || '').toUpperCase())}`}
          aria-label="Open this listing on the marketplace">
          <FiExternalLink size={14} />
        </a>
      )}
    </div>
  )
}

// Step 1 — what to look for, and where.
export function FindStep({
  value, idKey, autoKey, onValueChange, onKeyChange, onClear, onSearch,
  markets, setMarkets, marketplaces, region, setRegion,
}) {
  return (
        <>
          {/* One field, the way the Search page does it: the type sits inside
              the box because it qualifies what was typed. On its own row it
              read as an unrelated control, and the two never lined up. */}
          <label className="form-label" htmlFor="ap-value">What are you adding?</label>
          <div className="search-field">
            <select className="search-field__key" value={idKey} aria-label="What this value is"
              onChange={(e) => onKeyChange(e.target.value)}>
              {KEYS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
            <span className="search-field__divider" />
            <FiSearch className="search-field__icon" size={16} />
            <input id="ap-value" className="search-field__input" value={value} autoFocus
              placeholder="Paste a link or barcode, or type the product name"
              onChange={(e) => onValueChange(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && value.trim()) onSearch() }} />
            {value && (
              <button type="button" className="search-field__clear" aria-label="Clear"
                onClick={onClear}><FiX size={16} /></button>
            )}
          </div>
          <div className="mt-2 ps-1 text-muted small">
            {autoKey && value.trim()
              ? <>Detected as <strong>{LABEL[idKey]}</strong> — change it on the left if that is wrong.</>
              : 'An ASIN, barcode, MPN, eBay item number, product link, or the product name.'}
          </div>

          {/* Where to look. These narrow the search rather than defining it, so
              they are quieter than the field above and sized to match the
              Search page's scope row. */}
          <div className="search-scope mt-3 pt-3 border-top-soft">
            <div className="search-scope__field">
              <label className="form-label" htmlFor="ap-market">Marketplace</label>
              <select id="ap-market" className="form-select form-select-sm" value={markets[0] || ''}
                onChange={(e) => setMarkets(e.target.value ? [e.target.value] : [])}>
                <option value="">All connected</option>
                {marketplaces.map((code) => <option key={code} value={code}>{mpLabel(code)}</option>)}
              </select>
              <div className="form-text">Channels to include</div>
            </div>

            <div className="search-scope__field">
              <label className="form-label" htmlFor="ap-region">Search in</label>
              <select id="ap-region" className="form-select form-select-sm" value={region}
                onChange={(e) => setRegion(e.target.value)}>
                {REGIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
              </select>
              <div className="form-text">Which storefront to search</div>
            </div>
          </div>

          <div className="field-note mt-3" style={{ maxWidth: 'none' }}>
            <FiInfo size={15} />
            <span>
              An identifier search stops at the first marketplace that answers — an ASIN returns one
              Amazon listing. A second search then runs on the name it returned, to find the same
              product on the other marketplaces.
            </span>
          </div>
        </>
  )
}

// Step 2 — which listing IS the product, and which are its competitors.
// `selection` is { sourceKey, setSourceKey, competitors, toggleCompetitor, trackAll };
// `view` is { expanded, toggleGroup, showRejected, setShowRejected }.
export function PickStep({ allRows, groups, otherRejected, searchedAs, value, searching, selection, view }) {
  return (
        <>
          <p className="text-muted small mb-2">
            <strong>{allRows.filter(looksLikeMatch).length} of {allRows.length}</strong> look like
            the same product and are ticked.{' '}
            The <strong>selected</strong> listing supplies the title and identifiers.
            Tick <strong>Add as competitor</strong> on any listing whose price should be followed.
            Scores here compare each listing with <strong>what you searched for</strong>; competitors
            are checked again against the saved product, where its pack size and variant are known,
            so a verdict can come out lower on its Competitors tab.
            {searchedAs && (
              <> Searched <span className="font-monospace">{value.trim()}</span> as{' '}
                <strong>{LABEL[searchedAs] || searchedAs}</strong>.</>
            )}
          </p>

          {groups.map(([code, rows]) => {
            const open = view.expanded.has(code)
            const visible = open ? rows : rows.slice(0, GROUP_PREVIEW)
            const allTracked = rows.every((r) => selection.competitors.has(rowKey(r)))
            const more = rows.length - GROUP_PREVIEW
            return (
              <div key={code} className="mb-3">
                <div className="d-flex align-items-center justify-content-between gap-2 mb-1">
                  <div className="d-flex align-items-center gap-2">
                    <MarketplaceLogo marketplace={code} />
                    <span className="text-muted small">
                      {rows.length} listing{rows.length === 1 ? '' : 's'}
                    </span>
                  </div>
                  <button type="button" className="btn btn-sm btn-link p-0 text-decoration-none"
                    onClick={() => selection.trackAll(rows, !allTracked)}>
                    {allTracked ? 'Untrack all' : 'Add all as competitors'}
                  </button>
                </div>
                <div className="d-flex flex-column gap-1">
                  {visible.map((r) => <ListingRow key={rowKey(r)} r={r} selection={selection} />)}
                </div>
                {more > 0 && (
                  <button type="button" className="btn btn-sm btn-link p-0 text-decoration-none mt-1"
                    onClick={() => view.toggleGroup(code)}>
                    {open ? 'Show fewer' : `${more} more ${mpLabel(code)} listing${more === 1 ? '' : 's'}`}
                  </button>
                )}
              </div>
            )
          })}

          {/* An identifier search ends at the first marketplace that answers,
              so this is where the others appear — found by name, which is the
              only thing an ASIN and an eBay listing share. */}
          {searching && (
            <div className="text-muted small d-flex align-items-center gap-2">
              <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true" />
              Searching the other marketplaces by name…
            </div>
          )}

          {/* Shown, not hidden: the judge turns down the right listing often
              enough that folding these away strands anyone whose product is a
              variant or a renewed unit. */}
          {otherRejected.length > 0 && (
            <div className="mt-2 pt-2 border-top-soft">
              <button type="button" className="btn btn-sm btn-link text-decoration-none p-0 d-inline-flex align-items-center gap-1"
                onClick={() => view.setShowRejected((v) => !v)}>
                {otherRejected.length} other listing{otherRejected.length === 1 ? '' : 's'} found — not judged a match
                {view.showRejected ? <FiChevronUp size={14} /> : <FiChevronDown size={14} />}
              </button>
              {view.showRejected && (
                <div className="d-flex flex-column gap-1 mt-2">
                  {otherRejected.map((r) => <ListingRow key={rowKey(r)} r={r} showMarket selection={selection} />)}
                </div>
              )}
            </div>
          )}

          {allRows.length === 0 && !searching && (
            <div className="text-muted small">
              Nothing came back. Go back and try a barcode or the product name, or enter the
              details by hand.
            </div>
          )}
        </>
  )
}

// Step 3 — what to save.
export function ConfirmStep({ duplicate, source, form, setForm, ids, brands, categories, competitorCount }) {
  return (
        <div className="d-flex flex-column gap-3">
          {duplicate && (
            <div className="field-note field-note--warn" style={{ maxWidth: 'none' }}>
              <FiAlertTriangle size={15} />
              <span>
                <strong>{duplicate.title}</strong> already has this identifier.{' '}
                <Link to={`/products/${duplicate.id}`}>Open it</Link> and add these listings as
                competitors instead of creating a second product.
              </span>
            </div>
          )}

          {/* Which listing this is being built from, so the fields below read
              as "taken from this" rather than appearing from nowhere. */}
          <div className="draft-source">
            <MarketplaceLogo marketplace={source?.marketplace} storefront={source?.storefront} />
            <span className="text-truncate">{source?.title}</span>
            {source?.price != null && (
              <span className="fw-semibold ms-auto flex-shrink-0">
                {formatCurrency(source.price, source.currency || 'USD')}
              </span>
            )}
          </div>

          <ProductDraftFields form={form} setForm={setForm} ids={ids}
            brands={brands} categories={categories} idPrefix="ap" />

          <div className="field-note" style={{ maxWidth: 'none' }}>
            <FiInfo size={15} />
            <span>
              {competitorCount > 0
                ? <>{competitorCount} listing{competitorCount === 1 ? '' : 's'} will be attached as
                  competitors and their prices followed from now on. Each is checked again against
                  the saved product, and the verdict shows on its Competitors tab.</>
                : 'No competitors ticked, so this product starts with nothing to compare against.'}
            </span>
          </div>
        </div>
  )
}

// Step 4 — the outcome.
export function DoneStep({ created, onClose }) {
  return (
        <div className="d-flex flex-column gap-2">
          <div className="d-flex align-items-center gap-2">
            <FiCheckCircle className="text-success flex-shrink-0" />
            <span><strong>{created.product.title}</strong> was added.</span>
          </div>
          <div className="text-muted small">
            {created.attached} competitor{created.attached === 1 ? '' : 's'} attached
            {created.failed > 0 && <> · {created.failed} could not be attached</>}
          </div>
          {created.failed > 0 && (
            <div className="field-note field-note--warn" style={{ maxWidth: 'none' }}>
              <FiAlertTriangle size={15} />
              <span>
                Some listings were refused — usually a pack-size or condition rule. Open the product
                and add them from search to see the reason.
              </span>
            </div>
          )}
          <div>
            <Link to={`/products/${created.product.id}`} onClick={onClose}>Open the product</Link>
            {' · '}
            {/* Weight, dimensions and description live on the full form. They
                affect fees rather than matching, so they were left out here —
                but they should be one click away, not a hunt. */}
            <Link to={`/products/${created.product.id}`} state={{ edit: true }} onClick={onClose}>
              Add more details
            </Link>
          </div>
        </div>
  )
}
