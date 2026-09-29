// Shared marketplace helpers used by both the super-admin (platform) and client
// (per-tenant) Marketplaces views, so labels and status logic stay in one place.

// Friendly names for the codes the API returns (AMAZON, EBAY, KEEPA, WEB, …).
const MP_LABEL = { AMAZON: 'Amazon', EBAY: 'eBay', KEEPA: 'Amazon (Keepa)', WALMART: 'Walmart', WEB: 'Open Web' }

// eBay's Browse API returns a composite item id — v1|318811936051|0 — made of
// the encoding-scheme version, the real item number, and the variation id
// (0 meaning the listing itself rather than a specific variant).
//
// Only the middle segment means anything to a person: it is what appears in an
// ebay.com/itm/ URL. The full value is still what gets sent and linked, because
// that is the identifier — this shortens the DISPLAY only.
//
// A non-zero variation is kept, because "which variant" changes what the price
// refers to and silently dropping it would make two different rows look alike.
export function shortItemId(id) {
  if (typeof id !== 'string') return id
  const m = /^v\d+\|([^|]+)\|(\d+)$/.exec(id)
  if (!m) return id
  return m[2] === '0' ? m[1] : `${m[1]} · variant ${m[2]}`
}

export const mpLabel = (code) =>
  MP_LABEL[code] || (code ? code[0] + code.slice(1).toLowerCase() : '—')

// Amazon's marketplace ids are opaque, so the common ones are named here for
// when a response carries the id but no countryCode.
const AMAZON_MARKETPLACE_COUNTRY = {
  ATVPDKIKX0DER: 'US',
  A2EUQ1WTGCTBG2: 'CA',
  A1AM78C64UM0Y8: 'MX',
  A1F83G8C2ARO7P: 'GB',
  A1PA6795UKMFR9: 'DE',
}

const regionName = (cc) => {
  try { return new Intl.DisplayNames(['en'], { type: 'region' }).of(cc) } catch { return cc }
}

// National storefronts a search or a price read can be pointed at.
// Only US and CA are authorised on the Amazon connector today. The others are
// offered because a pasted link resolves to them, and reporting "not sold in
// this region" beats a silent empty result.
export const REGIONS = [
  ['', 'Any region'], ['US', 'United States'], ['CA', 'Canada'], ['GB', 'United Kingdom'],
  ['DE', 'Germany'], ['FR', 'France'], ['IT', 'Italy'], ['ES', 'Spain'],
  ['IN', 'India'], ['JP', 'Japan'], ['AU', 'Australia'], ['MX', 'Mexico'],
]

// Storefronts Amazon answers for through the in-house page reader rather than
// SP-API, because this account is only authorised for US and CA.
//
// Two consequences, both visible: the read takes about twenty seconds instead
// of two, and it often comes back with no price at all. Amazon prices by the
// visitor's location, so a reader sitting in India is quoted an import price
// in rupees — Amazon's own docs call the converted figure an estimate, not the
// shelf price, so the backend omits it rather than publish a number that is
// wrong by the cost of international shipping and duty.
const SCRAPED_REGIONS = new Set(['GB', 'DE', 'IN'])
export const isScrapedRegion = (code) => SCRAPED_REGIONS.has(String(code || '').toUpperCase())

// The storefront a saved price most likely came from, judged by its currency.
// The saved record carries a currency but no storefront, so this is the only
// clue. EUR is left out on purpose: four storefronts price in euros and a
// guess between them would be wrong three times in four.
const CURRENCY_REGION = { USD: 'US', CAD: 'CA', GBP: 'GB', MXN: 'MX', INR: 'IN', JPY: 'JP', AUD: 'AU' }
export const regionForCurrency = (currency) => CURRENCY_REGION[currency] || null

// The two-letter storefront a response came from, or null when it does not say.
export function storefrontCode({ countryCode, marketplaceId } = {}) {
  const cc = countryCode
    || /^EBAY_([A-Z]{2})$/.exec(marketplaceId || '')?.[1]
    || AMAZON_MARKETPLACE_COUNTRY[marketplaceId]
  return cc ? String(cc).toUpperCase() : null
}

// A marketplace read that failed, in words a user can act on.
//
// The backend now fails honestly when a storefront is not authorised, instead
// of quietly answering with the US listing. That is the right behaviour and a
// bad error message: "Amazon connector is not authorized for UK (HTTP 403)"
// reads as a bug in the app rather than a connector that was never set up.
export function marketplaceErrorText(error, shopName) {
  const raw = error?.message || ''
  if (/not authoriz|not authoris|403/i.test(raw)) {
    return `${shopName || 'That storefront'} is not connected yet, so its listings cannot be read.
      Ask an administrator to authorise it, or pick another storefront.`.replace(/\s+/g, ' ')
  }
  if (/unknown region/i.test(raw)) return raw
  return raw || `${shopName || 'That storefront'} could not be read.`
}

// Which national storefront a live read came from — "Canada", "United States".
//
// "Amazon" and "eBay" are not one shop each: amazon.com and amazon.ca are
// separate catalogues, and eBay shows the same listing converted into each
// site's currency. A price without its storefront cannot be compared with
// anything. Null when the response does not say, which is not the same as US.
export function storefrontLabel(source) {
  const cc = storefrontCode(source || {})
  return cc ? regionName(cc) : null
}

// GET /marketplaces returns codes (strings) or objects — normalize to codes.
export const marketplaceCodes = (list) =>
  (Array.isArray(list) ? list : [])
    .map((m) => (typeof m === 'string' ? m : m.code || m.name))
    .filter(Boolean)

// Find a health record for a code (case-insensitive on `marketplace`).
export const healthFor = (health, code) =>
  (Array.isArray(health) ? health : []).find(
    (h) => (h.marketplace || '').toUpperCase() === (code || '').toUpperCase(),
  )

// Map a /marketplaces/health record to a StatusBadge tone + label.
//   healthy true            → Connected (green)
//   status NOT_CONFIGURED   → Not configured (amber)
//   status DOWN / other     → Down / the raw status (red)
//   no record               → Unknown (grey)
export function healthBadge(h) {
  if (!h) return { tone: 'secondary', label: 'Unknown' }
  if (h.healthy) return { tone: 'success', label: 'Connected' }
  if (h.status === 'NOT_CONFIGURED') return { tone: 'warning', label: 'Not configured' }
  return { tone: 'danger', label: h.status === 'DOWN' ? 'Down' : (h.status || 'Unavailable') }
}

// A marketplace is usable by a client's search when its health says so.
export const isUsable = (h) => Boolean(h?.healthy)

// Admin config status (GET /admin/marketplaces .status) -> StatusBadge tone + label.
//   NOT_CONFIGURED → amber   CONFIGURED → blue (test to verify)
//   CONNECTED → green        ERROR → red (caller shows lastMessage)
export function mpStatusBadge(status) {
  switch (status) {
    case 'CONNECTED': return { tone: 'success', label: 'Connected' }
    case 'CONFIGURED': return { tone: 'info', label: 'Configured (test to verify)' }
    case 'ERROR': return { tone: 'danger', label: 'Error' }
    case 'NOT_CONFIGURED': return { tone: 'warning', label: 'Not configured' }
    default: return { tone: 'secondary', label: status || 'Unknown' }
  }
}
