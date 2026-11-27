import { FiAlertTriangle, FiGlobe, FiRotateCcw, FiTag } from 'react-icons/fi'
import Button from '../../components/common/Button'
import { storefrontLabel } from '../../utils/marketplaces'
import { useRetryCooldown } from '../../hooks/useRetryCooldown'
import { useEffect } from 'react'

// A search that never happened: the marketplace could not be reached, so the
// result says nothing about whether the product exists. The backend marks it
// `status: 'PROVIDER_ERROR'` and writes a `message` to be shown as-is — it ends
// "Nothing here says the product is absent", which is the point of showing it.
// It must not fall back to "no results": that tells the user the product is
// missing, and they go and "fix" an identifier that was right.
//
// A real miss (the reader looked and the ASIN is not on that storefront) is
// NO_LISTINGS and stays the ordinary empty result.
export const isProviderError = (result) => result?.status === 'PROVIDER_ERROR'

// What was searched, so a pause after a failed retry belongs to this search
// and not to every failed one on the page.
const searchKey = (result) =>
  ['retry', ...(result?.steps || []).map((s) => `${s.marketplace}:${s.query}`)].join('|')

// `retryable` is the cue for Try again; a genuine empty result never has it.
// One manual button, never a loop. A block by Amazon usually answers a retry
// the same way, so after a retry fails again the button waits a minute.
export default function ProviderError({ result, onRetry, busy = false, retryKey }) {
  const { secondsLeft, markRetried, settle } = useRetryCooldown(retryKey || searchKey(result))
  useEffect(() => { if (!busy) settle(isProviderError(result)) }, [result, busy, settle])

  return (
    <div className="field-note field-note--warn align-items-start" style={{ maxWidth: 'none' }}>
      <FiAlertTriangle size={15} className="flex-shrink-0 mt-1" />
      <div className="flex-grow-1">
        <div className="fw-semibold">Couldn&apos;t reach the marketplace</div>
        <div>{result?.message || 'The marketplace could not be reached, so this product was not searched.'}</div>
      </div>
      {result?.retryable && onRetry && (
        <Button write variant="light" size="sm" icon={FiRotateCcw} loading={busy} className="flex-shrink-0"
          disabled={secondsLeft > 0}
          title={secondsLeft > 0 ? 'The last retry was refused too. Give it a minute before trying again.' : undefined}
          onClick={() => { markRetried(); onRetry() }}>
          {secondsLeft > 0 ? `Try again in ${secondsLeft}s` : 'Try again'}
        </Button>
      )}
    </div>
  )
}

// Found, but nothing buyable: the marketplace shows the product with no price
// (out of stock, or no seller serving the page). A real answer about a correct
// identifier, so not "found nothing" and not a failure. `retryable` is false —
// a retry answers identically — so there is no Try again.
export const isUnpriced = (result) => result?.status === 'UNPRICED'

export function Unpriced({ result }) {
  return (
    <div className="field-note align-items-start" style={{ maxWidth: 'none' }}>
      <FiTag size={15} className="flex-shrink-0 mt-1" />
      <div className="flex-grow-1">
        <div className="fw-semibold">Listed, but not priced</div>
        <div>{result?.message || 'The marketplace lists this product, but nobody is selling it right now, so there is nothing to price against.'}</div>
      </div>
    </div>
  )
}

// The reader treats a storefront as a starting point and can fall through to a
// neighbour (asked for GB, answered by DE). The result carries both sides, so
// this is a field comparison, not a parse of the note. Per-item flags and
// currency already come from the item itself; this says the whole answer is
// from somewhere else.
export function ServedElsewhere({ result }) {
  const asked = result?.requestedCountryCode
  const got = result?.countryCode
  if (!asked || !got || asked.toUpperCase() === got.toUpperCase()) return null
  const name = (cc) => storefrontLabel({ countryCode: cc }) || cc
  return (
    <div className="field-note field-note--warn mb-3" style={{ maxWidth: 'none' }}>
      <FiGlobe size={15} className="flex-shrink-0" />
      <span>
        Asked for {name(asked)}, answered by <strong>{name(got)}</strong>.
        {' '}These prices are not {name(asked)} prices.
      </span>
    </div>
  )
}

// Priced results came back, and some others were left out for having no price.
export function UnpricedCount({ result }) {
  const n = result?.unpricedCount
  if (!n || isUnpriced(result)) return null
  return (
    <div className="text-muted small mb-2">
      {n} other listing{n === 1 ? '' : 's'} had no price and {n === 1 ? 'is' : 'are'} not shown.
    </div>
  )
}
