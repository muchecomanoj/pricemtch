import Card from '../../components/common/Card'
import StatusBadge from '../../components/common/StatusBadge'
import { money } from './shared'

// GET /products/{id}/sales
// FR-SALES-002: owned and estimated figures must never be blended, and each
// carries its own classification badge.
export default function SalesTab({ sales, loading }) {
  if (loading) {
    return <Card><div className="text-center py-4"><span className="spinner-border spinner-border-sm text-primary" /></div></Card>
  }
  const s = sales || {}
  const ccy = s.currency || 'USD'
  // UNAVAILABLE is the backend's answer whenever no marketplace reported a
  // number, and estimatedNote carries the reason. A zero that arrives WITH a
  // classification of ESTIMATED is a real figure from a counted source, so it
  // is printed as sent — this page never second-guesses the classification.
  const unavailable = (c) => !c || c === 'UNAVAILABLE'

  return (
    <div className="row g-3">
      <div className="col-12 col-lg-6">
        <Card className="h-100" title="Owned account"
          actions={<StatusBadge status={s.ownedClassification || 'UNAVAILABLE'} />}>
          {unavailable(s.ownedClassification) ? (
            <p className="text-muted small mb-0">
              {s.ownedNote || 'Connect an authorised seller account to see real order data. Nothing is estimated here.'}
            </p>
          ) : (
            <div className="row g-3">
              <Metric label="Units sold" value={s.ownedUnits ?? '—'} />
              <Metric label="Revenue" value={money(s.ownedRevenue, ccy)} />
            </div>
          )}
        </Card>
      </div>

      <div className="col-12 col-lg-6">
        {/* h-100 on both: the unavailable note runs to a couple of hundred
            characters, and an uneven pair of cards reads as a layout fault. */}
        <Card className="h-100" title="Estimated (competitors)"
          actions={<StatusBadge status={s.estimatedClassification || 'UNAVAILABLE'} />}>
          {unavailable(s.estimatedClassification) ? (
            <p className="text-muted small mb-0">
              {s.estimatedNote || 'Estimates appear once competitor listings have been matched.'}
            </p>
          ) : (
            <>
              <div className="row g-3">
                <Metric label="Est. units" value={s.estimatedUnits ?? '—'} />
                <Metric label="Est. revenue" value={money(s.estimatedRevenue, ccy)} />
              </div>
              <p className="text-muted small mb-0 mt-3">
                Estimated from {s.estimateSources ?? 0} matched source{s.estimateSources === 1 ? '' : 's'}.
                These are estimates, not actual marketplace sales.
              </p>
            </>
          )}
        </Card>
      </div>
    </div>
  )
}

function Metric({ label, value }) {
  return (
    <div className="col-6">
      <div className="text-muted small">{label}</div>
      <div className="h4 fw-bold mb-0">{value}</div>
    </div>
  )
}
