import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { FiChevronDown, FiChevronRight, FiArrowRight, FiAlertCircle } from 'react-icons/fi'
import JudgedListings, { JudgedRow, saveAs } from '../SearchProduct/JudgedListings'
import { buildPayloads } from '../SearchProduct/SearchForm'
import Card from '../../components/common/Card'
import SaveAsProductModal from '../../components/products/SaveAsProductModal'
import AttachToProductModal from '../MarketplaceTools/AttachToProductModal'
import ExistingProductDialog from './ExistingProductDialog'
import { parseListings, parseSteps } from '../../services/bulkSearchService'
import { ChannelTrace } from '../SearchProduct/SearchTrace'
import { isProviderError } from '../SearchProduct/ProviderError'
import { productService } from '../../services/productService'
import { marketplaceService } from '../../services/marketplaceService'
import { useDestination } from '../../hooks/useDestination'
import { keywordLadder } from '../../utils/identifiers'
import { primaryIdentifier, identifiersOf } from '../../utils/listingToProduct'
import { useNotification } from '../../context/NotificationContext'
import { errorMessage } from '../../services/apiError'

// One line of the uploaded file.
//
// ERROR is "we never looked": the marketplace could not be reached, so the row
// says nothing about whether the product exists. It reads "Not searched", not
// "Failed" — the row is waiting for a retry, not broken. NO_LISTINGS is "we
// looked and nothing was there", and stays the ordinary neutral result.
// ERROR is amber, not red: nothing is wrong with the user's data, and red
// sends them off to fix a file that was never the problem. Any row where a
// channel failed and nothing was found arrives as ERROR (the backend decides);
// a judge that read listings and rejected them all stays NONE_MATCHED.
const STATUS = {
  FOUND: { tone: 'green', label: 'Found' },
  NONE_MATCHED: { tone: 'slate', label: 'Nothing matched' },
  NO_LISTINGS: { tone: 'slate', label: 'Nothing matched' },
  NOT_JUDGED: { tone: 'blue', label: 'Not reviewed' },
  // On the marketplace, with no buyable offer. A real answer (counted under
  // "Nothing matched" in the job totals), so no retry and nothing amber.
  UNPRICED: { tone: 'blue', label: 'No price' },
  ERROR: { tone: 'orange', label: 'Not searched' },
  PENDING: { tone: 'slate', label: 'Waiting' },
}

// What the file said, for the columns that were filled in. Shown so a row that
// found nothing can be traced back to the cell that caused it.
function inputSummary(r) {
  return [
    ['SKU', r.inputSku], ['ASIN', r.inputAsin], ['UPC', r.inputUpc],
    ['EAN', r.inputEan], ['GTIN', r.inputGtin], ['MPN', r.inputMpn],
    ['Region', r.inputRegion],
  ].filter(([, v]) => v)
}

// The fields "Add to catalog" fills in that an update may change. SKU is the
// product's own label and identifiers are how it was recognised, so neither is
// rewritten; a blank in the form means "nothing to say", never "clear it".
const UPDATABLE = ['title', 'brand', 'category', 'condition', 'ourPrice']

// A row found by its title already searched the market by keyword. One found
// by an identifier only answered "what is this?" — one product.
const isKeyword = (t) => !t || /TITLE|KEYWORD|QUERY|TEXT/i.test(t)

// "What else is on the market?" — the same follow-up a single search runs
// after an identifier hit (SearchProduct's findSimilar): a keyword search on
// the title the marketplace returned, word by word and widest last, stopping
// at the first query the marketplace answers at all. Only a completely empty
// answer widens it; listings judged not-a-match mean the market holds
// different products, and broadening then just returns more of them.
//
// `region` is the storefront the rivals are searched in, worked out by the
// caller (see rivalRegion).
async function findCompetitors(seed, region, destination) {
  const ladder = keywordLadder(seed.title)
  const tried = []
  let last = null
  for (const query of ladder) {
    tried.push(query)
    const { channel: payload } = buildPayloads(
      { key: 'TITLE', value: query, region, image: null }, [], destination,
    )
    payload.judge = true
    last = await marketplaceService.channelSearch(payload)
    // Unreachable is not empty — stop, rather than widen and report "nothing".
    if (last?.matches?.length || last?.rejected?.length || last?.items?.length || isProviderError(last)) {
      return { title: query, result: last, tried }
    }
  }
  return { title: tried[tried.length - 1] || seed.title, result: last, tried }
}

// The storefront to look for rivals in, so they are priced in the same market
// as the row. The listing's own storefront first; then the row's region
// column, then the region the file was run for. A listing read from a UK page
// can come back without a storefront, and falling straight to the US then
// priced a UK product's competitors on amazon.com. 'US' only for jobs from
// before the region choice existed.
//
// A region column is typed by people, so "uk" is read as GB, the code the
// search expects.
const rivalRegion = (seed, row, jobRegion) => {
  const code = String(seed?.storefront || row.inputRegion || jobRegion || 'US').trim().toUpperCase()
  return code === 'UK' ? 'GB' : code
}

