import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FiUsers, FiExternalLink, FiTag, FiInfo } from 'react-icons/fi'
import Card from './Card'
import Loader from './Loader'
import EmptyState from './EmptyState'
import { marketplaceService } from '../../services/marketplaceService'
import { storefrontLabel, marketplaceErrorText } from '../../utils/marketplaces'
import { formatCurrency } from '../../utils/format'

// Who else sells this product on eBay.
//
// Amazon and eBay answer "who am I competing with?" in different shapes, so
// they get different panels. Amazon has one listing and many sellers fighting
// for the Buy Box; eBay has one listing PER seller and no winner at all. So an
// eBay listing's competitors are the other listings of the same product, found
// by its barcode or eBay product id — never by title, which would return
// similar products rather than this one.
//
// There is no Buy Box row to highlight here. The nearest equivalent is the
// cheapest landed price, and that is per CONDITION: a used unit at $40 does not
// undercut a new one at $60, and marking it "cheapest" would invite a price cut
// that loses money against a competitor who is not one.

// Feedback is the only signal of whether a cheaper seller is a real threat. A
// 99% seller undercutting you matters; a 70% seller with nine ratings does not.
const feedbackTone = (pct) => {
  if (pct == null) return 'text-muted'
  if (pct >= 98) return 'text-success'
  if (pct >= 95) return 'text-warning'
  return 'text-danger'
}

// What the listing was matched on, said plainly. GTIN is a barcode; EPID is
// eBay's own product id, used when a listing carries no barcode.
const MATCHED_BY = { GTIN: 'barcode', EPID: 'eBay product id' }

function Shipping({ row, currency }) {
  if (!row.shippingKnown) return <span className="text-muted">—</span>
  if (!row.shipping) return <span className="text-success">Free</span>
  return formatCurrency(row.shipping, row.currency || currency)
}

