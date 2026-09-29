import { useEffect, useState } from 'react'
import { FiUploadCloud, FiAlertTriangle, FiClock } from 'react-icons/fi'
import Modal from '../../components/common/Modal'
import Button from '../../components/common/Button'
import { bulkSearchService } from '../../services/bulkSearchService'
import { useNotification } from '../../context/NotificationContext'

// Starting a bulk search.
//
// Deliberately not the product import wizard, which takes the same file and
// does the opposite with it. This searches the marketplaces and saves nothing;
// that one creates products and never calls a marketplace. Mixing them up
// silently fills the catalogue with rows nobody reviewed.

// ~3 rows a minute, set by the AI token budget. Told BEFORE the upload — a
// 100-row file runs for over half an hour, and finding that out afterwards is
// how someone ends up sitting on a progress bar waiting for it to hurry up.
const ROWS_PER_MIN = 3
const MAX_ROWS = 500

function estimate(rows) {
  const mins = Math.ceil(rows / ROWS_PER_MIN)
  if (mins < 60) return `about ${mins} minute${mins === 1 ? '' : 's'}`
  const h = Math.floor(mins / 60)
  const m = mins % 60
  return `about ${h} hour${h === 1 ? '' : 's'}${m ? ` ${m} minutes` : ''}`
}

// Counts the data lines so the estimate is real rather than generic. Only CSV
// can be counted client-side; a spreadsheet is opaque until the backend reads
// it, so that case falls back to a rate rather than a total.
function countCsvRows(file) {
  return new Promise((resolve) => {
    if (!/\.csv$/i.test(file.name)) return resolve(null)
    const reader = new FileReader()
    reader.onload = () => {
      const lines = String(reader.result || '').split(/\r?\n/).filter((l) => l.trim())
      resolve(Math.max(0, lines.length - 1))   // minus the header
    }
    reader.onerror = () => resolve(null)
    reader.readAsText(file.slice(0, 2_000_000))
  })
}

export default function BulkSearchModal({ show, onClose, destination, onStarted }) {
  const { notify } = useNotification()
  const [file, setFile] = useState(null)
  const [rowCount, setRowCount] = useState(null)
  const [judge, setJudge] = useState(true)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (!show) { setFile(null); setRowCount(null); setJudge(true); setBusy(false) }
  }, [show])

  const pick = async (f) => {
    setFile(f)
    setRowCount(f ? await countCsvRows(f) : null)
  }

  const tooBig = rowCount != null && rowCount > MAX_ROWS

  const start = async () => {
    if (!file) return notify.warn('Choose a CSV or Excel file first')
    setBusy(true)
    try {
      const job = await bulkSearchService.start(file, {
        judge,
        country: destination?.country || undefined,
        postalCode: destination?.postalCode || undefined,
      })
      onStarted?.(job)
    } catch (e) {
      notify.error(e?.message || 'The bulk search could not be started.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal show={show} title="Search a list of products" onClose={onClose} size="lg"
      footer={(
        <>
          <Button variant="light" onClick={onClose}>Cancel</Button>
          <Button icon={FiUploadCloud} loading={busy} disabled={!file || tooBig} onClick={start}>
            Start searching
          </Button>
        </>
      )}>

      <p className="text-muted small">
        Searches the marketplaces for every row and shows you what it found.{' '}
        <strong>Nothing is added to your catalogue</strong> — you save the rows you want afterwards.
        To create products directly from a file instead, use Import on the Products page.
      </p>

      <label className="form-label">CSV or Excel file</label>
      <input type="file" className="form-control" accept=".csv,.xlsx,.xls"
        onChange={(e) => pick(e.target.files?.[0] || null)} />
      <div className="form-text">
        Same columns as the product import: sku, title, brand, asin, upc, ean, gtin, mpn.
        Headers are detected automatically.
      </div>

      {file && (
        <div className={`field-note ${tooBig ? 'field-note--warn' : ''} mt-3`} style={{ maxWidth: 'none' }}>
          {tooBig ? <FiAlertTriangle size={15} /> : <FiClock size={15} />}
          <span>
            {tooBig ? (
              <>
                <strong>{rowCount} rows is too many.</strong> The limit is {MAX_ROWS} per file —
                split it and run the parts separately.
              </>
            ) : rowCount != null ? (
              <>
                <strong>{rowCount} row{rowCount === 1 ? '' : 's'} — {estimate(rowCount)}.</strong>{' '}
                This runs in the background, so you can leave the page and come back to it.
              </>
            ) : (
              <>
                <strong>Around {ROWS_PER_MIN} rows a minute.</strong> This runs in the background,
                so you can leave the page and come back to it.
              </>
            )}
          </span>
        </div>
      )}

      <div className="form-check mt-3">
        <input className="form-check-input" type="checkbox" id="bulk-judge"
          checked={!judge} onChange={(e) => setJudge(!e.target.checked)} />
        <label className="form-check-label small" htmlFor="bulk-judge">
          Skip the AI review — faster, but every listing comes back unverified and has to be
          checked by hand.
        </label>
      </div>

      {destination?.country && (
        <div className="form-text mt-3">
          eBay delivery costs will be figured to {destination.country}
          {destination.postalCode ? ` ${destination.postalCode}` : ''}, as set on the search screen.
          Amazon has no address parameter, so it is not used there.
        </div>
      )}
    </Modal>
  )
}
