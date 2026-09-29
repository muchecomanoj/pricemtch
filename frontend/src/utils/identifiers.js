// Identifier normalisation and validation — FRD §4.2.
//
// "Validate UPC/EAN/GTIN length and check digit; reject malformed values
//  BEFORE external calls."
//
// The emphasis is the point. Marketplace lookups are chargeable and rate
// limited, so a barcode with a typo must never reach one: the call costs money
// and comes back empty, which reads as "not sold here" rather than "you mistyped
// it". A check digit is arithmetic — there is no reason to spend an API call
// discovering that 1942533927 is not a real UPC.
//
// This is a convenience gate, not a security boundary. The API is callable
// directly, so the backend has to validate too.

// Characters that survive a copy-paste from a spreadsheet, a PDF or a webpage
// and then silently break an exact-match lookup: zero-width space, zero-width
// non-joiner/joiner, BOM, and the various non-breaking spaces.
const INVISIBLE = /[\u200B-\u200D\uFEFF\u00A0\u2000-\u200A\u202F\u205F\u3000]/g

// Separators people put in barcodes and part numbers when reading them aloud.
const SEPARATORS = /[\s\-‐‑‒–—―_.]/g

// Trim, drop invisible characters, strip separators, and uppercase.
// Numeric types keep only digits; a stray letter is then caught as malformed
// rather than silently deleted.
export function normalize(type, raw) {
  if (raw == null) return ''
  const base = String(raw).replace(INVISIBLE, '').trim()
  if (type === 'KEYWORD' || type === 'MPN') return base.replace(/\s+/g, ' ')
  return base.replace(SEPARATORS, '').toUpperCase()
}

// GS1 check digit, used by UPC-A, EAN-8, EAN-13, GTIN-14 and ISBN-13.
// Weights alternate 3,1 from the rightmost DATA digit leftwards.
function gs1CheckDigitValid(digits) {
  const body = digits.slice(0, -1)
  const expected = Number(digits[digits.length - 1])
  let sum = 0
  for (let i = 0; i < body.length; i += 1) {
    const fromRight = body.length - 1 - i
    sum += Number(body[i]) * (fromRight % 2 === 0 ? 3 : 1)
  }
  return (10 - (sum % 10)) % 10 === expected
}

// ISBN-10 is a different scheme entirely: weights 10..1, modulo 11, and the
// check "digit" may be X for ten.
function isbn10Valid(v) {
  if (!/^[0-9]{9}[0-9X]$/.test(v)) return false
  let sum = 0
  for (let i = 0; i < 9; i += 1) sum += Number(v[i]) * (10 - i)
  sum += v[9] === 'X' ? 10 : Number(v[9])
  return sum % 11 === 0
}

const DIGITS_ONLY = /^[0-9]+$/

// Per-type rules. `lengths` is every valid length for that scheme.
const RULES = {
  ASIN: {
    label: 'ASIN',
    test: (v) => (/^[A-Z0-9]{10}$/.test(v) ? null : 'An ASIN is exactly 10 letters or digits.'),
  },
  UPC: { label: 'UPC', lengths: [12] },
  EAN: { label: 'EAN', lengths: [8, 13] },
  JAN: { label: 'JAN', lengths: [8, 13] },       // JAN is an EAN-13 with a JP prefix
  GTIN: { label: 'GTIN', lengths: [8, 12, 13, 14] },
  ISBN: {
    label: 'ISBN',
    test: (v) => {
      if (v.length === 10) return isbn10Valid(v) ? null : 'That ISBN-10 fails its check digit.'
      if (v.length === 13) {
        if (!DIGITS_ONLY.test(v)) return 'An ISBN-13 is 13 digits.'
        return gs1CheckDigitValid(v) ? null : 'That ISBN-13 fails its check digit.'
      }
      return 'An ISBN is 10 or 13 characters.'
    },
  },
  // No public format. Validating it would reject legitimate part numbers.
  MPN: { label: 'MPN', test: () => null },
  KEYWORD: { label: 'Keyword', test: () => null },
}

