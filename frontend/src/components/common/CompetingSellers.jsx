import { useQuery } from '@tanstack/react-query'
import { FiUsers, FiAward, FiTruck, FiStar } from 'react-icons/fi'
import Card from './Card'
import Loader from './Loader'
import EmptyState from './EmptyState'
import { marketplaceService } from '../../services/marketplaceService'
import { ItemIdentifiers } from './ListingCells'
import { shortItemId, marketplaceErrorText } from '../../utils/marketplaces'
import { formatCurrency } from '../../utils/format'

// Who else is selling THIS listing.
//
// The distinction this panel exists to make: an ASIN is one product, so a
// search by ASIN returns one result and always will. The seven or eight
// "competitors" visible on an Amazon page are seven or eight MERCHANTS on that
// same product, which is a different question and a different endpoint.
//
// FR-PRICE-004 wants our price compared to "lowest, median, average, highest,
// featured offer/buy box where available" — none of which is computable from a
// single catalogue record. This is where those numbers come from.

const landed = (o) => (o.price == null ? null : o.price + (o.shipping || 0))

function stats(offers) {
  const values = offers.map(landed).filter((v) => v != null).sort((a, b) => a - b)
  if (!values.length) return null
  const mid = Math.floor(values.length / 2)
  return {
    lowest: values[0],
    highest: values[values.length - 1],
    median: values.length % 2 ? values[mid] : (values[mid - 1] + values[mid]) / 2,
    count: values.length,
  }
}

