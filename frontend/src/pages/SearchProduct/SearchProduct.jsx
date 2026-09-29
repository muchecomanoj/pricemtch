import { useMemo, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  FiSearch, FiBox, FiCheckCircle, FiMinusCircle, FiXCircle, FiArrowRight,
  FiChevronDown, FiChevronUp, FiAlertCircle, FiExternalLink, FiImage, FiRefreshCw, FiRotateCcw,
  FiPlus, FiUpload, FiPackage,
} from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import EmptyState from '../../components/common/EmptyState'
import StatusBadge from '../../components/common/StatusBadge'
import { ItemIdentifiers } from '../../components/common/ListingCells'
import ItemPriceHistory from '../../components/common/ItemPriceHistory'
import ItemRecentChanges from '../../components/common/ItemRecentChanges'
import SearchForm, { EMPTY_SEARCH, buildPayloads } from './SearchForm'
import JudgedListings from './JudgedListings'
import SaveAsProductModal from '../../components/products/SaveAsProductModal'
import MarketplaceLogo from '../../components/common/MarketplaceLogo'
import { searchService, searchByImage } from '../../services/searchService'
import { marketplaceService } from '../../services/marketplaceService'
import { useMarketplaceCodes } from '../../hooks/useMarketplaces'
import { useDestination } from '../../hooks/useDestination'
import Modal from '../../components/common/Modal'
import ProductForm from '../../components/forms/ProductForm'
import { productService } from '../../services/productService'
import { useAuth } from '../../context/AuthContext'
import { useNotification } from '../../context/NotificationContext'
import { mpLabel, isScrapedRegion, storefrontLabel } from '../../utils/marketplaces'
import { alternateKey, KEY_LABEL, keywordLadder } from '../../utils/identifiers'
import { formatCurrency, formatDateTime } from '../../utils/format'

// Product discovery — FR-SRCH-001, FRD §13.1.
//
// ONE search. The form takes the whole documented input set at once — SKU,
// ASIN, EAN, UPC/GTIN, MPN, title, brand, product URL, image, target
// marketplaces and region — and one Search runs both halves of the waterfall:
//
//   steps 1-2   your own catalogue      POST /search  (free)
//   steps 3-8   the marketplaces        POST /marketplaces/search  (chargeable)
//
// The channel half is where the value is, so it renders first. The catalogue
// half answers a different question — "do I already track this?" — and sits
// below, labelled as such.
//
// The channel half is ONE call with judging switched on. FRD §4.3 is a single
// waterfall — find (steps 1-5), judge (step 6), apply rules (step 7) — so
// judging is a stage of every search, not a mode the user picks. FR-MATCH-001
// is explicit that an exact ASIN/GTIN hit "does not bypass pack-size and
// condition checks": the identifier decides which step FOUND a listing, never
// whether it gets checked. The renewed unit sitting under the ASIN you searched
// for is exactly what step 6 is there to catch.

// The backend now sends `url`, built from the country domain matching the
// region the price came from. It is never reconstructed here: an ASIN pasted
// onto amazon.com when the price was read from amazon.ca points at a different
// listing at a different price, which is worse than no link.

// Fixed vocabulary, so an empty result is explainable rather than ambiguous.
// The distinction that matters: RESTRICTED_ACCESS and RATE_LIMITED mean we were
// stopped from looking, which is NOT evidence the product is absent.
const OUTCOME = {
  SUCCESS: { tone: 'green', label: 'Found' },
  CACHED: { tone: 'green', label: 'From cache' },
  NO_RESULT: { tone: 'slate', label: 'Not there' },
  UNPRICED: { tone: 'orange', label: 'No price' },
  SKIPPED: { tone: 'slate', label: 'Skipped' },
  RESTRICTED_ACCESS: { tone: 'red', label: 'Access refused' },
  RATE_LIMITED: { tone: 'orange', label: 'Rate limited' },
  AUTHENTICATION_ERROR: { tone: 'red', label: 'Auth failed' },
  PROVIDER_ERROR: { tone: 'red', label: 'Provider error' },
}
const outcomeOf = (o) => OUTCOME[o] || { tone: 'slate', label: o || 'Unknown' }

