import { Link } from 'react-router-dom'
import { FiAlertCircle, FiArrowRight, FiExternalLink, FiPackage, FiRefreshCw, FiRotateCcw } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import { ItemIdentifiers, EstimatedBadge } from '../../components/common/ListingCells'
import MarketplaceLogo from '../../components/common/MarketplaceLogo'
import { listingPath } from '../../utils/marketplaces'
import { formatCurrency } from '../../utils/format'
import JudgedListings from './JudgedListings'
import { ChannelTrace, fmtTime } from './SearchTrace'

// The raw item grid, shown when judging did not run.
function RawListings({ channel, hitMarket, onAdd }) {
  return (
    <>
      <div className="text-muted small mb-2">
        {channel.totalResults} result{channel.totalResults === 1 ? '' : 's'}
        {channel.resolvedQuery && <> for <strong>{channel.resolvedQuery}</strong></>}
      </div>
      <div className="row g-2">
        {channel.items.map((item, i) => {
          return (
            <div className="col-12 col-lg-6" key={item.marketplaceItemId || i}>
              <div className="result-card">
                <div className="flex-grow-1" style={{ minWidth: 0 }}>
                  <MarketplaceLogo marketplace={item.marketplace || hitMarket}
                    storefront={item.storefront} />
                  <div className="fw-semibold clamp-2 mt-2">{item.title || 'Untitled listing'}</div>
                  <div className="text-muted small mt-1 d-flex flex-wrap gap-2">
                    {item.marketplaceItemId && <span className="font-monospace">{item.marketplaceItemId}</span>}
                    {item.seller && <span>· {item.seller}</span>}
                    <span>· {item.condition || 'condition not stated'}</span>
                  </div>
                  <ItemIdentifiers identifiers={item.identifiers} />
                  <div className="mt-1">
                    {item.price != null
                      ? (
                        <>
                          <strong>{formatCurrency(item.price, item.currency || 'USD')}</strong>
                          <EstimatedBadge estimated={item.priceEstimated} note={item.priceNote} />
                        </>
                      )
                      : (
                        <span className="text-muted small"
                          title="Amazon prices by the visitor's location, so this storefront quotes an import price rather than its shelf price. A converted figure would be wrong by shipping and duty.">
                          Price unavailable from this region
                        </span>
                      )}
                    {item.shipping > 0 && (
                      <span className="text-muted small ms-2">
                        + {formatCurrency(item.shipping, item.currency || 'USD')} shipping
                      </span>
                    )}
                  </div>
                  <div className="d-flex flex-wrap gap-1 mt-2">
                    {/* Unjudged results are still real listings, and one
                        of them is often the product being looked for —
                        the verdicts being absent says the AI pass did
                        not run, not that these are worthless. */}
                    {item.marketplaceItemId && (
                      <Button write variant="light" icon={FiPackage}
                        title="Create this as one of your own products"
                        onClick={() => onAdd({
                          ...item, marketplace: item.marketplace || hitMarket,
                        })}>
                        Add to catalog
                      </Button>
                    )}
                    {hitMarket && item.marketplaceItemId && (
                      <Link className="btn btn-sm btn-light d-inline-flex align-items-center gap-1"
                        to={listingPath(hitMarket, item.marketplaceItemId, item.storefront)}>
                        Details <FiArrowRight />
                      </Link>
                    )}
                    {item.url && (
                      <a href={item.url} target="_blank" rel="noreferrer"
                        className="btn btn-sm btn-light d-inline-flex align-items-center gap-1">
                        Open on marketplace <FiExternalLink size={13} />
                      </a>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </>
  )
}

// ── The marketplaces ──
// The channel half of a search: notes, verdicts or the raw grid, and the trace.
// `onFindSimilar` is left undefined on a title search.
export default function ChannelCard({
  channel, judged, hitMarket, busy, refreshing, onRefresh, onRetry, onFindSimilar, similarBusy, onAdd,
}) {
  return (
    <Card className="mt-3" title="On the marketplaces"
      // The time these prices were read, in the header rather than buried
      // in the trace. Marketplace prices move by the hour, so "ours says
      // $2,539.99, Amazon says $2,554.45" is unanswerable without knowing
      // when ours was taken — and the first assumption is always a bug.
      subtitle={`${judged
        ? 'Every listing here was found by a marketplace. The verdicts on them are AI.'
        : 'Live listings — what competitors are selling and charging.'}${
        channel.finishedAt ? ` Prices as at ${fmtTime(channel.finishedAt)}.` : ''}`}
      actions={(
        <Button write variant="light" icon={FiRefreshCw} loading={refreshing} disabled={busy}
          title="Re-read the marketplaces, ignoring anything cached. Uses API quota."
          onClick={onRefresh}>
          Refresh
        </Button>
      )}>
      {/* Shown whatever came back. It is the sentence that explains an
          answer that looks wrong but is not: a storefront that answered in
          place of the one asked for, a parent product instead of the
          variant, or a price left out because the reader is quoted an
          import price rather than the shelf price. */}
      {channel?.note && (
        <div className="field-note mb-3" style={{ maxWidth: 'none' }}>
          <FiAlertCircle size={15} />
          <span>{channel.note}</span>
        </div>
      )}

      {judged && (
        <JudgedListings result={channel} onFindSimilar={onFindSimilar} similarBusy={similarBusy} />
      )}

      {/* Only when judging did NOT run — with verdicts on screen, a blanket
          "these are lookalikes" banner says less than the per-listing
          conflicts and confidence do, and contradicts a confirmed match. */}
      {/* NOT_JUDGED. `message` explains why nothing was checked; `retryable`
          separates a provider that fell over from one that was never
          configured — retrying the latter forever was the old behaviour. */}
      {!judged && channel?.message && (
        <div className={`field-note ${channel.retryable ? 'field-note--warn' : ''} mb-3 align-items-center`}
          style={{ maxWidth: 'none' }}>
          <FiAlertCircle size={15} />
          <span className="flex-grow-1">{channel.message}</span>
          {channel.retryable && (
            <Button write variant="light" icon={FiRotateCcw} loading={busy} className="flex-shrink-0"
              onClick={onRetry}>Try again</Button>
          )}
        </div>
      )}

      {!judged && channel?.items?.length > 0 && !channel.resolvedIdentifierType && (
        <div className="field-note field-note--warn mb-3" style={{ maxWidth: 'none' }}>
          <FiAlertCircle size={15} />
          <span>
            <strong>Matched on title, not an identifier.</strong>{' '}
            These are lookalikes — confirm each before treating it as the same product.
          </span>
        </div>
      )}

      {!judged && channel?.items?.length > 0 ? (
        <RawListings channel={channel} hitMarket={hitMarket} onAdd={onAdd} />
      ) : !judged && !channel?.message && (
        // `note` explains an empty result in a sentence — but on an empty
        // search the backend now puts that same sentence in `message`, and
        // both branches rendering it printed the identical box twice.
        // `message` wins because it is the one that can carry `retryable`.
        <div className="field-note" style={{ maxWidth: 'none' }}>
          <FiAlertCircle size={15} />
          <span>{channel?.note || 'No listings were found in the selected marketplaces.'}</span>
        </div>
      )}

      {channel?.steps?.length > 0 && <ChannelTrace channel={channel} />}
    </Card>
  )
}
