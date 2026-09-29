import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  FiSearch, FiAlertTriangle, FiCheckCircle, FiChevronDown, FiChevronUp, FiEdit3, FiInfo, FiX,
  FiExternalLink,
} from 'react-icons/fi'
import Modal from '../common/Modal'
import Button from '../common/Button'
import MarketplaceLogo from '../common/MarketplaceLogo'
import ProductDraftFields from './ProductDraftFields'
import { ItemIdentifiers } from '../common/ListingCells'
import { marketplaceService } from '../../services/marketplaceService'
import { productService } from '../../services/productService'
import { competitorListingService } from '../../services/competitorListingService'
import { useMarketplaceCodes } from '../../hooks/useMarketplaces'
import { useNotification } from '../../context/NotificationContext'
import { brandStore, categoryStore } from '../../services/localMasterData'
import { detectKey, alternateKey, keywordLadder } from '../../utils/identifiers'
import {
  conditionOf, identifiersOf, suggestSku, primaryIdentifier, brandOf,
  scoreTone, looksLikeMatch, conflictLine,
} from '../../utils/listingToProduct'
import { REGIONS, mpLabel, shortItemId } from '../../utils/marketplaces'
import { formatCurrency } from '../../utils/format'

// Add a product by finding it on a marketplace, rather than typing it in.
//
// The old form demanded an ASIN, which is Amazon's identifier and nobody
// else's — an eBay-only seller could not add a product at all. Searching first
// also fixes the quieter problem: typed identifiers are frequently wrong, and
// a product whose ASIN is a typo is invisible to every search that follows.
//
// Three steps, because they are three different decisions: what to look for,
// which listing IS the product (and which are its competitors), and what to
// save. Manual entry stays one click away for anything the marketplaces have
// never heard of.

const KEYS = [
  ['ASIN', 'ASIN'],
  ['UPC', 'UPC'],
  ['EAN', 'EAN'],
  ['GTIN', 'GTIN'],
  ['MPN', 'MPN'],
  ['ITEM_ID', 'eBay item number'],
  ['TITLE', 'Product name'],
  ['URL', 'Product link'],
]
const LABEL = Object.fromEntries(KEYS)

// Rows shown per marketplace before the group collapses behind "N more".
const GROUP_PREVIEW = 3

// Which listing a row is, for selection state. eBay ids repeat across
// marketplaces in principle, so both halves are needed.
const rowKey = (r) => `${r.marketplace}:${r.marketplaceItemId}`

// FR-MATCH-003: only the two confident verdicts carry through as a decision,
// and missing evidence overrides even those. Everything else attaches as a
// CANDIDATE for review, which is what an unconfirmed match is.
const decisionFor = (r) => (r.missingEvidence?.length
  ? undefined
  : { MATCH: 'MATCHED', EQUIVALENT: 'EQUIVALENT' }[r.decision])

