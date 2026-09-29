// Turning a marketplace listing into a product record.
//
// Shared by the Add Product wizard and "Add to catalog" on a search result,
// because the two must produce the same product from the same listing — a SKU
// suggested one way here and another way there would quietly create duplicates
// that differ only in their code.

// Marketplaces write condition as free text — "New", "Good - Refurbished",
// "Pre-owned". The product master takes one of three values, and guessing NEW
// when it is unclear would compare a used unit against new prices.
export function conditionOf(raw) {
  const v = String(raw || '').toLowerCase()
  if (/refurb|renew/.test(v)) return 'REFURBISHED'
  if (/used|pre-owned|good|acceptable|very good/.test(v)) return 'USED'
  if (/new/.test(v)) return 'NEW'
  return ''
}

// Everything the listing knows that the product master has a field for.
export function identifiersOf(src) {
  const out = { asin: '', upc: '', ean: '', gtin: '', mpn: '', ebayItemId: '' }
  const FIELD = { ASIN: 'asin', UPC: 'upc', EAN: 'ean', GTIN: 'gtin', MPN: 'mpn' }
  for (const i of src?.identifiers || []) {
    const field = FIELD[String(i.type || '').toUpperCase()]
    const value = i.value ?? i.originalValue ?? i.normalizedValue
    if (field && value && !out[field]) out[field] = String(value)
  }
  const mp = String(src?.marketplace || '').toUpperCase()
  // The marketplace's own id for the listing: an ASIN on Amazon, an item
  // number on eBay, which is the identifier an eBay-only product hangs on.
  if (mp === 'AMAZON' && src?.marketplaceItemId && !out.asin) out.asin = src.marketplaceItemId
  if (mp === 'EBAY' && src?.marketplaceItemId) out.ebayItemId = src.marketplaceItemId
  return out
}

// Marketplace results carry no brand field at all — neither SearchResultItem
// nor MatchedListing has one. On Amazon the brand arrives in `seller`, because
// its catalogue returns no merchant and the adapter fills that field with the
// brand instead; on eBay `seller` is a real merchant username and writing it
// into a product's brand would record "techdeals" as the maker of the AirPods.
export function brandOf(src) {
  if (src?.brand) return src.brand
  return String(src?.marketplace || '').toUpperCase() === 'AMAZON' ? (src?.seller || '') : ''
}

// A starting SKU, not a final one — it is the client's own code, so it stays
// editable. Built from brand and title because "B0DGJDKHRQ" as a SKU tells a
// warehouse nothing.
export function suggestSku(src) {
  // The brand leads the SKU — but most titles already open with it, and
  // prefixing it again gave APPLE-APPLE-IPHONE-17.
  const brand = brandOf(src).trim()
  const title = String(src?.title || '').trim()
  const startsWithBrand = brand && title.toLowerCase().startsWith(brand.toLowerCase())
  const base = (startsWithBrand ? title : `${brand} ${title}`).trim()
  const words = base.replace(/[^A-Za-z0-9 ]+/g, ' ').split(/\s+/).filter(Boolean).slice(0, 4)
  const sku = words.join('-').toUpperCase().slice(0, 32)
  return sku || String(src?.marketplaceItemId || '').toUpperCase().slice(0, 32)
}

// The first identifier worth looking a duplicate up by.
export const primaryIdentifier = (ids) =>
  ids.asin || ids.upc || ids.ean || ids.gtin || ids.ebayItemId || ''

// Scores mean something now — they used to be 95 on everything, because the
// prompt's own example said 95 and the model copied it. So they are worth
// colouring: a 12 and a 95 are different answers, not different shades of yes.
export const scoreTone = (score) => {
  if (score == null) return 'text-muted'
  if (score >= 70) return 'text-success'
  if (score >= 50) return 'text-warning'
  return 'text-muted'
}

// Whether a listing is close enough to tick as a competitor without a person
// looking at it. Everything used to be ticked, which was survivable when every
// score was 95 and became harmful the moment they were real: four shoe sizes
// and a replacement cup would all have entered the price statistics.
export const looksLikeMatch = (r) =>
  r?.decision === 'MATCH' || r?.decision === 'EQUIVALENT' || (r?.score ?? 0) >= 50

// What it differs on, in a sentence. productValue is OUR product's, and
// candidateValue the listing's — the pair is the whole reason a rejection is
// readable: "size 11.5 vs 11" says which shoe is on screen.
export function conflictLine(r) {
  const rows = r?.conflictDetails?.length
    ? r.conflictDetails
    : (r?.conflicts || []).map((field) => ({ field }))
  if (!rows.length) return ''
  return rows.slice(0, 3).map((c) => (c.productValue != null && c.candidateValue != null
    ? `Different ${c.field}: ${c.productValue} vs ${c.candidateValue}`
    : `Different ${c.field}`)).join(' · ')
}