// Searches that resolve to exactly one product, and therefore always want a
// keyword pass afterwards. TITLE and BRAND are already keyword searches, so
// running a second one on their own result would just repeat them.
const IDENTIFIER_KEYS = ['ASIN', 'UPC', 'EAN', 'GTIN', 'MPN', 'ITEM_ID', 'URL']

export default function SearchProduct() {
  const [form, setForm] = useState(EMPTY_SEARCH)
  const [markets, setMarkets] = useState([])
  const [busy, setBusy] = useState(false)
  const [channel, setChannel] = useState(null)   // ChannelSearchResponse
  const [catalog, setCatalog] = useState(null)   // { results, trace, … }
  const [imageHits, setImageHits] = useState(null)
  // A raw, unjudged listing being turned into one of our own products.
  const [addingListing, setAddingListing] = useState(null)
  const [showTrace, setShowTrace] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const { codes: channelCodes } = useMarketplaceCodes()
  // Deliberately NOT cleared by "Clear all": it is a standing preference, not
  // part of this particular query.
  const { destination, setDestination } = useDestination()
  // Competitors for the product an identifier search found, fetched SEPARATELY
  // and shown underneath it.
  //
  // This used to replace the search — switching the form to Title and re-running
  // — which answered the question but threw away the exact product you had just
  // identified. Both belong on screen: "this is your product" and "this is what
  // it competes with" are two halves of one answer.
  const [rivals, setRivals] = useState(null)   // { title, result } | null
  const [rivalsBusy, setRivalsBusy] = useState(false)
  const rivalsRef = useRef(null)

  // FRD 13.1 puts intake on this screen: a search that finds nothing should
  // flow straight into creating the product, rather than sending someone to
  // another page to retype what they just searched for.
  const { can } = useAuth()
  const { notify } = useNotification()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [creating, setCreating] = useState(false)

  const createProduct = useMutation({
    mutationFn: (values) => productService.create(values),
    onSuccess: (p) => {
      qc.invalidateQueries({ queryKey: ['products'] })
      setCreating(false)
      notify.success('Product created')
      if (p?.id) navigate(`/products/${p.id}`)
    },
    onError: (e) => notify.error(e?.message || 'Could not create the product.'),
  })

  // Seed the form from whatever was searched, so an identifier that came back
  // empty is carried across rather than retyped from memory.
  const seedFromSearch = () => {
    const meta = { ASIN: 'asin', UPC: 'upc', EAN: 'ean', GTIN: 'gtin', MPN: 'mpn' }[form.key]
    return {
      ...(meta ? { [meta]: form.value.trim() } : {}),
      ...(form.key === 'TITLE' ? { title: form.value.trim() } : {}),
      ...(form.key === 'BRAND' ? { brand: form.value.trim() } : {}),
    }
  }

  const reset = () => {
    setForm(EMPTY_SEARCH); setMarkets([]); setRivals(null)
    setChannel(null); setCatalog(null); setImageHits(null); setShowTrace(false)
  }

  // `force` bypasses the backend's cached research and re-reads the channels
  // live. That spends marketplace quota, so it is never the default — a plain
  // Search happily serves cached listings.
  //
  // `f` overrides the form, for re-running immediately after picking a listing.
  // `retried` stops the ambiguous-identifier retry below from looping.
  const run = async ({ force = false, form: f = form, retried = false } = {}) => {
    setRivals(null)
    const { catalogQuery, channel: payload } = buildPayloads(f, markets, destination)
    // Always judged — see the note at the top of this file.
    payload.judge = true
    if (force) payload.forceRefresh = true
    setBusy(true)
    setRefreshing(force)
    setChannel(null); setCatalog(null); setImageHits(null); setShowTrace(false)
    // Twelve digits is both a UPC and the usual length of an eBay item number,
    // and 135344088794 is a structurally valid UPC that is really an item
    // number — the check digit passes about one time in ten. Only the
    // marketplace can settle it, so an empty result is retried the other way
    // rather than reported as "no such product".
    let retryAs = null
    try {
      // Both halves in parallel, and allSettled so a chargeable channel failure
      // does not discard the free catalogue result that already succeeded.
      const [ch, cat, img] = await Promise.allSettled([
        marketplaceService.channelSearch(payload),
        catalogQuery ? searchService.search({ value: catalogQuery }) : Promise.resolve(null),
        f.image ? searchByImage(f.image) : Promise.resolve(null),
      ])
      setChannel(ch.status === 'fulfilled' ? ch.value
        : { items: [], steps: [], retryable: true,
          note: ch.reason?.message || 'The marketplace search failed.' })
      if (cat.status === 'fulfilled' && cat.value) setCatalog(cat.value)
      if (img.status === 'fulfilled' && img.value) setImageHits(img.value)

      // An identifier resolves to one product, which answers "what is this?"
      // but never "what does it compete with?". The keyword pass runs straight
      // after, on the title the marketplace just returned — the one string good
      // enough to find rivals, and the reason this cannot start any earlier.
      //
      // Deliberately not awaited: the exact result is on screen immediately and
      // the competitor card carries its own spinner underneath it.
      const found = ch.status === 'fulfilled' ? ch.value : null
      const seed = found?.matches?.[0] || found?.items?.[0]
      if (IDENTIFIER_KEYS.includes(f.key) && seed?.title) findSimilar(seed, f)

      const empty = !seed && !(found?.rejected?.length)
      if (empty && !retried) retryAs = alternateKey(f.key, f.value)
    } finally {
      setBusy(false)
      setRefreshing(false)
    }

    if (retryAs) {
      const next = { ...f, key: retryAs }
      setForm(next)
      notify.info(`Nothing found as ${KEY_LABEL[f.key] || f.key} — trying it as ${
        KEY_LABEL[retryAs] || retryAs}.`)
      await run({ force, form: next, retried: true })
    }
  }

  // "What else is on the market?" — a second search on the title the marketplace
  // returned, run under the exact result rather than over it.
  //
  // The identifier is dropped deliberately: keeping it would re-run the same
  // exact lookup and return the same single product, which is the one result
  // this exists to get away from. Region, markets, destination and result count
  // all carry over, so the two searches are comparable.
  const findSimilar = async (r, f = form) => {
    // Word by word, widest last. One query is a guess at how specific to be:
    // the full title finds nothing because no rival writes it the same way,
    // and two words find the wrong product. So it steps outwards and stops at
    // the first query the marketplace answers at all.
    //
    // Only a COMPLETELY empty response widens it. Listings that came back and
    // were judged not-a-match mean the market holds different products, and
    // broadening then just returns more of them.
    const ladder = keywordLadder(r.title)
    if (!ladder.length) return

    setRivals({ title: ladder[0], result: null })
    setRivalsBusy(true)
    try {
      requestAnimationFrame(() => {
        rivalsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      })

      let last = null
      const tried = []
      for (const query of ladder) {
        tried.push(query)
        setRivals({ title: query, result: null, tried })
        // The identifier is dropped deliberately: keeping it would re-run the
        // same exact lookup and return the same single product.
        const { channel: payload } = buildPayloads(
          { ...f, key: 'TITLE', value: query, image: null }, markets, destination,
        )
        payload.judge = true
        last = await marketplaceService.channelSearch(payload)
        const anything = (last?.matches?.length || last?.rejected?.length || last?.items?.length)
        if (anything) {
          setRivals({ title: query, result: last, tried })
          return
        }
      }
      // Every step came back empty — report the widest attempt, since that is
      // the one that proves the point.
      setRivals({ title: tried[tried.length - 1], result: last, tried })
    } catch (e) {
      setRivals({
        title: ladder[0],
        result: { matches: [], rejected: [], message: e?.message || 'The competitor search failed.' },
      })
    } finally {
      setRivalsBusy(false)
    }
  }

  // Which channel produced the results — the waterfall stops at the first that
  // succeeds, so it is the last step with a non-zero count.
  // What the wait is going to cost, from the backend's measured ~1.4 listings
  // a second. maxResults is PER marketplace, so the channel count multiplies it.
  const channelsSearched = (markets.length ? markets : channelCodes).length || 1
  const expectedListings = (form.maxResults || 40) * channelsSearched
  const expectedSeconds = Math.max(5, Math.round(expectedListings / 1.4))

  const hitMarket = [...(channel?.steps || [])].reverse()
    .find((s) => s.resultCount > 0)?.marketplace || channel?.steps?.[0]?.marketplace

  // Step 6 ran when the response carries verdict arrays. NOT_JUDGED (or a
  // failure before judging) leaves them null, and the raw item grid stands in.
  const judged = Array.isArray(channel?.matches) || Array.isArray(channel?.rejected)

  const catalogRows = catalog?.results || []

  return (
    <>
      <PageHeader title="Search Product"
        subtitle="Find a product across your catalogue and the marketplaces in one search."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Search' }]} />

      {can('MANAGE_PRODUCTS') && (
        <div className="d-flex flex-wrap gap-2 mb-3">
          <Button write variant="light" icon={FiPlus} onClick={() => setCreating(true)}>Save product</Button>
          {/* Bulk SEARCH, not bulk import. The same spreadsheet reaches
              /products/imports from the Products page and creates products
              without ever calling a marketplace; here it searches every row
              and saves nothing until a person decides. */}
          <Button write variant="light" icon={FiUpload} onClick={() => navigate('/search/bulk')}>
            Search a list of products
          </Button>
        </div>
      )}

      <Modal show={creating} title="New product" onClose={() => setCreating(false)} size="lg" scrollable>
        <ProductForm defaultValues={seedFromSearch()} submitting={createProduct.isPending}
          onSubmit={(v) => createProduct.mutate(v)} onCancel={() => setCreating(false)} />
      </Modal>

      {/* Twenty seconds of silence reads as a hung page. An ASIN search on a
          storefront SP-API is not authorised for is read from the Amazon page
          instead, which is slow and worth saying out loud while it happens. */}
      {busy && isScrapedRegion(form.region) && IDENTIFIER_KEYS.includes(form.key) && (
        <div className="field-note mb-3" style={{ maxWidth: 'none' }}>
          <FiAlertCircle size={15} />
          <span>
            Reading the Amazon {storefrontLabel({ countryCode: form.region })} page — about
            20 seconds. That storefront has no API access, so the page itself is read.
          </span>
        </div>
      )}

      <SearchForm
        form={form} setForm={setForm}
        markets={markets} setMarkets={setMarkets}
        channelCodes={channelCodes}
        destination={destination} setDestination={setDestination}
        onSearch={() => run()} busy={busy} onReset={reset}
      />

      {/* A judged search at 40 per marketplace runs close to a minute, and the
          old page showed nothing at all until it finished — a spinner on the
          button, and otherwise a screen that looks broken. What it is doing and
          roughly how long is the difference between waiting and reloading. */}
      {busy && !channel && (
        <Card className="mt-3">
          <div className="d-flex align-items-center gap-3">
            <span className="spinner-border text-primary" role="status" aria-hidden="true" />
            <div>
              <div className="fw-semibold">
                Searching {(markets.length ? markets : channelCodes).map(mpLabel).join(' and ') || 'the marketplaces'}…
              </div>
              <div className="text-muted small">
                Up to {expectedListings} listings, each checked against what you typed — about{' '}
                {expectedSeconds}s. Results appear as soon as the judging finishes.
              </div>
            </div>
          </div>
        </Card>
      )}

      {/* ── The marketplaces ─────────────────────────────────────── */}
      {channel && (
        <Card className="mt-3" title="On the marketplaces"
          // The time these prices were read, in the header rather than buried
          // in the trace. Marketplace prices move by the hour, so "ours says
          // $2,539.99, Amazon says $2,554.45" is unanswerable without knowing
          // when ours was taken — and the first assumption is always a bug.
          subtitle={`${judged
            ? 'Every listing here was found by a marketplace. The verdicts on them are AI.'
            : 'Live listings — what competitors are selling and charging.'}${
            channel.finishedAt ? ` Prices as at ${fmtTime(channel.finishedAt)}.` : ''}`}
          actions={(
            <Button write variant="light" icon={FiRefreshCw} loading={refreshing} disabled={busy}
              title="Re-read the marketplaces, ignoring anything cached. Uses API quota."
              onClick={() => run({ force: true })}>
              Refresh
            </Button>
          )}>
          {/* Shown whatever came back. It is the sentence that explains an
              answer that looks wrong but is not: a storefront that answered in
              place of the one asked for, a parent product instead of the
              variant, or a price left out because the reader is quoted an
              import price rather than the shelf price. */}
          {channel?.note && (
            <div className="field-note mb-3" style={{ maxWidth: 'none' }}>
              <FiAlertCircle size={15} />
              <span>{channel.note}</span>
            </div>
          )}

          {judged && (
            <JudgedListings result={channel}
              // Offered only when the search was BY an identifier. On a title
              // search every card would re-run a near-identical query.
              onFindSimilar={form.key === 'TITLE' ? undefined : findSimilar}
              similarBusy={rivalsBusy} />
          )}

          {/* Only when judging did NOT run — with verdicts on screen, a blanket
              "these are lookalikes" banner says less than the per-listing
              conflicts and confidence do, and contradicts a confirmed match. */}
          {/* NOT_JUDGED. `message` explains why nothing was checked; `retryable`
              separates a provider that fell over from one that was never
              configured — retrying the latter forever was the old behaviour. */}
          {!judged && channel?.message && (
            <div className={`field-note ${channel.retryable ? 'field-note--warn' : ''} mb-3 align-items-center`}
              style={{ maxWidth: 'none' }}>
              <FiAlertCircle size={15} />
              <span className="flex-grow-1">{channel.message}</span>
              {channel.retryable && (
                <Button write variant="light" icon={FiRotateCcw} loading={busy} className="flex-shrink-0"
                  onClick={() => run()}>Try again</Button>
              )}
            </div>
          )}

          {!judged && channel?.items?.length > 0 && !channel.resolvedIdentifierType && (
            <div className="field-note field-note--warn mb-3" style={{ maxWidth: 'none' }}>
              <FiAlertCircle size={15} />
              <span>
                <strong>Matched on title, not an identifier.</strong>{' '}
                These are lookalikes — confirm each before treating it as the same product.
              </span>
            </div>
          )}

          {!judged && channel?.items?.length > 0 ? (
            <>
              <div className="text-muted small mb-2">
                {channel.totalResults} result{channel.totalResults === 1 ? '' : 's'}
                {channel.resolvedQuery && <> for <strong>{channel.resolvedQuery}</strong></>}
              </div>
              <div className="row g-2">
                {channel.items.map((item, i) => {
                  return (
                    <div className="col-12 col-lg-6" key={item.marketplaceItemId || i}>
                      <div className="result-card">
                        <div className="flex-grow-1" style={{ minWidth: 0 }}>
                          <MarketplaceLogo marketplace={item.marketplace || hitMarket}
                            storefront={item.storefront} />
                          <div className="fw-semibold clamp-2 mt-2">{item.title || 'Untitled listing'}</div>
                          <div className="text-muted small mt-1 d-flex flex-wrap gap-2">
                            {item.marketplaceItemId && <span className="font-monospace">{item.marketplaceItemId}</span>}
                            {item.seller && <span>· {item.seller}</span>}
                            <span>· {item.condition || 'condition not stated'}</span>
                          </div>
                          <ItemIdentifiers identifiers={item.identifiers} />
                          <div className="mt-1">
                            {item.price != null
                              ? <strong>{formatCurrency(item.price, item.currency || 'USD')}</strong>
                              : (
                                <span className="text-muted small"
                                  title="Amazon prices by the visitor's location, so this storefront quotes an import price rather than its shelf price. A converted figure would be wrong by shipping and duty.">
                                  Price unavailable from this region
                                </span>
                              )}
                            {item.shipping > 0 && (
                              <span className="text-muted small ms-2">
                                + {formatCurrency(item.shipping, item.currency || 'USD')} shipping
                              </span>
                            )}
                          </div>
                          <div className="d-flex flex-wrap gap-1 mt-2">
                            {/* Unjudged results are still real listings, and one
                                of them is often the product being looked for —
                                the verdicts being absent says the AI pass did
                                not run, not that these are worthless. */}
                            {item.marketplaceItemId && (
                              <Button write variant="light" icon={FiPackage}
                                title="Create this as one of your own products"
                                onClick={() => setAddingListing({
                                  ...item, marketplace: item.marketplace || hitMarket,
                                })}>
                                Add to catalog
                              </Button>
                            )}
                            {hitMarket && item.marketplaceItemId && (
                              <Link className="btn btn-sm btn-light d-inline-flex align-items-center gap-1"
                                to={`/marketplace-tools/${hitMarket.toLowerCase()}/${encodeURIComponent(item.marketplaceItemId)}`}>
                                Details <FiArrowRight />
                              </Link>
                            )}
                            {item.url && (
                              <a href={item.url} target="_blank" rel="noreferrer"
                                className="btn btn-sm btn-light d-inline-flex align-items-center gap-1">
                                Open on marketplace <FiExternalLink size={13} />
                              </a>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>
                  )
                })}
              </div>
            </>
          ) : !judged && !channel?.message && (
            // `note` explains an empty result in a sentence — but on an empty
            // search the backend now puts that same sentence in `message`, and
            // both branches rendering it printed the identical box twice.
            // `message` wins because it is the one that can carry `retryable`.
            <div className="field-note" style={{ maxWidth: 'none' }}>
              <FiAlertCircle size={15} />
              <span>{channel?.note || 'No listings were found in the selected marketplaces.'}</span>
            </div>
          )}

          {/* The trace answers "why was that empty" — refused, rate limited and
              genuinely absent are indistinguishable without it. */}
          {channel?.steps?.length > 0 && (
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
          )}
        </Card>
      )}

      {/* ── Competitors for the product that was found ───────────── */}
      {rivals && (
        <div ref={rivalsRef}>
        <Card className="mt-3" title="Competitors"
          subtitle={rivals.tried?.length > 1
            ? `Searched for "${rivals.title}" — ${rivals.tried.slice(0, -1).map((t) => `"${t}"`).join(', ')} found nothing`
            : `Searched by keyword for "${rivals.title}" — from the title the marketplace returned`}>
          {rivalsBusy ? (
            <div className="text-center py-4">
              <span className="spinner-border spinner-border-sm text-primary me-2" />
              <span className="text-muted small">Searching for competing products…</span>
            </div>
          ) : (
            // similarMode opens the rejected list and renames it: a listing that
            // is NOT the same product is precisely what was asked for here.
            <JudgedListings result={rivals.result} similarMode />
          )}
        </Card>
        </div>
      )}

      {/* ── Price history for one of the matches ─────────────────── */}
      {judged && channel.matches?.length > 0 && (
        <MatchPriceHistory matches={channel.matches} rivals={rivals?.result?.matches} />
      )}

      {/* ── Your catalogue ───────────────────────────────────────── */}
      {catalog && (
        <Card className="mt-3" title={`In your catalogue (${catalog.totalMatches ?? catalogRows.length})`}
          subtitle="Products you already track. Marketplace listings are above.">
          {catalogRows.length > 0 ? (
            <div className="row g-3">{catalogRows.map((p) => <CatalogResult key={p.id} p={p} />)}</div>
          ) : (
            <EmptyState icon={FiBox} title="Not in your catalogue yet"
              message="Nothing here matches. If a marketplace listing above is the right product, add it to your catalogue." />
          )}

          {catalog.trace?.length > 0 && (
            <div className="mt-3 pt-3 border-top-soft">
              <button type="button" className="btn btn-link p-0 text-decoration-none d-flex align-items-center gap-2 fw-semibold"
                onClick={() => setShowTrace((s) => !s)}>
                {showTrace ? <FiChevronUp /> : <FiChevronDown />} Match trace ({catalog.trace.length} stages)
              </button>
              {showTrace && (
                <div className="mt-2 d-flex flex-column gap-1">
                  {catalog.trace.map((t, i) => <TraceRow key={i} t={t} />)}
                </div>
              )}
            </div>
          )}
        </Card>
      )}

      {/* ── Visually similar, from the image ─────────────────────── */}
      {imageHits && imageHits.length > 0 && (
        <Card className="mt-3" title={`Visually similar in your catalogue (${imageHits.length})`}
          subtitle="Matched against your own product images — near-identical pictures, not similar items.">
          <div className="row g-3">
            {imageHits.map((r) => (
              <div className="col-12 col-lg-6" key={r.productId}>
                <Link to={`/products/${r.productId}`} className="result-card text-reset text-decoration-none">
                  {r.imageUrl
                    ? <img src={r.imageUrl} alt={r.title} className="rounded-3 flex-shrink-0"
                      style={{ width: 64, height: 64, objectFit: 'contain', background: 'var(--app-bg)' }} />
                    : <div className="rounded-3 flex-shrink-0 d-grid"
                      style={{ width: 64, height: 64, placeItems: 'center', background: 'var(--app-bg)' }}>
                      <FiImage className="text-muted" />
                    </div>}
                  <div className="flex-grow-1" style={{ minWidth: 0 }}>
                    <div className="fw-semibold clamp-2">{r.title}</div>
                    <div className="text-muted small mt-1">
                      {r.sku && <span className="me-2">SKU {r.sku}</span>}
                      {r.brand && <span>· {r.brand}</span>}
                    </div>
                    <span className="mr-pill mr-pill--blue mt-2 d-inline-block">{r.similarityPct}% match</span>
                  </div>
                </Link>
              </div>
            ))}
          </div>
        </Card>
      )}

      {addingListing && (
        <SaveAsProductModal show onClose={() => setAddingListing(null)} listing={addingListing} />
      )}

      {!channel && !catalog && !busy && (
        <Card className="mt-3">
          <EmptyState icon={FiSearch} title="Run a search"
            message="Enter any identifier, a title, a product URL or an image above. The marketplaces and your own
                     catalogue are searched together." />
        </Card>
      )}
    </>
  )
}

// A trace line. `marketplace: "AI"` is not a channel — it is step 6 (judging)
// or the vision pass on an image, and reading it as a marketplace would print
// "Ai by MATCH", which says nothing.
const AI_STEP = { MATCH: 'AI matching', IMAGE: 'Image' }

const fmtTime = (iso) => {
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
      {st.detail && <span className="text-muted">— {st.detail}</span>}
    </div>
  )
}

// Price history without leaving the search.
//
// Offers everything the judge MATCHED — from the identifier lookup and from the
// competitor search that follows it. Both are tracked, so both have history,
// and listing only the first left three of four charts unreachable when the
// exact search returned one product and the keyword pass found the rest.
//
// Rejected lookalikes stay out: they are shown in the results but never
// tracked, so every one of those entries would open an empty chart, and a
// picker where most choices lead nowhere teaches people to stop opening it.
function MatchPriceHistory({ matches, rivals }) {
  const [sel, setSel] = useState(0)

  // The exact match first, then the competitors, deduplicated — an identifier
  // search and its keyword follow-up routinely return the same listing, and
  // charting it twice under two headings reads as two products.
  const options = useMemo(() => {
    const seen = new Set()
    return [...(matches || []), ...(rivals || [])]
      .filter((m) => {
        const key = `${m.marketplace}:${m.marketplaceItemId}`
        if (!m.marketplaceItemId || seen.has(key)) return false
        seen.add(key)
        return true
      })
  }, [matches, rivals])

  // Options change with each search, so an index held from the last one can
  // point past the end of this one.
  const active = options[sel] || options[0]
  if (!active?.marketplaceItemId) return null

  // Short enough that the browser's own dropdown stays near the width of the
  // box. A native popup is drawn at the width of its longest option and can
  // not be styled, so a 48-character title plus a price spilled across the
  // range buttons and into the next card.
  const label = (m) => {
    const t = m.title || m.marketplaceItemId || ''
    const short = t.length > 42 ? `${t.slice(0, 40).trim()}…` : t
    const where = [mpLabel(m.marketplace), m.storefront].filter(Boolean).join(' ')
    const price = m.price != null ? formatCurrency(m.price, m.currency || 'USD') : null
    return [short, where, price].filter(Boolean).join(' · ')
  }

  return (
    <>
      {/* The picker drives both cards below, so it sits above them, on its
          own row — inside the chart header it fought the range buttons for
          space. With a single listing there is nothing to choose between, so
          it is a line of text rather than a dropdown with one option. */}
      <div className="d-flex flex-wrap align-items-center gap-2 mt-3">
        <span className="text-muted small">
          {options.length > 1 ? `Charting 1 of ${options.length} listings:` : 'Charting:'}
        </span>
        {options.length > 1 ? (
          <select className="form-select form-select-sm w-auto" style={{ maxWidth: '100%' }}
            aria-label="Which listing to chart"
            value={options.indexOf(active)} onChange={(e) => setSel(Number(e.target.value))}>
            {options.map((m, i) => (
              <option key={`${m.marketplace}:${m.marketplaceItemId}`} value={i} title={m.title || ''}>
                {label(m)}
              </option>
            ))}
          </select>
        ) : (
          <span className="small fw-semibold text-truncate" style={{ minWidth: 0 }} title={active.title || ''}>
            {label(active)}
          </span>
        )}
      </div>

      <div className="row g-3 mt-0">
        <div className="col-12 col-lg-7">
          <ItemPriceHistory
            marketplace={active.marketplace}
            itemId={active.marketplaceItemId}
            storefront={active.storefront}
            currency={active.currency || 'USD'}
            title="Price history"
          />
        </div>
        <div className="col-12 col-lg-5">
          <ItemRecentChanges marketplace={active.marketplace} itemId={active.marketplaceItemId}
            storefront={active.storefront} />
        </div>
      </div>
    </>
  )
}

function CatalogResult({ p }) {
  const img = p.images?.[0]?.url || (typeof p.images?.[0] === 'string' ? p.images[0] : null)
  return (
    <div className="col-12 col-lg-6">
      <div className="result-card">
        {img ? (
          <img src={img} alt={p.title} className="rounded-3 flex-shrink-0"
            style={{ width: 64, height: 64, objectFit: 'cover' }} />
        ) : (
          <div className="rounded-3 flex-shrink-0 d-grid"
            style={{ width: 64, height: 64, placeItems: 'center', background: 'var(--app-bg)', color: 'var(--primary)' }}>
            <FiBox size={22} />
          </div>
        )}
        <div className="flex-grow-1" style={{ minWidth: 0 }}>
          <div className="d-flex align-items-start justify-content-between gap-2">
            <div className="fw-semibold clamp-2">{p.title}</div>
            {p.status && <StatusBadge status={p.status} />}
          </div>
          <div className="text-muted small mt-1">
            {p.brand && <span className="me-2">{p.brand}</span>}
            {p.sku && <span>· SKU {p.sku}</span>}
          </div>
          <Link to={`/products/${p.id}`} className="btn btn-sm btn-light d-inline-flex align-items-center gap-1 mt-2">
            Open in my catalogue <FiArrowRight />
          </Link>
        </div>
      </div>
    </div>
  )
}

const TRACE_ICON = {
  MATCHED: <FiCheckCircle className="text-success" />,
  NO_MATCH: <FiXCircle className="text-muted" />,
  SKIPPED: <FiMinusCircle className="text-muted" />,
}

function TraceRow({ t }) {
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