export default function AddProductWizard({ show, onClose, onManual }) {
  const qc = useQueryClient()
  const { notify } = useNotification()
  const { codes: marketplaces } = useMarketplaceCodes()

  // Suggestions from Master Data, as the manual product form uses. Free text
  // still: brand and category are plain strings on the API, and forcing a
  // picker would block a genuinely new value mid-entry.
  const { data: brands = [] } = useQuery({
    queryKey: ['masterData', 'brands'], queryFn: () => brandStore.list(),
  })
  const { data: categories = [] } = useQuery({
    queryKey: ['masterData', 'categories'], queryFn: () => categoryStore.list(),
  })

  const [step, setStep] = useState('find')
  const [value, setValue] = useState('')
  const [key, setKey] = useState('ASIN')
  const [autoKey, setAutoKey] = useState(true)   // until the user picks one
  // What the search that produced these results read the value as — which is
  // not always what was detected, because an ambiguous value is retried.
  const [searchedAs, setSearchedAs] = useState('')
  const [markets, setMarkets] = useState([])     // empty = every enabled channel
  // US by default, matching the search page: a listing read from an unnamed
  // storefront is a price with no country behind it.
  const [region, setRegion] = useState('US')
  const [result, setResult] = useState(null)
  const [similar, setSimilar] = useState(null)
  const [sourceKey, setSourceKey] = useState('')
  const [competitors, setCompetitors] = useState(() => new Set())
  // Open by default: the judge turns down the right listing often enough —
  // variants, renewed units, bundles — that hiding them strands whoever is
  // adding one of those.
  const [showRejected, setShowRejected] = useState(true)
  const [expanded, setExpanded] = useState(() => new Set())
  const [form, setForm] = useState({
    title: '', sku: '', brand: '', category: '', condition: '', ourPrice: '', color: '', size: '', model: '', packQuantity: '',
  })
  const [created, setCreated] = useState(null)

  const onValueChange = (v) => {
    setValue(v)
    // Detection stops the moment someone picks a type by hand — overriding it
    // and then watching it change back is worse than no detection at all.
    if (autoKey) {
      const guess = detectKey(v, { markets })
      if (guess) setKey(guess)
    }
  }

  const matches = useMemo(() => {
    if (!result) return []
    // judge on => matches/rejected; judge off or an older backend => items.
    return result.matches?.length || result.rejected?.length ? result.matches ?? [] : result.items ?? []
  }, [result])
  const rejected = useMemo(() => result?.rejected ?? [], [result])

  // The identifier pass answers "what is this?" and stops there: the waterfall
  // ends at the first channel that returns anything, so an ASIN yields one
  // Amazon listing and eBay is never asked. The name pass below is what finds
  // the same product on the other marketplaces.
  const seen = useMemo(
    () => new Set([...matches, ...rejected].map(rowKey)),
    [matches, rejected],
  )
  const byName = useMemo(() => {
    if (!similar) return { matches: [], rejected: [] }
    const found = similar.matches?.length || similar.rejected?.length
      ? similar.matches ?? [] : similar.items ?? []
    const fresh = (list) => (list ?? []).filter((r) => r.marketplaceItemId && !seen.has(rowKey(r)))
    return { matches: fresh(found), rejected: fresh(similar.rejected) }
  }, [similar, seen])

  const allRows = useMemo(
    () => [...matches, ...byName.matches, ...rejected, ...byName.rejected],
    [matches, byName, rejected],
  )
  const otherRejected = useMemo(() => [...rejected, ...byName.rejected], [rejected, byName])

  // Grouped by marketplace. Eight eBay listings read as one group with a count;
  // the same eight in a flat list read as eight separate decisions.
  const groups = useMemo(() => {
    const out = new Map()
    for (const r of [...matches, ...byName.matches]) {
      const code = String(r.marketplace || '').toUpperCase() || 'OTHER'
      if (!out.has(code)) out.set(code, [])
      out.get(code).push(r)
    }
    for (const rows of out.values()) rows.sort((a, b) => (b.score ?? 0) - (a.score ?? 0))
    return [...out.entries()]
  }, [matches, byName])

  const source = allRows.find((r) => rowKey(r) === sourceKey) || null

  const search = useMutation({
    mutationFn: ({ as }) => {
      const v = value.trim()
      const payload = {
        markets: markets.length ? markets : undefined,
        region: region || undefined,
        maxResults: 12,
        judge: true,
      }
      if (as === 'TITLE') payload.title = v
      else if (as === 'URL') payload.url = v
      else payload.identifiers = { [as]: v }
      return marketplaceService.channelSearch(payload)
    },
    onSuccess: (res, { as, retried }) => {
      setResult(res)
      const found = (res.matches?.length || res.rejected?.length ? res.matches ?? [] : res.items ?? [])
      if (!found.length && !(res.rejected ?? []).length) {
        // Twelve digits can be a barcode or an eBay item number, and only the
        // marketplace can settle it. One retry on the other reading beats
        // telling someone their real item number does not exist.
        const alt = retried ? null : alternateKey(as, value)
        if (alt) {
          setKey(alt); setAutoKey(false)
          notify.info(`Nothing found as ${LABEL[as] || as} — trying it as ${LABEL[alt] || alt}.`)
          search.mutate({ as: alt, retried: true })
          return
        }
        notify.info(res.message || res.note || 'Nothing found for that.')
        return
      }
      setSearchedAs(as)
      setSimilar(null)
      // The best match is the product; everything else the judge accepted is a
      // competitor, pre-ticked because that is nearly always the intent.
      const first = found[0] ?? (res.rejected ?? [])[0]
      setSourceKey(first ? rowKey(first) : '')
      // Not everything found: real scores mean a 12% listing is a different
      // product, and ticking it would put its price into the statistics.
      setCompetitors(new Set(found.filter(looksLikeMatch).map(rowKey)))
      setStep('pick')

      // Straight after, on the title the marketplace just returned — the one
      // string good enough to find the same product elsewhere, and the reason
      // this cannot start any earlier. Not awaited: the exact match is already
      // on screen with its own spinner underneath.
      // Cut back to the identifying words: a full marketplace title is
      // written for search engines, and querying with it finds nothing.
      if (as !== 'TITLE' && first?.title) pivot.mutate(first.title)
    },
    onError: (e) => notify.error(e?.message || 'The search failed.'),
  })

  // The identifier is deliberately dropped here: keeping it would re-run the
  // same exact lookup and return the same single listing.
  const pivot = useMutation({
    // Word by word, widest last: the full marketplace title finds nothing
    // because no rival writes it the same way, and the first query that
    // returns anything wins. Only an entirely empty response widens it.
    mutationFn: async (title) => {
      let last = null
      for (const query of keywordLadder(title)) {
        last = await marketplaceService.channelSearch({
          title: query,
          markets: markets.length ? markets : undefined,
          region: region || undefined,
          maxResults: 12,
          judge: true,
        })
        if (last?.matches?.length || last?.rejected?.length || last?.items?.length) return last
      }
      return last
    },
    onSuccess: (res) => {
      setSimilar(res)
      const found = res.matches?.length || res.rejected?.length ? res.matches ?? [] : res.items ?? []
      setCompetitors((s) => {
        const n = new Set(s)
        found.forEach((r) => { if (r.marketplaceItemId && looksLikeMatch(r)) n.add(rowKey(r)) })
        return n
      })
    },
    // Non-fatal: the exact match stands on its own, and a failed name search
    // should not look like a failed add.
    onError: () => {},
  })

  const toConfirm = () => {
    if (!source) return
    setForm({
      title: source.title || '',
      sku: suggestSku(source),
      brand: brandOf(source),
      category: '',
      condition: conditionOf(source.condition),
      ourPrice: '',
      color: '', size: '', model: '', packQuantity: '',
    })
    setStep('confirm')
  }

  const ids = source ? identifiersOf(source) : {}
  const primaryId = primaryIdentifier(ids)

  // An exact identifier lookup, not a text search: GET /products takes
  // `identifier`, so a product already holding this ASIN or barcode is found
  // reliably rather than by hoping its title contains the digits. Catching the
  // duplicate here is far cheaper than merging two product records later.
  const dupQ = useQuery({
    queryKey: ['products', 'dupCheck', primaryId],
    queryFn: () => productService.list({ identifier: primaryId, size: 5 }),
    enabled: step === 'confirm' && !!primaryId,
  })
  const duplicate = (dupQ.data?.content ?? []).find((p) => [
    ['asin', ids.asin], ['upc', ids.upc], ['ean', ids.ean], ['gtin', ids.gtin],
  ].some(([f, v]) => v && String(p[f] || '').toUpperCase() === String(v).toUpperCase()))

  const create = useMutation({
    mutationFn: async () => {
      const product = await productService.create({
        sku: form.sku.trim(),
        title: form.title.trim(),
        brand: form.brand.trim() || undefined,
        category: form.category.trim() || undefined,
        condition: form.condition || undefined,
        ourPrice: form.ourPrice === '' ? undefined : Number(form.ourPrice),
        // Variant facts the match judge compares; blanks are dropped by the
        // request mapper, so an unopened section sends nothing.
        color: form.color, size: form.size, model: form.model, packQuantity: form.packQuantity,
        ...ids,
      })
      // Attached one at a time, and allSettled: one listing the pack-size or
      // condition rules reject must not lose the others, and the product
      // itself already exists by then.
      const picked = allRows.filter((r) => competitors.has(rowKey(r)) && r.marketplaceItemId)
      const outcomes = await Promise.allSettled(picked.map((r) => competitorListingService.attach(product.id, {
        marketplace: r.marketplace,
        marketplaceItemId: r.marketplaceItemId,
        // Where it was found, so the Canadian listing is stored as Canadian.
        region: r.storefront,
        decision: decisionFor(r),
      })))
      return {
        product,
        attached: outcomes.filter((o) => o.status === 'fulfilled').length,
        failed: outcomes.filter((o) => o.status === 'rejected').length,
      }
    },
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: ['products'] })
      setCreated(res)
      setStep('done')
      notify.success('Product added')
    },
    onError: (e) => notify.error(e?.message || 'The product could not be created.'),
  })

  const close = () => {
    setStep('find'); setValue(''); setResult(null); setSimilar(null); setSearchedAs('')
    setSourceKey(''); setCompetitors(new Set())
    setCreated(null); setShowRejected(true); setKey('ASIN'); setAutoKey(true); setRegion('US')
    onClose()
  }

  const toggleCompetitor = (r) => setCompetitors((s) => {
    const n = new Set(s); const k = rowKey(r)
    n.has(k) ? n.delete(k) : n.add(k)
    return n
  })
  const toggleGroup = (code) => setExpanded((e) => {
    const n = new Set(e); n.has(code) ? n.delete(code) : n.add(code); return n
  })
  const trackAll = (rows, on) => setCompetitors((sel) => {
    const n = new Set(sel)
    rows.forEach((r) => (on ? n.add(rowKey(r)) : n.delete(rowKey(r))))
    return n
  })

  // One line of title, one of facts, price right-aligned. The five-line cards
  // this replaces turned eight eBay listings into a scroll nobody could hold in
  // their head.
  // `showMarket` puts the badge on the row itself. Inside a marketplace group
  // the heading already says it; the rejected list is not grouped, so without
  // this those rows named no marketplace at all.
  const Row = ({ r, showMarket = false }) => {
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

  const titles = {
    find: 'Add a product — find it',
    pick: 'Add a product — which one is it?',
    confirm: 'Add a product — check the details',
    done: 'Product added',
  }

  return (
    <Modal show={show} onClose={close} scrollable title={titles[step]}
      size={step === 'pick' ? 'xl' : 'lg'}
      // The two facts decided at the top and then scrolled away from: which
      // listing is the product, and how many will be tracked.
      subheader={step === 'pick' && allRows.length > 0 ? (
        <div className="d-flex flex-wrap align-items-center justify-content-between gap-2">
          <div className="small text-truncate" style={{ minWidth: 0 }}>
            {source
              ? <>Product: <strong>{source.title || source.marketplaceItemId}</strong></>
              : <span className="text-muted">Choose which listing is your product</span>}
          </div>
          <div className="d-flex align-items-center gap-2 flex-shrink-0">
            <span className="text-muted small">
              {competitors.size} competitor{competitors.size === 1 ? '' : 's'}
            </span>
            {competitors.size > 0 && (
              <button type="button" className="btn btn-sm btn-link p-0 text-decoration-none text-muted"
                onClick={() => setCompetitors(new Set())}>Clear</button>
            )}
          </div>
        </div>
      ) : null}
      footer={(
        <>
          {step === 'find' && (
            <>
              <Button variant="light" icon={FiEdit3} onClick={() => { close(); onManual?.() }}>
                Enter manually
              </Button>
              <Button icon={FiSearch} loading={search.isPending} disabled={!value.trim()}
                onClick={() => search.mutate({ as: key })}>Search</Button>
            </>
          )}
          {step === 'pick' && (
            <>
              <Button variant="light" onClick={() => setStep('find')}>Back</Button>
              <Button disabled={!source} onClick={toConfirm}>Continue</Button>
            </>
          )}
          {step === 'confirm' && (
            <>
              <Button variant="light" onClick={() => setStep('pick')}>Back</Button>
              <Button loading={create.isPending} disabled={!form.title.trim() || !form.sku.trim()}
                onClick={() => create.mutate()}>Add product</Button>
            </>
          )}
          {step === 'done' && <Button onClick={close}>Done</Button>}
        </>
      )}>

      {step === 'find' && (
        <>
          {/* One field, the way the Search page does it: the type sits inside
              the box because it qualifies what was typed. On its own row it
              read as an unrelated control, and the two never lined up. */}
          <label className="form-label" htmlFor="ap-value">What are you adding?</label>
          <div className="search-field">
            <select className="search-field__key" value={key} aria-label="What this value is"
              onChange={(e) => { setKey(e.target.value); setAutoKey(false) }}>
              {KEYS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
            </select>
            <span className="search-field__divider" />
            <FiSearch className="search-field__icon" size={16} />
            <input id="ap-value" className="search-field__input" value={value} autoFocus
              placeholder="Paste a link or barcode, or type the product name"
              onChange={(e) => onValueChange(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && value.trim()) search.mutate({ as: key }) }} />
            {value && (
              <button type="button" className="search-field__clear" aria-label="Clear"
                onClick={() => { setValue(''); setAutoKey(true) }}><FiX size={16} /></button>
            )}
          </div>
          <div className="mt-2 ps-1 text-muted small">
            {autoKey && value.trim()
              ? <>Detected as <strong>{LABEL[key]}</strong> — change it on the left if that is wrong.</>
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
      )}

      {step === 'pick' && (
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
            const open = expanded.has(code)
            const visible = open ? rows : rows.slice(0, GROUP_PREVIEW)
            const allTracked = rows.every((r) => competitors.has(rowKey(r)))
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
                    onClick={() => trackAll(rows, !allTracked)}>
                    {allTracked ? 'Untrack all' : 'Add all as competitors'}
                  </button>
                </div>
                <div className="d-flex flex-column gap-1">
                  {visible.map((r) => <Row key={rowKey(r)} r={r} />)}
                </div>
                {more > 0 && (
                  <button type="button" className="btn btn-sm btn-link p-0 text-decoration-none mt-1"
                    onClick={() => toggleGroup(code)}>
                    {open ? 'Show fewer' : `${more} more ${mpLabel(code)} listing${more === 1 ? '' : 's'}`}
                  </button>
                )}
              </div>
            )
          })}

          {/* An identifier search ends at the first marketplace that answers,
              so this is where the others appear — found by name, which is the
              only thing an ASIN and an eBay listing share. */}
          {pivot.isPending && (
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
                onClick={() => setShowRejected((v) => !v)}>
                {otherRejected.length} other listing{otherRejected.length === 1 ? '' : 's'} found — not judged a match
                {showRejected ? <FiChevronUp size={14} /> : <FiChevronDown size={14} />}
              </button>
              {showRejected && (
                <div className="d-flex flex-column gap-1 mt-2">
                  {otherRejected.map((r) => <Row key={rowKey(r)} r={r} showMarket />)}
                </div>
              )}
            </div>
          )}

          {allRows.length === 0 && !pivot.isPending && (
            <div className="text-muted small">
              Nothing came back. Go back and try a barcode or the product name, or enter the
              details by hand.
            </div>
          )}
        </>
      )}

      {step === 'confirm' && (
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
              {competitors.size > 0
                ? <>{competitors.size} listing{competitors.size === 1 ? '' : 's'} will be attached as
                  competitors and their prices followed from now on. Each is checked again against
                  the saved product, and the verdict shows on its Competitors tab.</>
                : 'No competitors ticked, so this product starts with nothing to compare against.'}
            </span>
          </div>
        </div>
      )}

      {step === 'done' && created && (
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
            <Link to={`/products/${created.product.id}`} onClick={close}>Open the product</Link>
            {' · '}
            {/* Weight, dimensions and description live on the full form. They
                affect fees rather than matching, so they were left out here —
                but they should be one click away, not a hunt. */}
            <Link to={`/products/${created.product.id}`} state={{ edit: true }} onClick={close}>
              Add more details
            </Link>
          </div>
        </div>
      )}
    </Modal>
  )
}