// `region` is the storefront to read offers from, `storefront` its name for the
// subtitle. Who holds the Buy Box on amazon.com says nothing about who holds it
// on amazon.ca, so the two must never be mixed in one table.
export default function CompetingSellers({ mp, itemId, identifiers, currency = 'USD', storefront, region }) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['mp-offers', mp, itemId, region],
    queryFn: () => marketplaceService.listingOffers(mp, itemId, { region }),
    enabled: !!mp && !!itemId,
  })

  const offers = data?.offers || []
  // Buy Box first, then cheapest landed — the order someone actually reads it
  // in: who wins today, and who is closest to taking it.
  const sorted = [...offers].sort((a, b) => {
    if (a.buyBox !== b.buyBox) return a.buyBox ? -1 : 1
    return (landed(a) ?? Infinity) - (landed(b) ?? Infinity)
  })
  const s = stats(offers)

  // Only Amazon shares one listing between sellers, so it is the only place
  // this panel can have an answer. eBay returns a payload we cannot parse into
  // offers because there are none to parse — that is the marketplace's design,
  // not a shape problem, and the diagnostic below must not accuse it of one.
  const isAmazon = String(mp).toUpperCase() === 'AMAZON'

  // Something came back that we could not READ — not something readable that
  // happened to be empty. Those are different answers and only the first is a
  // problem worth putting a diagnostic in front of the user for.
  const unrecognised = isAmazon && data?.recognised === false && data?.raw != null

  // A card that says "this does not apply here" on every eBay listing forever
  // is permanent noise. It appears when there is something to show, and stays
  // out of the way when there is not.
  if (!isAmazon && offers.length === 0 && !unrecognised) return null

  return (
    <Card className="mt-3" title="Competing sellers"
      subtitle={storefront
        ? `Other merchants on ${storefront} offering this same listing — not other products`
        : 'Other merchants offering this same listing — not other products'}
      actions={<FiUsers className="text-muted" />}>

      {/* Once, not per row. Every offer below is on this same catalogue item, so
          an ASIN column would repeat one value ten times and imply the rows
          differ by product — which is the exact misreading this panel exists to
          prevent. What varies is the seller and the price. */}
      <div className="text-muted small mb-3 pb-3 border-bottom-soft">
        All offers below are for{' '}
        <span className="font-monospace text-body" title={itemId}>{shortItemId(itemId)}</span>
        <ItemIdentifiers identifiers={identifiers} />
      </div>

      {isLoading ? <Loader label="Loading offers…" />
        : isError ? (
          <div className="text-danger small">
            {marketplaceErrorText(error, storefront) || 'Offers could not be loaded for this listing.'}
          </div>
        ) : unrecognised ? (
          <details>
            <summary className="small fw-semibold" style={{ cursor: 'pointer' }}>
              Offers came back in an unexpected shape — show what was returned
            </summary>
            <pre className="small text-muted mt-2 mb-0"
              style={{ whiteSpace: 'pre-wrap', maxHeight: 260, overflow: 'auto' }}>
              {JSON.stringify(data.raw, null, 2)}
            </pre>
          </details>
        ) : offers.length === 0 ? (
          <EmptyState icon={FiUsers} title="No competing offers"
            message="No other sellers are offering this listing right now. An item with no featured
                     offer — out of stock, or sold only by one merchant — reports nothing here." />
        ) : (
          <>
            {s && (
              <div className="d-flex flex-wrap gap-4 mb-3 small">
                <span>Lowest <strong>{formatCurrency(s.lowest, currency)}</strong></span>
                <span>Median <strong>{formatCurrency(s.median, currency)}</strong></span>
                <span>Highest <strong>{formatCurrency(s.highest, currency)}</strong></span>
                <span className="text-muted">across {s.count} priced offer{s.count === 1 ? '' : 's'}</span>
              </div>
            )}

            <div className="table-responsive">
              <table className="table table-sm align-middle mb-0">
                <thead>
                  <tr>
                    <th>Seller</th>
                    <th className="text-nowrap">Price</th>
                    <th className="text-nowrap">Delivered</th>
                    <th>Condition</th>
                    <th>Feedback</th>
                  </tr>
                </thead>
                <tbody>
                  {sorted.map((o, i) => (
                    <tr key={o.sellerId || i}>
                      <td>
                        <div className="d-flex align-items-center flex-wrap gap-2">
                          <span className="fw-semibold">
                            {o.sellerName || o.sellerId || 'Unnamed seller'}
                          </span>
                          {/* The Buy Box holder is the price a shopper is shown,
                              so it is the one offer that sets the market. */}
                          {o.buyBox && (
                            <span className="mr-pill mr-pill--green d-inline-flex align-items-center gap-1">
                              <FiAward size={11} /> Buy Box
                            </span>
                          )}
                          {o.fba && (
                            <span className="mr-pill mr-pill--slate d-inline-flex align-items-center gap-1"
                              title="Fulfilled by Amazon">
                              <FiTruck size={11} /> FBA
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="text-nowrap">
                        {o.price != null ? formatCurrency(o.price, o.currency || currency) : '—'}
                        {o.shipping > 0 && (
                          <div className="text-muted" style={{ fontSize: 11 }}>
                            + {formatCurrency(o.shipping, o.currency || currency)} delivery
                          </div>
                        )}
                      </td>
                      <td className="text-nowrap fw-semibold">
                        {landed(o) != null ? formatCurrency(landed(o), o.currency || currency) : '—'}
                      </td>
                      <td className="text-nowrap">{o.condition || 'not stated'}</td>
                      <td className="text-nowrap">
                        {o.feedbackRating != null ? (
                          <span className="d-inline-flex align-items-center gap-1">
                            <FiStar size={12} className="text-warning" />
                            {o.feedbackRating}%
                            {o.feedbackCount != null && (
                              <span className="text-muted">({o.feedbackCount})</span>
                            )}
                          </span>
                        ) : <span className="text-muted">—</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Amazon's offers API returns a merchant ID and no name, so these
                are IDs by design rather than a field that failed to load. */}
            {sorted.some((o) => !o.sellerName) && (
              <div className="text-muted mt-2" style={{ fontSize: 11 }}>
                Amazon identifies sellers by ID, not by name.
              </div>
            )}
          </>
        )}
    </Card>
  )
}
