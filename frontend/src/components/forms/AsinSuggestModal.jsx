import { useCallback, useEffect, useState } from 'react'
import { FiSearch, FiAlertCircle, FiInfo, FiExternalLink, FiRotateCcw } from 'react-icons/fi'
import Modal from '../common/Modal'
import Button from '../common/Button'
import EmptyState from '../common/EmptyState'
import { EMPTY_ASIN_LOOKUP } from '../../services/productService'
import { formatCurrency } from '../../utils/format'

// Suggested Amazon ASINs, from a saved product or from a typed value.
//
// The copy matters more than usual. These are SUGGESTIONS — the feature
// narrows the search, it does not decide. Testing on "Adobe Photoshop Elements
// 5" returned two real ASINs for two real Amazon listings, both of which were
// BOOKS ABOUT the software rather than the software. Only the title tells you
// that, so the title gets the room and the header asks the user to pick rather
// than announcing that we found it.
//
// `fetcher` is passed in so the same modal serves both endpoints.

// Only a tone. The backend's `message` carries the actual wording, and an
// unrecognised status must not read as a failure — the contract types it as a
// plain string, so the set can grow without warning.
const TONE = {
  FOUND: 'info',
  PARTIAL: 'warn',
  UNVERIFIED: 'warn',
  NOT_FOUND: 'info',
  NOT_CONFIGURED: 'warn',
  ERROR: 'warn',
}

export default function AsinSuggestModal({ show, onClose, fetcher, subject, onUse }) {
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setResult(await fetcher())
    } catch (e) {
      // 400 covers "no web-search model configured" and "product not found".
      // Both are worded usefully by the backend, so pass them through — the
      // first is a setup state rather than something that went wrong.
      setError(e?.message || 'Could not look up an ASIN.')
      setResult(EMPTY_ASIN_LOOKUP)
    } finally {
      setLoading(false)
    }
  }, [fetcher])

  useEffect(() => {
    if (show) load()
    else { setResult(null); setError(null) }
  }, [show, load])

  const rows = result?.suggestions ?? []
  // The model proposed more than survived verification against Amazon. Saying
  // so explains a short list instead of leaving it looking arbitrary.
  const dropped = (result?.proposedCount ?? 0) - rows.length

  return (
    <Modal show={show} onClose={onClose} size="lg"
      title="Pick the listing that matches your product"
      footer={(
        <>
          {result?.retryable && (
            <Button variant="light" icon={FiRotateCcw} loading={loading} onClick={load}>Try again</Button>
          )}
          <Button variant="light" onClick={onClose}>Cancel</Button>
        </>
      )}>

      <p className="text-muted small">
        Suggested Amazon listings for <strong>{subject}</strong>. Read the titles — a listing
        can be a manual, an accessory or a different variant and still look right.
      </p>

      {loading && (
        <div className="text-center py-4">
          <span className="spinner-border spinner-border-sm text-primary me-2" />
          <span className="text-muted small">Searching Amazon — this takes up to 20 seconds.</span>
        </div>
      )}

      {!loading && (error || result?.message) && (
        <div className={`field-note ${TONE[result?.status] === 'warn' || error ? 'field-note--warn' : ''}`}
          style={{ maxWidth: 'none' }}>
          <FiAlertCircle size={15} />
          <span>{error || result.message}</span>
        </div>
      )}

      {!loading && !error && rows.length === 0 && (
        // Zero is a normal outcome, not a failure — common for products not
        // sold in the US, since the connector only reaches US and CA.
        <div className="mt-3">
          <EmptyState icon={FiSearch} title="No matching Amazon listing found"
            message="Search Amazon yourself and paste the ASIN. Products not sold in the US or Canada will not appear here." />
        </div>
      )}

      {!loading && rows.length > 0 && (
        <div className="d-flex flex-column gap-2 mt-3">
          {rows.map((r) => {
            const taken = r.alreadyUsedByAnotherProduct
            return (
              <div key={r.asin} className={`result-card ${taken ? 'opacity-75' : ''}`}>
                <div className="flex-grow-1" style={{ minWidth: 0 }}>
                  <div className="d-flex align-items-baseline justify-content-between gap-2">
                    <span className="font-monospace fw-semibold">{r.asin}</span>
                    <span className="fw-semibold flex-shrink-0">
                      {r.price != null ? formatCurrency(r.price, r.currency || 'USD') : '—'}
                    </span>
                  </div>
                  {/* Amazon's own title — the deciding information. */}
                  <div className="mt-1" style={{ lineHeight: 1.4 }}>{r.title}</div>
                  <div className="text-muted small mt-1 d-flex flex-wrap gap-2">
                    {r.brand && <span>{r.brand}</span>}
                    <span>· {r.available ? 'Available' : 'Not available'}</span>
                  </div>

                  {taken && (
                    <div className="d-flex align-items-start gap-1 text-warning mt-2" style={{ fontSize: 12 }}>
                      <FiAlertCircle size={13} className="flex-shrink-0 mt-1" />
                      <span>Already assigned to another product in your catalogue.</span>
                    </div>
                  )}

                  <div className="d-flex flex-wrap gap-1 mt-2">
                    <Button variant="light" disabled={taken}
                      title={taken ? 'Identifiers must be unique — saving this would fail.' : undefined}
                      onClick={() => { onUse(r.asin, r); onClose() }}>
                      Use this ASIN
                    </Button>
                    <a className="btn btn-sm btn-light d-inline-flex align-items-center gap-1"
                      href={`https://www.amazon.com/dp/${r.asin}`} target="_blank" rel="noreferrer">
                      Check on Amazon <FiExternalLink size={13} />
                    </a>
                  </div>
                </div>
              </div>
            )
          })}

          {dropped > 0 && (
            <p className="text-muted small mb-0">
              {dropped} more {dropped === 1 ? 'was' : 'were'} proposed but did not resolve on
              Amazon, so {dropped === 1 ? 'it is' : 'they are'} not shown.
            </p>
          )}
        </div>
      )}

      <div className="field-note mt-3" style={{ maxWidth: 'none' }}>
        <FiInfo size={15} />
        <span>
          Choosing one fills the ASIN field — it does not save. Check it, then save
          yourself. Prices and titles come from Amazon, not from the model.
        </span>
      </div>
    </Modal>
  )
}
