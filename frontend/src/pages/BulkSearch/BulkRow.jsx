import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  FiChevronDown, FiChevronRight, FiBookmark, FiArrowRight, FiAlertCircle,
} from 'react-icons/fi'
import Button from '../../components/common/Button'
import { JudgedRow } from '../SearchProduct/JudgedListings'
import ExistingProductDialog from './ExistingProductDialog'
import { bulkSearchService, parseListings } from '../../services/bulkSearchService'
import { useNotification } from '../../context/NotificationContext'

// One line of the uploaded file.
const STATUS = {
  FOUND: { tone: 'green', label: 'Found' },
  NONE_MATCHED: { tone: 'orange', label: 'Nothing matched' },
  NO_LISTINGS: { tone: 'slate', label: 'No listings' },
  NOT_JUDGED: { tone: 'blue', label: 'Not reviewed' },
  ERROR: { tone: 'red', label: 'Failed' },
  PENDING: { tone: 'slate', label: 'Waiting' },
}

// What the file said, for the columns that were filled in. Shown so a row that
// found nothing can be traced back to the cell that caused it.
function inputSummary(r) {
  return [
    ['SKU', r.inputSku], ['ASIN', r.inputAsin], ['UPC', r.inputUpc],
    ['EAN', r.inputEan], ['GTIN', r.inputGtin], ['MPN', r.inputMpn],
  ].filter(([, v]) => v)
}

export default function BulkRow({ row: r, jobId, onSaved }) {
  const { notify } = useNotification()
  const [open, setOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [sku, setSku] = useState('')
  const [needsSku, setNeedsSku] = useState(false)
  const [existing, setExisting] = useState(null)   // SaveRowResponse with status EXISTS
  const [saved, setSaved] = useState(null)         // productId, before the row refetches

  const st = STATUS[r.status] || { tone: 'slate', label: r.status || 'Unknown' }
  const matches = parseListings(r.matchesJson)
  const rejected = parseListings(r.rejectedJson)

  // CREATED / EXISTS / UPDATED / UNCHANGED all arrive as HTTP 200. Only EXISTS
  // stops for an answer; the rest are finished outcomes and are reported as
  // what actually happened rather than all as "saved".
  const OUTCOME_TOAST = {
    CREATED: ['success', 'Added to your catalogue'],
    UPDATED: ['success', 'Existing product updated'],
    UNCHANGED: ['info', 'Already up to date — nothing changed'],
  }

  const save = async (update = false) => {
    setSaving(true)
    try {
      const res = await bulkSearchService.save(jobId, r.id, { sku, update })

      if (res?.status === 'EXISTS') {
        // Not an error and not a save: the catalogue already has this, and
        // whether to overwrite it is the user's call.
        setExisting(res)
        return
      }

      const [tone, text] = OUTCOME_TOAST[res?.status] || ['success', 'Saved to your catalogue']
      notify[tone](text)
      setNeedsSku(false)
      setExisting(null)
      if (res?.productId != null) setSaved(res.productId)
      onSaved?.(res)
    } catch (e) {
      // A genuine 400: the backend needs a SKU it could not derive, or the one
      // supplied is taken. Both are fixed by typing one, so the field is
      // offered rather than the message left as a dead end.
      setNeedsSku(true)
      notify.error(e?.message || 'Could not save this row — try giving it a SKU.')
    } finally {
      setSaving(false)
    }
  }

  const productId = r.savedProductId ?? saved

  return (
    <div className="rounded-3 border-soft" style={{ border: '1px solid var(--border-soft)' }}>
      <ExistingProductDialog result={existing} saving={saving}
        onClose={() => setExisting(null)}
        onConfirm={() => save(true)} />
      <button type="button"
        className="btn w-100 text-start d-flex align-items-center gap-2 p-3"
        onClick={() => setOpen((o) => !o)}>
        {open ? <FiChevronDown className="flex-shrink-0" /> : <FiChevronRight className="flex-shrink-0" />}

        {/* The row number is the line in THEIR spreadsheet, so a problem can be
            fixed in the file rather than hunted for by title. */}
        <span className="text-muted small font-monospace flex-shrink-0">Row {r.rowNumber}</span>

        <span className="fw-semibold text-truncate flex-grow-1" style={{ minWidth: 0 }}>
          {r.inputTitle || r.resolvedQuery || `#${r.rowNumber}`}
        </span>

        <span className={`mr-pill mr-pill--${st.tone} flex-shrink-0`}>{st.label}</span>

        {r.matchCount > 0 && (
          <span className="text-muted small flex-shrink-0 d-none d-md-inline">
            {r.matchCount} match{r.matchCount === 1 ? '' : 'es'}
          </span>
        )}
        {r.resolvedIdentifierType && (
          <span className="text-muted small flex-shrink-0 d-none d-lg-inline">
            via {r.resolvedIdentifierType}
          </span>
        )}
      </button>

      {open && (
        <div className="px-3 pb-3">
          <div className="text-muted small mb-2 d-flex flex-wrap gap-3">
            {inputSummary(r).map(([k, v]) => (
              <span key={k}>{k} <span className="font-monospace">{v}</span></span>
            ))}
            {r.resolvedQuery && <span>searched: <strong>{r.resolvedQuery}</strong></span>}
          </div>

          {/* Written to be shown as-is. */}
          {r.message && (
            <div className={`field-note ${r.status === 'ERROR' ? 'field-note--warn' : ''} mb-3`}
              style={{ maxWidth: 'none' }}>
              <FiAlertCircle size={15} />
              <span>{r.message}</span>
            </div>
          )}

          {matches.length > 1 && (
            <div className="text-muted small mb-2">
              {matches.length} listings matched this row — they are alternatives, not a ranking.
              Saving uses the best of them.
            </div>
          )}
          {matches.length > 0 && (
            <div className="row g-2">
              {matches.map((m, i) => (
                <div className="col-12 col-xl-6" key={m.marketplaceItemId || i}>
                  {/* The same verdict card as a single search — same data, same
                      meaning, so it must not look like a different thing. */}
                  <JudgedRow r={m} />
                </div>
              ))}
            </div>
          )}

          {rejected.length > 0 && (
            <div className="text-muted small mt-2">
              {rejected.length} other listing{rejected.length === 1 ? '' : 's'} were rejected.
            </div>
          )}

          <div className="d-flex flex-wrap align-items-center gap-2 mt-3 pt-3 border-top-soft">
            {productId != null ? (
              <Link className="btn btn-sm btn-light d-inline-flex align-items-center gap-1"
                to={`/products/${productId}`}>
                Open in your catalogue <FiArrowRight />
              </Link>
            ) : matches.length > 0 ? (
              <>
                {needsSku && (
                  <input className="form-control form-control-sm" style={{ maxWidth: 220 }}
                    placeholder="SKU for this product" value={sku}
                    onChange={(e) => setSku(e.target.value)} />
                )}
                <Button variant="light" icon={FiBookmark} loading={saving} onClick={() => save(false)}>
                  Save to catalogue
                </Button>
                <span className="text-muted small">
                  Creates a product. If one already matches, you will be asked before anything changes.
                </span>
              </>
            ) : (
              <span className="text-muted small">Nothing to save from this row.</span>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
