import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { FiInfo, FiAlertTriangle } from 'react-icons/fi'
import Modal from '../../components/common/Modal'
import Button from '../../components/common/Button'
import ProductSelector from '../../components/common/ProductSelector'
import StatusBadge from '../../components/common/StatusBadge'
import { marketplaceService } from '../../services/marketplaceService'
import { mpLabel, marketplaceCodes, healthFor, healthBadge, isUsable } from '../../utils/marketplaces'
import { INTERVALS, intervalLabel } from '../../services/monitorService'

const DEFAULT_MAX = 5
const DEFAULT_INTERVAL = 1440

// Create or edit one monitor. `monitor` null => create.
//
// `lockProduct` is used from the product page, where the product is already
// decided and offering a picker would only invite picking the wrong one.
export default function MonitorModal({
  show, onClose, onSave, saving, monitor, lockProduct, productId, minIntervalMinutes,
}) {
  const editing = Boolean(monitor?.id)

  const [product, setProduct] = useState(productId ?? null)
  const [markets, setMarkets] = useState([])
  const [maxResults, setMaxResults] = useState(DEFAULT_MAX)
  const [interval, setIntervalMins] = useState(DEFAULT_INTERVAL)

  // Reset each time the modal opens so a cancelled edit can't leak into the next.
  useEffect(() => {
    if (!show) return
    setProduct(monitor?.productId ?? productId ?? null)
    setMarkets(monitor?.markets ?? [])
    setMaxResults(monitor?.maxResults ?? DEFAULT_MAX)
    setIntervalMins(monitor?.intervalMinutes ?? DEFAULT_INTERVAL)
  }, [show, monitor, productId])

  // Channels come from the API, never a hardcoded list — which marketplaces
  // exist is a backend concern and has changed before.
  const mpQ = useQuery({ queryKey: ['marketplaces'], queryFn: marketplaceService.list, enabled: show })
  const healthQ = useQuery({ queryKey: ['marketplaces', 'health'], queryFn: marketplaceService.health, enabled: show })
  const codes = marketplaceCodes(mpQ.data)

  // The floor comes from the monitor being edited, or from any existing monitor
  // (it is a plan property, so identical across a tenant). With no monitors yet
  // it is unknown — every option stays enabled and the backend's 400 explains.
  const floor = monitor?.minIntervalMinutes ?? minIntervalMinutes ?? null

  const toggleMarket = (code) => setMarkets((m) =>
    m.includes(code) ? m.filter((c) => c !== code) : [...m, code])

  const submit = () => {
    onSave({
      productId: product,
      markets,
      maxResults: Math.min(50, Math.max(1, Number(maxResults) || DEFAULT_MAX)),
      intervalMinutes: Number(interval),
    })
  }

  const belowFloor = floor != null && Number(interval) < floor

  return (
    <Modal
      show={show}
      onClose={onClose}
      size="lg"
      title={editing ? 'Edit monitor' : 'Monitor this product'}
      footer={(
        <>
          <Button variant="light" onClick={onClose}>Cancel</Button>
          <Button onClick={submit} loading={saving} disabled={!product || belowFloor}>
            {editing ? 'Save changes' : 'Start monitoring'}
          </Button>
        </>
      )}
    >
      <p className="text-muted small mb-4">
        A monitor re-checks this product on a schedule, so competitor prices, trends and
        recommendations stay current without anyone running a search by hand.
      </p>

      <div className="row g-3">
        {!lockProduct && (
          <div className="col-12">
            <ProductSelector value={product} onChange={setProduct} width="100%" />
          </div>
        )}

        <div className="col-12 col-md-7">
          <label className="form-label" htmlFor="monitor-interval">Check for new listings</label>
          <select id="monitor-interval" className="form-select" value={interval}
            onChange={(e) => setIntervalMins(Number(e.target.value))}>
            {INTERVALS.map((i) => (
              <option key={i.minutes} value={i.minutes} disabled={floor != null && i.minutes < floor}>
                {i.label}{floor != null && i.minutes < floor ? ' — not on your plan' : ''}
              </option>
            ))}
          </select>
          {floor != null && (
            <div className="form-text">
              {/* intervalLabel already starts with "Every", so this sentence
                  must not supply its own. */}
              The fastest check on your plan is {intervalLabel(floor).toLowerCase()}.
            </div>
          )}
        </div>

        <div className="col-12 col-md-5">
          <label className="form-label" htmlFor="monitor-max">Listings per marketplace</label>
          <input id="monitor-max" type="number" min="1" max="50" className="form-control"
            value={maxResults} onChange={(e) => setMaxResults(e.target.value)} />
          <div className="form-text">Up to 50. Applied per channel, not as a total.</div>
        </div>

        <div className="col-12">
          <hr className="my-2" style={{ borderColor: 'var(--border-soft)', opacity: 1 }} />
        </div>

        <div className="col-12">
          <label className="form-label">Marketplaces</label>
          {mpQ.isLoading ? (
            <div className="text-muted small">Loading channels…</div>
          ) : codes.length === 0 ? (
            <div className="field-note field-note--warn">
              <FiAlertTriangle size={15} />
              <span>No marketplaces are connected yet. Connect one in Settings before monitoring.</span>
            </div>
          ) : (
            <>
              {/* Same label+checkbox pattern as Find competitors: the chip's
                  selected style is keyed to :has(input:checked), so a plain
                  button gets no visual state however well the click handler
                  works. Not disabled when health is unknown either — a health
                  endpoint that hasn't answered shouldn't lock the whole form. */}
              <div className="d-flex flex-wrap gap-2">
                {codes.map((code) => {
                  const h = healthFor(healthQ.data, code)
                  const badge = healthBadge(h)
                  // A badge only when health said something definite. "Unknown"
                  // on every chip is the health call not having answered, which
                  // is noise the user can neither read nor act on.
                  const showBadge = Boolean(h) && !isUsable(h)
                  return (
                    <label key={code} className="marketplace-chip">
                      <input type="checkbox" checked={markets.includes(code)}
                        onChange={() => toggleMarket(code)} />
                      <span>{mpLabel(code)}</span>
                      {showBadge && <StatusBadge status={badge.tone} label={badge.label} />}
                    </label>
                  )
                })}
              </div>
              <div className="field-note mt-3" style={{ maxWidth: 'none' }}>
                <FiInfo size={15} />
                <span>
                  {markets.length === 0
                    ? 'Nothing selected, so every connected marketplace is checked. Pick specific ones to spend less API quota.'
                    : `Only ${markets.map(mpLabel).join(' and ')} will be checked each run.`}
                </span>
              </div>
            </>
          )}
        </div>
      </div>
    </Modal>
  )
}