function numericRule(rule, v) {
  if (!DIGITS_ONLY.test(v)) return `A ${rule.label} contains digits only.`
  if (!rule.lengths.includes(v.length)) {
    const list = rule.lengths.length > 1
      ? `${rule.lengths.slice(0, -1).join(', ')} or ${rule.lengths[rule.lengths.length - 1]}`
      : String(rule.lengths[0])
    return `A ${rule.label} is ${list} digits — you entered ${v.length}.`
  }
  // The length being right is what makes a wrong check digit meaningful: it
  // means a digit was mistyped or transposed, not that the value is truncated.
  return gs1CheckDigitValid(v) ? null : `That ${rule.label} fails its check digit — one of the digits is wrong.`
}

// -> { ok, value, error }
//   value is the canonical form, which is what should be sent and stored.
//   An empty input is `ok` with an empty value; requiredness is a separate rule.
export function validateIdentifier(type, raw) {
  const value = normalize(type, raw)
  if (!value) return { ok: true, value: '', error: null }

  const rule = RULES[type]
  if (!rule) return { ok: true, value, error: null }

  const error = rule.test ? rule.test(value) : numericRule(rule, value)
  return { ok: !error, value, error }
}

// react-hook-form adapter: returns true or the message.
export const identifierValidator = (type) => (raw) => validateIdentifier(type, raw).error || true

// ── Guessing the identifier type from what was typed ─────────────
//
// Returns a dropdown key, or null when nothing is confidently identifiable —
// null meaning "leave the user's choice alone", never "default to something".
//
// Order matters: the most specific shapes are tested first, because several
// overlap. The hard case is twelve digits, which is both a UPC and the usual
// length of an eBay item number; the check digit separates them, since a real
// UPC always passes it and an item number almost never will.
const URL_RE = /^https?:\/\//i
const EBAY_COMPOSITE_RE = /^v\d+\|[^|]+\|\d+$/
const ASIN_RE = /^B[A-Z0-9]{9}$/i
const MPN_RE = /^[A-Za-z0-9][A-Za-z0-9\-/._]*$/

// What each key is called in a sentence.
export const KEY_LABEL = {
  ASIN: 'an ASIN', UPC: 'a UPC', EAN: 'an EAN', GTIN: 'a GTIN', MPN: 'an MPN',
  ITEM_ID: 'an eBay item number', TITLE: 'a product name', BRAND: 'a brand', URL: 'a link',
}

// The other thing the same string could be, or null when there is no second
// reading. Twelve digits is the case that matters: a valid UPC check digit is
// only evidence, not proof — an eBay item number passes it about one time in
// ten, and 135344088794 is exactly that. The caller uses this to retry once
// when the first reading finds nothing, rather than telling someone their real
// item number does not exist.
export function alternateKey(key, raw) {
  const v = (raw || '').trim()
  const compact = v.replace(/[\s-]/g, '')
  if (!/^\d+$/.test(compact) || compact.length !== 12) return null
  // Punctuation rules out an item number: eBay never writes them with separators.
  if (key === 'UPC') return /^\d+$/.test(v) ? 'ITEM_ID' : null
  if (key === 'ITEM_ID') return validateIdentifier('UPC', compact).error ? null : 'UPC'
  return null
}

