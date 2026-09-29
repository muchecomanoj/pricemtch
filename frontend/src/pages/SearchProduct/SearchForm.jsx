import { useState } from 'react'
import { FiSearch, FiAlertCircle, FiRotateCcw, FiX, FiLock } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import ImageInput from '../../components/forms/ImageInput'
import { marketplaceService } from '../../services/marketplaceService'
import { validateIdentifier, detectKey } from '../../utils/identifiers'
import { destinationPayload } from '../../hooks/useDestination'
import { mpLabel, REGIONS } from '../../utils/marketplaces'
import { useAuth } from '../../context/AuthContext'

// FR-SRCH-001's input set, entered one key at a time.
//
// `valType` is what the value is validated as — the identifier types get a
// format and a check digit, the free-text ones do not. `field` is where the
// value lands in ChannelSearchRequest, which is a map for identifiers and a
// named property for the rest.
const KEYS = [
  { key: 'ASIN', label: 'ASIN', valType: 'ASIN', field: 'id', hint: 'e.g. B0BDHWDR12' },
  { key: 'UPC', label: 'UPC', valType: 'UPC', field: 'id', hint: '12 digits, e.g. 036000291452' },
  { key: 'EAN', label: 'EAN', valType: 'EAN', field: 'id', hint: '8 or 13 digits, e.g. 6925281993084' },
  { key: 'GTIN', label: 'GTIN', valType: 'GTIN', field: 'id', hint: '8–14 digits' },
  { key: 'MPN', label: 'MPN', valType: 'MPN', field: 'id', hint: 'Manufacturer part number' },
  // eBay's own listing number. Deliberately unvalidated: it arrives either bare
  // (257610723030) or in eBay's composite form (v1|257610723030|0), and the
  // backend works out which. A format check here would reject one of the two
  // shapes people actually paste.
  { key: 'ITEM_ID', label: 'Item ID', valType: 'KEYWORD', field: 'id', hint: 'e.g. 257610723030' },
  { key: 'TITLE', label: 'Title', valType: 'KEYWORD', field: 'title', hint: 'e.g. JBL Flip 6 Bluetooth Speaker' },
  { key: 'BRAND', label: 'Brand', valType: 'KEYWORD', field: 'brand', hint: 'e.g. JBL' },
  { key: 'URL', label: 'Paste a link', valType: 'KEYWORD', field: 'url', hint: 'Paste an Amazon or eBay product link' },
]
const keyMeta = (k) => KEYS.find((x) => x.key === k) || KEYS[0]

// How many listings to bring back, PER MARKETPLACE — with Amazon and eBay both
// enabled, 12 means up to 24 listings and roughly 20 seconds of judging at the
// backend's measured ~1.4 listings a second. A default people wait through.
//
// Raising it still works: the judge runs in batches of 40 up to a total of 120,
// so asking for more returns more. Anything past that total is still fetched
// and still unjudged, which is said out loud rather than capped — fetching 200
// and silently checking 120 is the one outcome worth preventing.
const DEFAULT_MAX = 12
const JUDGE_TOTAL = 120
// The backend's measured judging rate, for the "this will take a while" line.
const PER_SECOND = 1.4

// US by default, because that is the storefront the connector actually reads
// unless told otherwise. "Any region" left the backend to choose and the
// choice was invisible — a price whose country nobody stated cannot be
// compared with anything, which is the bug that started the whole storefront
// thread. It stays in the list for anyone who wants it deliberately.
const DEFAULT_REGION = 'US'

export const EMPTY_SEARCH = {
  key: 'ASIN', value: '', region: DEFAULT_REGION, image: null, maxResults: DEFAULT_MAX,
}

// "Require at least one identifier, URL, image, or sufficiently descriptive
// title." Three characters is where "descriptive" starts — below that a title
// matches half the catalogue and spends a chargeable call proving it.
const MIN_TEXT = 3

export function hasEnoughToSearch(f) {
  if (f.image) return true
  const v = (f.value || '').trim()
  if (!v) return false
  // A bare identifier is specific enough at any length; free text is not.
  return keyMeta(f.key).valType === 'KEYWORD' ? v.length >= MIN_TEXT : true
}

