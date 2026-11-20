import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FiSearch, FiEdit3 } from 'react-icons/fi'
import Modal from '../common/Modal'
import Button from '../common/Button'
import { FindStep, PickStep, ConfirmStep, DoneStep } from './AddProductSteps'
import { LABEL, rowKey, decisionFor } from './addProductHelpers'
import { marketplaceService } from '../../services/marketplaceService'
import { productService } from '../../services/productService'
import { competitorListingService } from '../../services/competitorListingService'
import { useMarketplaceCodes } from '../../hooks/useMarketplaces'
import { useNotification } from '../../context/NotificationContext'
import { brandStore, categoryStore } from '../../services/localMasterData'
import { detectKey, alternateKey, keywordLadder } from '../../utils/identifiers'
import {
  conditionOf, identifiersOf, suggestSku, primaryIdentifier, brandOf, looksLikeMatch,
} from '../../utils/listingToProduct'
import { errorMessage } from '../../services/apiError'

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
    onError: (e) => notify.error(errorMessage(e, 'The search failed.')),
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
    onError: (e) => notify.error(errorMessage(e, 'The product could not be created.')),
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
        <FindStep value={value} idKey={key} autoKey={autoKey} onValueChange={onValueChange}
          onKeyChange={(k) => { setKey(k); setAutoKey(false) }}
          onClear={() => { setValue(''); setAutoKey(true) }}
          onSearch={() => search.mutate({ as: key })}
          markets={markets} setMarkets={setMarkets} marketplaces={marketplaces}
          region={region} setRegion={setRegion} />
      )}

      {step === 'pick' && (
        <PickStep allRows={allRows} groups={groups} otherRejected={otherRejected}
          searchedAs={searchedAs} value={value} searching={pivot.isPending}
          selection={{ sourceKey, setSourceKey, competitors, toggleCompetitor, trackAll }}
          view={{ expanded, toggleGroup, showRejected, setShowRejected }} />
      )}

      {step === 'confirm' && (
        <ConfirmStep duplicate={duplicate} source={source} form={form} setForm={setForm} ids={ids}
          brands={brands} categories={categories} competitorCount={competitors.size} />
      )}

      {step === 'done' && created && (
        <DoneStep created={created} onClose={close} />
      )}
    </Modal>
  )
}
