import { useCallback, useState } from 'react'

// Where the goods are being shipped to, which is what makes a landed price
// comparable. FR-PRICE-002: "calculate comparable landed price using
// deterministic arithmetic and configured destination."
//
// EBAY ONLY, today. It travels as contextualLocation and genuinely changes the
// delivery costs eBay returns. Amazon's Product Pricing API takes a marketplace
// id and has no address parameter at all, so its adapter drops this — which is
// a limit of the API, not a gap to be filled later.
//
// Remembered rather than retyped. It is a property of the person searching —
// their warehouse, their customer base — not of any one search, and a field
// that has to be re-entered every time gets left empty.
const KEY = 'searchDestination'

export const EMPTY_DESTINATION = { country: '', postalCode: '' }

function read() {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return EMPTY_DESTINATION
    const v = JSON.parse(raw)
    return {
      country: typeof v?.country === 'string' ? v.country : '',
      postalCode: typeof v?.postalCode === 'string' ? v.postalCode : '',
    }
  } catch {
    // A corrupt or unavailable store is not a reason to block searching.
    return EMPTY_DESTINATION
  }
}

export function useDestination() {
  const [destination, setDestinationState] = useState(read)

  const setDestination = useCallback((next) => {
    setDestinationState((prev) => {
      const merged = { ...prev, ...next }
      // A postcode without a country is meaningless — 10001 is Manhattan in the
      // US and a Copenhagen suburb in Denmark — so clearing the country clears
      // it rather than leaving a value that would be silently ignored.
      if (!merged.country) merged.postalCode = ''
      try { localStorage.setItem(KEY, JSON.stringify(merged)) } catch { /* not fatal */ }
      return merged
    })
  }, [])

  return { destination, setDestination }
}

// The request body shape, or undefined.
//
// Omitted entirely when empty — sending {"country":"","postalCode":""} is not
// "no destination", it is two invalid values, and `country` must be a
// two-letter ISO code or the backend rejects the whole search with a 400.
export function destinationPayload(d) {
  const country = (d?.country || '').trim().toUpperCase()
  if (country.length !== 2) return undefined
  const postalCode = (d?.postalCode || '').trim()
  return postalCode ? { country, postalCode } : { country }
}
