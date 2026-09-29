import { useQuery } from '@tanstack/react-query'
import { FiClock } from 'react-icons/fi'
import Card from '../../components/common/Card'
import { SkeletonRows } from '../../components/common/Skeleton'
import { productIntelService } from '../../services/productIntelService'
import { money } from './shared'
import { formatDate } from '../../utils/format'

// Every cost version, newest effective date first.
//
// A product used to have exactly one cost profile, so editing it overwrote the
// old costs — last quarter's margin was silently recomputed with this quarter's
// freight, and a price approved against a $4.10 landed cost could not be
// explained afterwards because the $4.10 no longer existed anywhere.
//
// This panel is the answer to the question a moved margin always raises: when
// did this change, and what was it before?
export default function CostHistory({ productId }) {
  const { data = [], isLoading } = useQuery({
    queryKey: ['costProfileHistory', productId],
    queryFn: () => productIntelService.costProfileHistory(productId),
    enabled: !!productId,
  })

  // One version is the normal state of a product whose costs have never been
  // corrected — a table of one row is noise, so it stays out of the way.
  if (!isLoading && data.length < 2) return null

  // Newest effective date first — but a future-dated entry sits at the top and
  // is NOT the version in force. "Current" means the newest one that has
  // actually started, which is the only row today's figures came from.
  const today = new Date().toISOString().slice(0, 10)
  const currentIdx = data.findIndex((v) => !v.validFrom || v.validFrom.slice(0, 10) <= today)

  return (
    <Card className="mt-3" title="Cost history"
      subtitle="Earlier versions stay intact, so a past margin can still be explained"
      actions={<FiClock className="text-muted" />}>
      {isLoading ? <SkeletonRows rows={3} /> : (
        <div className="table-responsive">
          <table className="table table-sm align-middle mb-0">
            <thead>
              <tr>
                <th>Effective from</th>
                <th className="text-nowrap">COGS</th>
                <th>Note</th>
                <th>Updated by</th>
              </tr>
            </thead>
            <tbody>
              {data.map((v, i) => (
                <tr key={v.validFrom || i}>
                  <td className="text-nowrap fw-semibold">
                    {v.validFrom ? formatDate(v.validFrom) : '—'}
                    {/* The version in force today, so a future-dated entry
                        above it is not mistaken for the current one. */}
                    {i === currentIdx && <span className="mr-pill mr-pill--slate ms-2">current</span>}
                    {i < currentIdx && (
                      <span className="mr-pill mr-pill--blue ms-2" title="Starts on this date">
                        scheduled
                      </span>
                    )}
                  </td>
                  <td className="text-nowrap">{money(v.cogs, v.currency)}</td>
                  <td className="text-muted small">{v.note || '—'}</td>
                  <td className="text-muted small text-nowrap">{v.updatedBy || v.createdBy || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  )
}