export default function EbayOtherSellers({ itemId, region, destination, currency = 'USD' }) {
  const [condition, setCondition] = useState('')

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['ebay-other-sellers', itemId, region, destination?.country, destination?.postalCode],
    queryFn: () => marketplaceService.otherSellers('EBAY', itemId, {
      region,
      // Sent always: without a destination many sellers quote no delivery at
      // all, and a row with unknown shipping has no comparable total. Falls
      // back to the storefront's own country, which is the common case anyway.
      deliveryCountry: destination?.country || region || undefined,
      deliveryPostalCode: destination?.postalCode || undefined,
    }),
    enabled: !!itemId,
  })

  const sellers = useMemo(() => data?.sellers ?? [], [data])
  const conditions = useMemo(
    () => [...new Set(sellers.map((s) => s.condition).filter(Boolean))],
    [sellers],
  )
  // Filtered here, not refetched: the whole set is already loaded and a second
  // call would cost two more eBay requests to show fewer rows.
  const rows = condition ? sellers.filter((s) => s.condition === condition) : sellers
  const mine = sellers.find((s) => s.thisListing)
  const cheapest = data?.cheapestLandedPrice
  const place = storefrontLabel({ countryCode: data?.storefront || region })
  const money = (v) => formatCurrency(v, data?.currency || currency)

  const subtitle = data?.matchedBy
    ? `Matched by ${MATCHED_BY[data.matchedBy] || data.matchedBy}${
      data.matchedValue ? ` ${data.matchedValue}` : ''} · ${data.listingsFound ?? sellers.length} listings found`
    : 'Every seller with their own listing of this product — eBay has no shared product page'

  return (
    <Card className="mt-3" title="Other sellers of this product" subtitle={subtitle}
      actions={<FiUsers className="text-muted" />}>

      {isLoading ? <Loader label={`Comparing sellers on eBay${place ? ` ${place}` : ''}…`} />
        : isError ? (
          <div className="text-danger small">
            {marketplaceErrorText(error, place ? `eBay ${place}` : 'eBay')}
          </div>
        ) : (
          <>
            {/* The one number worth carrying away: the cheapest anyone sells it
                for in the same condition, next to what this listing charges. */}
            {cheapest != null && (
              <div className="d-flex flex-wrap align-items-baseline gap-3 mb-3">
                <span>
                  Cheapest{mine?.condition ? ` ${mine.condition.toLowerCase()}` : ''} on eBay
                  {place ? ` ${place}` : ''}: <strong>{money(cheapest)}</strong>
                </span>
                {mine?.landedPrice != null && (
                  <span className="text-muted">
                    this listing: <strong className={mine.landedPrice > cheapest ? 'text-danger' : 'text-success'}>
                      {money(mine.landedPrice)}
                    </strong>
                  </span>
                )}
              </div>
            )}

            {/* Only when there IS something to compare. With no barcode the
                empty state below carries the same sentence, and printing it
                twice made one normal situation look like two faults. */}
            {data?.note && data?.matchedBy && (
              <div className="field-note mb-3" style={{ maxWidth: 'none' }}>
                <FiInfo size={15} />
                <span>{data.note}</span>
              </div>
            )}

            {sellers.length <= 1 ? (
              // Two quite different answers, and only one of them is about the
              // market. "Nobody else sells it" is a finding; "this listing
              // carries no barcode" is a limit of what can be asked, and
              // reporting the second as the first sends someone looking for a
              // fault that is not there.
              !data?.matchedBy ? (
                // Never "it has no id" — it plainly has one on screen. The item
                // number identifies THIS SELLER'S listing and nothing else:
                // every seller who lists the same product gets a different one,
                // so it cannot find them. Only a shared identifier can — a
                // barcode, or eBay's catalogue product id.
                <EmptyState icon={FiTag} title="Nothing shared to match other sellers on"
                  message={`This listing has an item number — ${itemId} — but that is its own
                           listing id, and every other seller of the same product has a different
                           one. Finding them needs an identifier they all share: a barcode (UPC,
                           EAN or GTIN) or eBay's catalogue product id (EPID). This seller gave
                           neither, which is common on unbranded goods.`} />
              ) : (
                <EmptyState icon={FiUsers} title={`No other sellers found on eBay${place ? ` ${place}` : ''}`}
                  message="Nobody else is listing this product right now, so there is nothing to
                           compare against. Auctions are left out — a current bid is not a price
                           anyone can buy at." />
              )
            ) : (
              <>
                {conditions.length > 1 && (
                  <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
                    <span className="text-muted small">Condition</span>
                    <select className="form-select form-select-sm w-auto" value={condition}
                      onChange={(e) => setCondition(e.target.value)}>
                      <option value="">All ({sellers.length})</option>
                      {conditions.map((c) => (
                        <option key={c} value={c}>
                          {c} ({sellers.filter((s) => s.condition === c).length})
                        </option>
                      ))}
                    </select>
                  </div>
                )}

                <div className="table-responsive">
                  <table className="table table-sm table-hover align-middle mb-0">
                    <thead>
                      <tr>
                        <th>Seller</th>
                        <th className="text-nowrap">Feedback</th>
                        <th>Condition</th>
                        <th className="text-nowrap">Price</th>
                        <th className="text-nowrap">Shipping</th>
                        <th className="text-nowrap">Total</th>
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {/* Sorted by the backend, cheapest landed first. Re-sorting
                          here would fight rows it deliberately ranks below the
                          rest, such as those with no quoted shipping. */}
                      {rows.map((r) => (
                        <tr key={r.itemId}
                          style={r.thisListing ? { background: 'var(--hover-soft)' } : undefined}>
                          <td>
                            <div className="d-flex align-items-center flex-wrap gap-2">
                              <span className="fw-semibold">{r.seller || 'Unnamed seller'}</span>
                              {r.thisListing && (
                                <span className="mr-pill mr-pill--blue">Your listing</span>
                              )}
                              {/* Per CONDITION, so two badges on one table is
                                  correct and not a bug. */}
                              {r.cheapestInCondition && (
                                <span className="mr-pill mr-pill--green d-inline-flex align-items-center gap-1">
                                  <FiTag size={11} /> Cheapest {String(r.condition || '').toLowerCase() || 'in condition'}
                                </span>
                              )}
                            </div>
                            {/* The same seller legitimately appears more than
                                once when they list in several conditions, so
                                rows are never merged by seller. */}
                            {r.listingsFromSeller > 1 && (
                              <div className="text-muted" style={{ fontSize: 11 }}>
                                +{r.listingsFromSeller - 1} more listing{r.listingsFromSeller - 1 === 1 ? '' : 's'} from this seller
                              </div>
                            )}
                          </td>
                          <td className="text-nowrap">
                            {/* Null is a seller with no ratings yet — never 0%,
                                which would read as a terrible seller rather
                                than an unrated one. */}
                            {r.feedbackPercent == null ? (
                              <span className="text-muted small">New seller</span>
                            ) : (
                              <span className={feedbackTone(r.feedbackPercent)}>
                                {r.feedbackPercent.toFixed(1)}%
                                {r.feedbackScore != null && (
                                  <span className="text-muted small"> ({r.feedbackScore.toLocaleString()})</span>
                                )}
                              </span>
                            )}
                          </td>
                          <td className="text-nowrap">{r.condition || '—'}</td>
                          <td className="text-nowrap">
                            {r.price != null ? formatCurrency(r.price, r.currency || currency) : '—'}
                          </td>
                          <td className="text-nowrap"><Shipping row={r} currency={currency} /></td>
                          <td className="text-nowrap fw-semibold">
                            {/* An unknown delivery cost makes the total unknown.
                                Printing the item price as the total would
                                understate it against rows that include postage. */}
                            {r.landedPrice != null
                              ? formatCurrency(r.landedPrice, r.currency || currency)
                              : <span className="text-muted fw-normal small">shipping unknown</span>}
                          </td>
                          <td className="text-end">
                            {r.url && (
                              <a className="icon-btn" href={r.url} target="_blank" rel="noreferrer"
                                title="Open this listing on eBay">
                                <FiExternalLink />
                              </a>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

                {data?.shippingUnknown > 0 && (
                  <div className="text-muted small mt-2">
                    {data.shippingUnknown} seller{data.shippingUnknown === 1 ? '' : 's'} quoted no
                    delivery cost{destination?.country ? '' : ' — setting a delivery country makes more of them quote one'}.
                    Those rows have no comparable total and are never marked cheapest.
                  </div>
                )}
              </>
            )}
          </>
        )}
    </Card>
  )
}