const norm = (v) => (v == null ? '' : String(v).trim())

function differencesOf(product, form) {
  return UPDATABLE
    .filter((f) => norm(form[f]) !== '' && norm(form[f]) !== norm(product[f])
      // 49 and 49.00 are the same price.
      && !(f === 'ourPrice' && Number(form[f]) === Number(product[f])))
    .map((f) => ({ field: f, current: product[f] ?? null, incoming: norm(form[f]), advisory: false }))
}

export default function BulkRow({ row: r, jobRegion }) {
  const qc = useQueryClient()
  const { notify } = useNotification()
  const [open, setOpen] = useState(false)
  const [adding, setAdding] = useState(null)       // the listing being made a product
  const [attaching, setAttaching] = useState(null) // the listing being saved as a competitor
  // { result, patch }: the existing product, what would change, and the
  // partial update that "Yes, update it" sends.
  const [existing, setExisting] = useState(null)
  const [updating, setUpdating] = useState(false)

  const matches = parseListings(r.matchesJson)
  const rejected = parseListings(r.rejectedJson)
  const steps = parseSteps(r.stepsJson)
  const notSearched = r.status === 'ERROR'
  const st = STATUS[r.status] || { tone: 'slate', label: r.status || 'Unknown' }
  // Written to be shown as-is; on an ERROR row it is the failed step's sentence.
  const message = r.message
  const { destination } = useDestination()

  // Competitors for a row found by identifier, as a single search shows them.
  // Run when the row is opened rather than for every row of the file — each
  // is a judged marketplace search of up to a minute — and kept once found,
  // so closing and reopening the row doesn't search again.
  const seed = matches[0]
  const wantsRivals = !isKeyword(r.resolvedIdentifierType) && !!seed?.title
  const rivalsQ = useQuery({
    queryKey: ['bulkRivals', r.id],
    queryFn: () => findCompetitors(seed, rivalRegion(seed, r, jobRegion), destination),
    enabled: open && wantsRivals,
    staleTime: Infinity,
    gcTime: 30 * 60 * 1000,
    retry: false,
  })

  // From the "already has this identifier" warning in the Add to catalog
  // modal: compare the product already there with what the form would write,
  // and ask before changing anything — the same dialog as before.
  const compareExisting = async (dup, form) => {
    const listing = adding
    setAdding(null)
    try {
      const product = await productService.get(dup.id)
      const differences = differencesOf(product, form)
      const patch = Object.fromEntries(differences.map((d) => [
        d.field, d.field === 'ourPrice' ? Number(d.incoming) : d.incoming,
      ]))
      setExisting({
        patch,
        result: {
          productId: product.id,
          productSku: product.sku,
          productTitle: product.title,
          matchedOn: primaryIdentifier(identifiersOf(listing)),
          message: 'A product with this identifier is already in your catalogue. Updating changes only the fields below; its SKU and identifiers stay as they are.',
          differences,
        },
      })
    } catch (e) {
      notify.error(errorMessage(e, 'Could not load the existing product.'))
    }
  }

  const applyUpdate = async () => {
    if (!existing) return
    if (!Object.keys(existing.patch).length) { setExisting(null); return }
    setUpdating(true)
    try {
      await productService.update(existing.result.productId, existing.patch)
      qc.invalidateQueries({ queryKey: ['products'] })
      notify.success('Existing product updated')
      setExisting(null)
    } catch (e) {
      notify.error(errorMessage(e, 'Could not update the product.'))
    } finally {
      setUpdating(false)
    }
  }

  return (
    <div className="rounded-3 border-soft" style={{ border: '1px solid var(--border-soft)' }}>
      {/* The same modal as "Add to catalog" on a single search. */}
      {adding && (
        <SaveAsProductModal show listing={adding} decision={saveAs(adding)}
          onClose={() => setAdding(null)}
          onUpdateExisting={compareExisting} />
      )}
      {/* The same modal as "Save as competitor" on a single search. */}
      {attaching && (
        <AttachToProductModal show onClose={() => setAttaching(null)}
          marketplace={attaching.marketplace}
          itemId={attaching.marketplaceItemId}
          title={attaching.title}
          storefront={attaching.storefront}
          // Carries the verdict through, as a single search does; the backend
          // still applies its pack-size and condition rules.
          decision={saveAs(attaching)} />
      )}
      <ExistingProductDialog result={existing?.result} saving={updating}
        onClose={() => setExisting(null)}
        onConfirm={applyUpdate} />

      <button type="button"
        className="btn w-100 text-start d-flex align-items-center gap-2 p-3"
        onClick={() => setOpen((o) => !o)}>
        {open ? <FiChevronDown className="flex-shrink-0" /> : <FiChevronRight className="flex-shrink-0" />}

        {/* The row number is the line in THEIR spreadsheet, so a problem can be
            fixed in the file rather than hunted for by title. */}
        <span className="text-muted small font-monospace flex-shrink-0">Row {r.rowNumber}</span>

        <span className="fw-semibold text-truncate flex-grow-1" style={{ minWidth: 0 }}>
          {r.inputTitle || r.resolvedQuery || `#${r.rowNumber}`}
        </span>

        <span className={`mr-pill mr-pill--${st.tone} flex-shrink-0`}>{st.label}</span>

        {r.matchCount > 0 && (
          <span className="text-muted small flex-shrink-0 d-none d-md-inline">
            {r.matchCount} match{r.matchCount === 1 ? '' : 'es'}
          </span>
        )}
        {r.resolvedIdentifierType && (
          <span className="text-muted small flex-shrink-0 d-none d-lg-inline">
            via {r.resolvedIdentifierType}
          </span>
        )}
      </button>

      {/* Why it wasn't searched, without opening the row: a page of red pills
          with no reason reads as "the product is missing". */}
      {!open && notSearched && message && (
        <div className="px-3 pb-3 small text-truncate text-warning-emphasis"
          title={message} style={{ marginTop: -6 }}>
          {message}
        </div>
      )}

      {open && (
        <div className="px-3 pb-3">
          <div className="text-muted small mb-2 d-flex flex-wrap gap-3">
            {inputSummary(r).map(([k, v]) => (
              <span key={k}>{k} <span className="font-monospace">{v}</span></span>
            ))}
            {r.resolvedQuery && <span>searched: <strong>{r.resolvedQuery}</strong></span>}
          </div>

          {/* Written to be shown as-is. */}
          {message && (
            <div className={`field-note ${notSearched ? 'field-note--warn' : ''} mb-3`}
              style={{ maxWidth: 'none' }}>
              <FiAlertCircle size={15} />
              <span>{message}</span>
            </div>
          )}

          {matches.length > 1 && (
            <div className="text-muted small mb-2">
              {matches.length} listings matched this row — they are alternatives, not a ranking.
              Add the one that is your product.
            </div>
          )}
          {matches.length > 0 && (
            <div className="row g-2">
              {matches.map((m, i) => (
                <div className="col-12 col-xl-6" key={m.marketplaceItemId || i}>
                  {/* The same verdict card as a single search — same data,
                      same meaning, same "Add to catalog" opening the same
                      modal — so it must not look like a different thing. */}
                  <JudgedRow r={m} onAddToCatalog={setAdding} onSave={setAttaching} />
                </div>
              ))}
            </div>
          )}

          {rejected.length > 0 && (
            <div className="text-muted small mt-2">
              {rejected.length} other listing{rejected.length === 1 ? '' : 's'} were rejected.
            </div>
          )}

          {/* Per channel, as on a single search: a refused connector, a rate
              limit and a bad identifier need different answers, and only the
              trace tells them apart. Rows searched before the trace existed
              have none, so nothing is drawn rather than an empty panel. */}
          {steps.length > 0 && (
            <div className="mb-3">
              <ChannelTrace channel={{ steps, correlationId: r.correlationId, finishedAt: r.searchedAt }} />
            </div>
          )}

          {/* ── Competitors for the product that was found ── */}
          {wantsRivals && (
            <Card className="mt-3" title="Competitors"
              subtitle={rivalsQ.data
                ? (rivalsQ.data.tried?.length > 1
                  ? `Searched for "${rivalsQ.data.title}" — ${rivalsQ.data.tried.slice(0, -1).map((t) => `"${t}"`).join(', ')} found nothing`
                  : `Searched by keyword for "${rivalsQ.data.title}" — from the title the marketplace returned`)
                : 'A keyword search on the title the marketplace returned'}>
              {rivalsQ.isFetching ? (
                <div className="text-center py-4">
                  <span className="spinner-border spinner-border-sm text-primary me-2" />
                  <span className="text-muted small">Searching for competing products…</span>
                </div>
              ) : rivalsQ.isError ? (
                <div className="text-muted small">
                  {errorMessage(rivalsQ.error, 'The competitor search failed.')}{' '}
                  <button type="button" className="btn btn-link btn-sm p-0 align-baseline"
                    onClick={() => rivalsQ.refetch()}>Try again</button>
                </div>
              ) : (
                // similarMode opens the rejected list and renames it: a listing
                // that is NOT the same product is precisely what was asked for.
                <JudgedListings result={rivalsQ.data?.result} similarMode onRetry={() => rivalsQ.refetch()} />
              )}
            </Card>
          )}

          <div className="d-flex flex-wrap align-items-center gap-2 mt-3 pt-3 border-top-soft">
            {r.savedProductId != null && (
              <Link className="btn btn-sm btn-light d-inline-flex align-items-center gap-1"
                to={`/products/${r.savedProductId}`}>
                Open in your catalogue <FiArrowRight />
              </Link>
            )}
            <span className="text-muted small">
              {matches.length > 0
                ? 'Use “Add to catalog” on a listing to create it as a product.'
                : notSearched
                  ? 'Not searched yet — “Retry failed rows” at the top runs it again.'
                  : 'Nothing to save from this row.'}
            </span>
          </div>
        </div>
      )}
    </div>
  )
}
