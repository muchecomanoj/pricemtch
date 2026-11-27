import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { FiSearch, FiAlertCircle, FiPlus, FiUpload } from 'react-icons/fi'
import { useRecentSearches } from '../../hooks/useRecentSearches'
import { isProviderError, isUnpriced } from './ProviderError'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import EmptyState from '../../components/common/EmptyState'
import SearchForm, { EMPTY_SEARCH, buildPayloads } from './SearchForm'
import JudgedListings from './JudgedListings'
import ChannelCard from './ChannelResults'
import MatchPriceHistory from './MatchPriceHistory'
import { RecentSearches, CatalogCard, ImageHitsCard } from './SearchResults'
import SaveAsProductModal from '../../components/products/SaveAsProductModal'
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
import { errorMessage } from '../../services/apiError'

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
  // What the last competitor search ran on, so Try again can repeat it.
  const lastSimilar = useRef(null)
  const [rivalsBusy, setRivalsBusy] = useState(false)
  const rivalsRef = useRef(null)

  // FRD 13.1 puts intake on this screen: a search that finds nothing should
  // flow straight into creating the product, rather than sending someone to
  // another page to retype what they just searched for.
  const { can, user } = useAuth()
  const { recent, remember, forget, clear: clearRecent } = useRecentSearches(user?.id ?? user?.email)

  // Fills the form exactly as it was and runs it, so the result is the same
  // search rather than an approximation of it.
  const rerun = (e) => {
    const f = { ...EMPTY_SEARCH, key: e.key, value: e.value, region: e.region,
      maxResults: e.maxResults || EMPTY_SEARCH.maxResults }
    setForm(f)
    setMarkets(e.markets || [])
    run({ form: f, markets: e.markets || [] })
  }
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
    onError: (e) => notify.error(errorMessage(e, 'Could not create the product.')),
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
  // `m` overrides the marketplaces the same way, for re-running a recent search.
  const run = async ({ force = false, form: f = form, markets: m = markets, retried = false } = {}) => {
    setRivals(null)
    // What was typed, not what a retry turned it into below.
    if (!retried) remember(f, m)
    const { catalogQuery, channel: payload } = buildPayloads(f, m, destination)
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
          note: errorMessage(ch.reason, 'The marketplace search failed.') })
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

      // Only a search that LOOKED and found nothing is worth re-asking as the
      // other identifier type. One that never reached the marketplace (or
      // failed outright) answered nothing, and a second try would replace its
      // "couldn't reach" message with a second failure under another name.
      // UNPRICED found the product (it just has no price), so the identifier
      // was right and asking again under another type would be wrong too.
      const looked = found && !isProviderError(found) && !isUnpriced(found)
      const empty = !seed && !(found?.rejected?.length)
      if (looked && empty && !retried) retryAs = alternateKey(f.key, f.value)
    } finally {
      setBusy(false)
      setRefreshing(false)
    }

    if (retryAs) {
      const next = { ...f, key: retryAs }
      setForm(next)
      notify.info(`Nothing found as ${KEY_LABEL[f.key] || f.key} — trying it as ${
        KEY_LABEL[retryAs] || retryAs}.`)
      await run({ force, form: next, markets: m, retried: true })
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
    lastSimilar.current = [r, f]
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
        // Unreachable is not empty: widening the query would only ask a
        // marketplace that isn't answering again, and then report "nothing".
        if (anything || isProviderError(last)) {
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
        result: { matches: [], rejected: [], message: errorMessage(e, 'The competitor search failed.') },
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

      {recent.length > 0 && (
        <RecentSearches recent={recent} busy={busy} onRerun={rerun} onForget={forget} onClear={clearRecent} />
      )}

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
        <ChannelCard channel={channel} judged={judged} hitMarket={hitMarket} busy={busy} refreshing={refreshing}
          onRefresh={() => run({ force: true })} onRetry={() => run()}
          // Offered only when the search was BY an identifier. On a title
          // search every card would re-run a near-identical query.
          onFindSimilar={form.key === 'TITLE' ? undefined : findSimilar}
          similarBusy={rivalsBusy} onAdd={setAddingListing} />
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
            <JudgedListings result={rivals.result} similarMode
              onRetry={lastSimilar.current ? () => findSimilar(...lastSimilar.current) : undefined} />
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
        <CatalogCard catalog={catalog} showTrace={showTrace} onToggleTrace={() => setShowTrace((s) => !s)} />
      )}

      {/* ── Visually similar, from the image ─────────────────────── */}
      {imageHits && imageHits.length > 0 && <ImageHitsCard hits={imageHits} />}

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