// Everything the search needs, derived once.
export function buildPayloads(form, markets, destination) {
  const meta = keyMeta(form.key)
  const { value } = validateIdentifier(meta.valType, form.value)

  const channel = { markets, maxResults: form.maxResults || DEFAULT_MAX }
  if (value) {
    if (meta.field === 'id') channel.identifiers = { [form.key]: value }
    else channel[meta.field] = value
  }
  if (form.region) channel.region = form.region
  if (form.image) channel.image = form.image
  // Omitted entirely when incomplete — see destinationPayload.
  const dest = destinationPayload(destination)
  if (dest) channel.destination = dest

  // The catalogue endpoint takes a single `query` string, so it gets the same
  // value whatever field it belongs to.
  return { catalogQuery: value, channel }
}

// What the chosen key means for the search, said rather than discovered.
function hintFor(key, valType, parsing) {
  if (parsing) return 'Reading the link…'
  if (key === 'URL') return 'The identifier and region are read from the link before anything is searched.'
  if (key === 'ITEM_ID') return "eBay's listing number — the digits in an eBay URL. Either format works."

  if (valType === 'KEYWORD') return 'Matches on words — every listing found is then checked against what you typed.'
  return 'Checked for length and check digit before anything is sent.'
}

export default function SearchForm({
  form, setForm, markets, setMarkets, channelCodes, onSearch, busy, onReset,
  destination, setDestination,
}) {
  const [parsing, setParsing] = useState(false)
  // Whether the region is still the default rather than a deliberate choice.
  // Mirrors how the identifier type stops auto-detecting once picked by hand.
  const [autoRegion, setAutoRegion] = useState(true)
  const set = (patch) => setForm((f) => ({ ...f, ...patch }))

  // Auto-detection runs until the user picks a type themselves, and then stops
  // for good. Someone who deliberately chose Brand and typed "Nike" has said
  // what they meant; overriding that on the next keystroke would be the form
  // arguing with them. Guessing is a convenience, never a correction.
  const [autoKey, setAutoKey] = useState(true)
  const [detected, setDetected] = useState(false)

  const onValueChange = (value) => {
    if (!autoKey) return set({ value })
    const guess = detectKey(value, { markets })
    // null means "nothing confidently identifiable" — the current choice stands
    // rather than falling back to a default.
    if (guess && guess !== form.key) {
      setDetected(true)
      return set({ value, key: guess })
    }
    return set({ value })
  }

  const meta = keyMeta(form.key)
  // Validation follows the dropdown: pick UPC and the value is length- and
  // check-digit checked; pick Title and it is not.
  const check = validateIdentifier(meta.valType, form.value)
  const tooShort = meta.valType === 'KEYWORD' && form.value.trim()
    && form.value.trim().length < MIN_TEXT
  const error = check.error || (tooShort ? `Enter at least ${MIN_TEXT} characters.` : null)
  // Searching spends marketplace and AI quota, so it is the first thing a
  // lapsed plan loses. The button stays visible but locked, with the reason,
  // rather than failing with a 403 after the wait.
  const { readOnly } = useAuth()
  const ready = hasEnoughToSearch(form) && !error && !readOnly

  // Pasting a link fills in the region it came from. Country matters: an
  // amazon.in link searched against the US catalogue reports "not found" when
  // the product simply is not sold there.
  const [parseNote, setParseNote] = useState(null)
  const onValueBlur = async () => {
    if (form.key !== 'URL' || !form.value.trim()) return
    setParsing(true)
    setParseNote(null)
    try {
      const parsed = await marketplaceService.parseUrl(form.value.trim())
      // A search or category page has no item in it. The backend says so
      // rather than erroring, and guessing an identifier from it would send a
      // chargeable search after nothing.
      if (!parsed?.itemId) {
        setParseNote('That link has no product in it — it looks like a search or category page.')
        return
      }
      // Switch to the identifier the link resolved to, so the search runs as a
      // normal exact lookup rather than as free text that happens to be a URL.
      set({
        key: KEYS.some((k) => k.key === parsed.identifierType) ? parsed.identifierType : 'ASIN',
        value: parsed.itemId,
        // A pasted link names its own storefront, and that beats the default
        // — but never a region the user picked by hand.
        region: autoRegion && parsed.countryCode ? parsed.countryCode : form.region,
      })
      setParseNote(null)
    } catch {
      setParseNote('That link could not be read. Search by identifier or title instead.')
    } finally {
      setParsing(false)
    }
  }

  // Amazon's Product Pricing API takes a marketplace id, not an address —
  // there is no parameter to pass a postcode to — so the adapter ignores the
  // destination entirely. eBay accepts it (contextualLocation) and it genuinely
  // changes the delivery costs that come back. The field is therefore real, but
  // only for one channel, and the label has to say which.
  const amazonOnly = markets.length === 1 && markets[0] === 'AMAZON'
  // maxResults is per channel, so what it costs depends on how many are in
  // play — "all connected" means every enabled one.
  const channels = markets.length || channelCodes.length || 1

  const refined = Boolean(
    form.region !== DEFAULT_REGION || markets.length || form.image
    || form.maxResults !== DEFAULT_MAX
    || destination?.country)

  return (
    <Card>
      {/* Two ways to search, each on its own row: a value you type, or a
          picture. The image sits here rather than with the scope controls
          below — it is another input, not a filter. */}
      <form onSubmit={(e) => { e.preventDefault(); if (ready) onSearch() }}>
        <div className={`search-field ${error ? 'is-invalid' : ''}`}>
          <select className="search-field__key" aria-label="Search by" value={form.key}
            onChange={(e) => { setAutoKey(false); setDetected(false); set({ key: e.target.value }) }}>
            {KEYS.map((k) => <option key={k.key} value={k.key}>{k.label}</option>)}
          </select>
          <span className="search-field__divider" />
          <FiSearch className="search-field__icon" aria-hidden="true" />
          <input
            className="search-field__input"
            aria-label={meta.label}
            aria-invalid={!!error}
            placeholder={meta.hint}
            value={form.value}
            onBlur={onValueBlur}
            onChange={(e) => onValueChange(e.target.value)}
          />
          {form.value && (
            <button type="button" className="search-field__clear" aria-label="Clear value"
              onClick={() => { setDetected(false); set({ value: '' }) }}>
              <FiX />
            </button>
          )}
          <span className="search-field__divider" />
          <div className="search-field__count">
            <input type="number" min="1" step="1" className="search-field__count-input"
              aria-label="How many results to return"
              value={form.maxResults}
              // Empty and 0 are allowed WHILE typing — clearing a field to
              // retype it is normal, and snapping the value back mid-keystroke
              // fights the user. Blur is where it gets settled.
              onChange={(e) => set({
                maxResults: e.target.value === '' ? '' : Math.max(0, Math.floor(+e.target.value) || 0),
              })}
              onBlur={() => { if (!form.maxResults) set({ maxResults: DEFAULT_MAX }) }}
            />
            <span>results</span>
          </div>
          <Button type="submit" icon={readOnly ? FiLock : FiSearch} loading={busy} disabled={!ready}
            title={readOnly ? 'Renew to use this' : undefined}
            className="flex-shrink-0">Search</Button>
        </div>

        <div className="mt-2 ps-1 d-flex flex-wrap align-items-center gap-3">
          {error ? (
            <span className="d-inline-flex align-items-start gap-1 text-danger small">
              <FiAlertCircle size={14} className="flex-shrink-0 mt-1" /> {error}
            </span>
          ) : parseNote ? (
            <span className="d-inline-flex align-items-start gap-1 small">
              <FiAlertCircle size={14} className="flex-shrink-0 mt-1" style={{ color: 'var(--warning)' }} />
              {parseNote}
            </span>
          ) : (
            <span className="text-muted small">
              {detected && autoKey && <span className="fw-semibold">Detected as {meta.label}. </span>}
              {hintFor(form.key, meta.valType, parsing)}
            </span>
          )}

          {form.key === 'ITEM_ID' && markets.includes('AMAZON') && (
            <span className="d-inline-flex align-items-start gap-1 small">
              <FiAlertCircle size={14} className="flex-shrink-0 mt-1" style={{ color: 'var(--warning)' }} />
              Amazon has no item ID — use ASIN for Amazon. This search will only find eBay listings.
            </span>
          )}

          {/* Per marketplace, so the number typed is not the number returned
              whenever more than one channel is searched. */}
          {channels > 1 && form.maxResults > 0 && (
            <span className="text-muted small">
              Up to {form.maxResults * channels} listings across {channels} marketplaces — about{' '}
              {Math.round((form.maxResults * channels) / PER_SECOND)}s of judging.
            </span>
          )}

          {form.maxResults * channels > JUDGE_TOTAL && (
            <span className="d-inline-flex align-items-start gap-1 small">
              <FiAlertCircle size={14} className="flex-shrink-0 mt-1" style={{ color: 'var(--warning)' }} />
              Only the first {JUDGE_TOTAL} get a verdict — the rest come back unchecked.
            </span>
          )}
        </div>
      </form>

      <div className="search-image mt-3">
        <span className="search-image__label">or search by image</span>
        {/* Honest about the mechanism AND the limits. Marketplaces still have
            no image search — we describe the photo and search for the words,
            which is why a clear shot of a branded product works and an
            unbranded object gives "black digital watch" and hundreds of hits. */}
        <p className="text-muted small mb-2">
          We describe your photo, then search the marketplaces for what we see. Works best on a
          clear photo of a recognisable product. Limited to about two image searches a minute.
        </p>
        {readOnly ? (
          <div className="text-muted small d-flex align-items-center gap-2">
            <FiLock size={13} /> Image search is paused while your plan is expired. Renew to use it.
          </div>
        ) : (
          <ImageInput value={form.image} onChange={(v) => set({ image: v })} maxDim={512} height={70} />
        )}
      </div>

      {/* Where to look — these narrow the search rather than defining it. */}
      <div className="search-scope mt-3 pt-3 border-top-soft">
        {/* Two country pickers sit side by side and they are NOT the same
            question. This one narrows WHERE WE LOOK — which country's
            storefront. The other says where the goods are going, which is what
            the delivery cost is figured from. Labelled by the question each
            answers rather than both being called a place. */}
        <div className="search-scope__field">
          <label className="form-label" htmlFor="f-region">Search in</label>
          <select id="f-region" className="form-select form-select-sm" value={form.region}
            onChange={(e) => { setAutoRegion(false); set({ region: e.target.value }) }}>
            {REGIONS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
          <div className="form-text">Which storefront to search</div>
        </div>

        <div className="search-scope__field">
          <label className="form-label" htmlFor="f-market">Marketplace</label>
          <select id="f-market" className="form-select form-select-sm" value={markets[0] || ''}
            onChange={(e) => setMarkets(e.target.value ? [e.target.value] : [])}>
            <option value="">All connected</option>
            {channelCodes.map((code) => <option key={code} value={code}>{mpLabel(code)}</option>)}
          </select>
          <div className="form-text">Channels to include</div>
        </div>

        {/* Where it ships TO. Without this a landed price is a number with no
            destination behind it, which is not comparable to anything. */}
        <div className="search-scope__field">
          <label className="form-label" htmlFor="f-dest-country">Deliver to</label>
          <select id="f-dest-country" className="form-select form-select-sm"
            value={destination?.country || ''}
            onChange={(e) => setDestination({ country: e.target.value })}>
            <option value="">Not set</option>
            {REGIONS.filter(([v]) => v).map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
          <div className="form-text">
            {amazonOnly ? 'Amazon ignores this — eBay only' : 'eBay delivery costs'}
          </div>
        </div>

        <div className="search-scope__field">
          <label className="form-label" htmlFor="f-dest-post">Postcode</label>
          <input id="f-dest-post" className="form-control form-control-sm"
            // A postcode without a country is ignored by the backend, because
            // 10001 is Manhattan in the US and a Copenhagen suburb in Denmark.
            // Disabled rather than silently discarded.
            disabled={!destination?.country}
            placeholder={destination?.country ? 'Optional' : 'Pick a country first'}
            value={destination?.postalCode || ''}
            onChange={(e) => setDestination({ postalCode: e.target.value })} />
          <div className="form-text">
            {amazonOnly ? 'No effect on Amazon' : "Sharpens eBay's estimate"}
          </div>
        </div>

        {refined && (
          <button type="button" className="btn btn-sm btn-ghost ms-auto align-self-end d-inline-flex align-items-center gap-1"
            // Clearing everything restores auto-detection too — otherwise a
            // single manual pick would keep it switched off for the rest of the
            // session, long after the search that needed it.
            onClick={() => { setAutoKey(true); setDetected(false); onReset() }}>
            <FiRotateCcw size={13} /> Clear all
          </button>
        )}
      </div>
    </Card>
  )
}