export function detectKey(raw, { markets = [] } = {}) {
  const v = (raw || '').trim()
  if (!v) return null

  if (URL_RE.test(v) || /\b(amazon|ebay)\.[a-z.]{2,}\//i.test(v)) return 'URL'
  if (EBAY_COMPOSITE_RE.test(v)) return 'ITEM_ID'

  // Separators are how people paste barcodes, so they are stripped before a
  // length is measured — but ONLY for that. A hyphenated part number like
  // 910-006556 is nine digits once stripped, and treating stripped digits as a
  // number in its own right turns every such MPN into an eBay item id.
  const compact = v.replace(/[\s-]/g, '')
  if (ASIN_RE.test(compact)) return 'ASIN'

  if (/^\d+$/.test(compact)) {
    const n = compact.length
    const passes = (type) => !validateIdentifier(type, compact).error
    // Searching eBay alone makes a bare number an item number by intent, even
    // when it happens to be a structurally valid UPC.
    const ebayOnly = markets.length === 1 && markets[0] === 'EBAY'
    // eBay item numbers are never written with separators, so anything the
    // user punctuated is a part number rather than an item id.
    const unpunctuated = /^\d+$/.test(v)

    if (n === 12) {
      if (ebayOnly) return 'ITEM_ID'
      if (passes('UPC')) return 'UPC'
      return unpunctuated ? 'ITEM_ID' : 'MPN'
    }
    if (n === 13) return passes('EAN') ? 'EAN' : null
    if (n === 8 || n === 14) return passes('GTIN') ? 'GTIN' : null
    // Nothing else in this range is a barcode length, so an unpunctuated run of
    // digits here is an item number by elimination. A punctuated one is not.
    if (unpunctuated && n >= 9 && n <= 12) return 'ITEM_ID'
    return MPN_RE.test(v) ? 'MPN' : null
  }

  // Words and spaces are a product name.
  if (/\s/.test(v)) return 'TITLE'
  // A single run of letters is a brand far more often than a one-word product.
  if (/^[A-Za-z]+$/.test(v) && v.length <= 24) return 'BRAND'
  // Letters mixed with digits or part-number punctuation: 910-006556, MWP22AM/A.
  if (MPN_RE.test(v)) return 'MPN'
  return 'TITLE'
}

// ── Turning a marketplace title into a keyword query ─────────────
//
// Marketplace titles are written for search engines, not for searching WITH:
//
//   Apple AirPods 4 Wireless Earbuds, Bluetooth Headphones, Personalized
//   Spatial Audio, Sweat and Water Resistant, USB-C Charging Case, Up to 30
//   Hours of Battery Life
//
// Sent back as a keyword query, that finds almost nothing — every extra word
// narrows the match, and no rival seller writes their title the same way. What
// identifies the product is the front of it: brand, model, maybe the variant.
//
// So the tail is cut at the first punctuation that starts a marketing clause,
// bracketed asides are dropped, and what remains is capped. Deliberately
// conservative: cutting too much finds the wrong product, which is worse than
// finding fewer of the right one.
const CLAUSE_BREAK = /[,;|(){}[\]]|( - )|(–)|(—)/
const FILLER = new Set(['with', 'for', 'and', 'the', 'a', 'an', 'of', 'up', 'to', 'in', 'by'])

export function keywordsFromTitle(raw, { maxWords = 8 } = {}) {
  const head = String(raw || '').split(CLAUSE_BREAK)[0] || ''
  const words = head
    .replace(/[^\p{L}\p{N}+\-/. ]/gu, ' ')
    .split(/\s+/)
    .filter(Boolean)
  const kept = []
  for (const w of words) {
    // Filler is dropped only in the middle: a leading "The" is part of some
    // brand names, and stopping at one would truncate the query to nothing.
    if (kept.length && FILLER.has(w.toLowerCase())) continue
    kept.push(w)
    if (kept.length >= maxWords) break
  }
  return kept.join(' ').trim() || String(raw || '').trim()
}

// Progressively broader queries, most specific first.
//
// One query is a guess at how specific to be, and the right answer differs per
// product: "TP-Link Archer AX73 AX5400 Dual Band Gigabit Wi-Fi" finds nothing
// because no rival writes it that way, while "TP-Link Archer" finds routers
// that are not this one. So it is tried in steps, and the first step that
// returns anything wins.
//
// The floor is deliberate. Below about three words a query stops describing a
// product — "TP-Link" alone matches a catalogue — and a competitor set full of
// the wrong product is worse than an empty one, because it is priced against.
export function keywordLadder(raw, { maxWords = 8, minWords = 3, steps = 3 } = {}) {
  const full = keywordsFromTitle(raw, { maxWords })
  const words = full.split(/\s+/).filter(Boolean)
  if (words.length <= minWords) return [full]

  const out = [full]
  // Roughly halving each time rather than one word at a time: dropping a
  // single word rarely changes what a marketplace returns, and each attempt is
  // a chargeable call.
  let n = words.length
  while (out.length < steps) {
    n = Math.floor(n / 2) + 1
    if (n < minWords) n = minWords
    const next = words.slice(0, n).join(' ')
    if (next === out[out.length - 1]) break
    out.push(next)
    if (n === minWords) break
  }
  return out
}
