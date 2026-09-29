import { useState, useEffect, useRef } from 'react'
import { FiUploadCloud, FiCheckCircle, FiAlertTriangle, FiDownload } from 'react-icons/fi'
import Modal from '../common/Modal'
import Button from '../common/Button'
import { importService } from '../../services/importService'
import { useNotification } from '../../context/NotificationContext'

// Wires: POST /imports/preview -> POST /imports -> GET /imports/{id} (poll) -> GET /imports/{id}/errors
export default function ImportWizard({ show, onClose, onComplete }) {
  const { notify } = useNotification()
  const [step, setStep] = useState(1) // 1 select, 2 preview, 3 running/result
  const [file, setFile] = useState(null)
  const [preview, setPreview] = useState(null)
  const [job, setJob] = useState(null)
  const [errorReport, setErrorReport] = useState(null)   // Blob, when rows failed
  const [busy, setBusy] = useState(false)
  const pollRef = useRef(null)

  const reset = () => {
    clearInterval(pollRef.current)
    setStep(1); setFile(null); setPreview(null); setJob(null); setErrorReport(null); setBusy(false)
  }
  const close = () => { reset(); onClose() }

  useEffect(() => () => clearInterval(pollRef.current), [])

  const doPreview = async () => {
    if (!file) return notify.warn('Choose a CSV/XLSX file first')
    setBusy(true)
    try {
      setPreview(await importService.preview(file))
      setStep(2)
    } catch (e) { notify.error(e.message || 'Preview failed') }
    finally { setBusy(false) }
  }

  const doImport = async () => {
    setBusy(true)
    try {
      const created = await importService.create(file, preview?.suggestedMapping)
      setJob(created); setStep(3)
      poll(created.id)
    } catch (e) { notify.error(e.message || 'Import failed'); setBusy(false) }
  }

  const poll = (id) => {
    clearInterval(pollRef.current)
    pollRef.current = setInterval(async () => {
      try {
        const j = await importService.get(id)
        setJob(j)
        const done = ['COMPLETED', 'FAILED', 'COMPLETED_WITH_ERRORS'].includes(j.status)
        if (done) {
          clearInterval(pollRef.current)
          setBusy(false)
          if (j.errorCount > 0) setErrorReport(await importService.errorReport(id).catch(() => null))
          notify.success(`Import ${j.status?.toLowerCase()} — ${j.successCount ?? 0} imported`)
          onComplete?.()
        }
      } catch { clearInterval(pollRef.current); setBusy(false) }
    }, 1500)
  }

  // Hand the file to the browser. Revoked immediately after — an object URL
  // held open pins the whole blob in memory for the life of the tab.
  const saveErrorReport = () => {
    if (!errorReport) return
    const url = URL.createObjectURL(errorReport)
    const a = document.createElement('a')
    a.href = url
    a.download = `import-${job?.id ?? 'errors'}-failed-rows.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }

  const footer = step === 1
    ? <><Button variant="light" onClick={close}>Cancel</Button><Button icon={FiUploadCloud} loading={busy} onClick={doPreview}>Preview</Button></>
    : step === 2
    ? <><Button variant="light" onClick={() => setStep(1)}>Back</Button><Button loading={busy} onClick={doImport}>Start Import</Button></>
    : <Button variant="light" onClick={close}>Close</Button>

  return (
    <Modal show={show} title="Import Products" onClose={close} size="lg" footer={footer}>
      {/* Stepper */}
      <div className="d-flex gap-2 mb-4">
        {['Select file', 'Preview', 'Import'].map((label, i) => (
          <div key={label} className={`flex-fill text-center small fw-semibold py-2 rounded ${step === i + 1 ? 'text-bg-primary' : 'text-muted'}`}
            style={{ background: step === i + 1 ? undefined : 'var(--app-bg)' }}>
            {i + 1}. {label}
          </div>
        ))}
      </div>

      {step === 1 && (
        <div>
          <label className="form-label">CSV or Excel file</label>
          <input type="file" className="form-control" accept=".csv,.xlsx,.xls"
            onChange={(e) => setFile(e.target.files?.[0] || null)} />
          <div className="form-text">The file is validated in a preview before anything is imported.</div>
        </div>
      )}

      {step === 2 && preview && (
        <div>
          <div className="d-flex gap-4 mb-3 small">
            <span><strong>{preview.fileName}</strong></span>
            <span>{preview.fileType}</span>
            <span>{preview.totalRows} rows</span>
            <span>{preview.detectedColumns?.length || 0} columns</span>
          </div>
          {preview.warnings?.length > 0 && (
            <div className="alert alert-warning py-2 d-flex gap-2 align-items-start">
              <FiAlertTriangle className="mt-1" />
              <div>{preview.warnings.map((w, i) => <div key={i} className="small">{w}</div>)}</div>
            </div>
          )}
          <div className="table-responsive" style={{ maxHeight: 260, overflow: 'auto' }}>
            <table className="table table-sm">
              <thead><tr>{(preview.detectedColumns || []).map((c) => <th key={c}>{c}</th>)}</tr></thead>
              <tbody>
                {(preview.sampleRows || []).map((row, i) => (
                  <tr key={i}>
                    {(preview.detectedColumns || []).map((c) => <td key={c}>{String(row[c] ?? '')}</td>)}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {step === 3 && job && (
        <div>
          <div className="d-flex align-items-center gap-2 mb-3">
            {['COMPLETED'].includes(job.status)
              ? <FiCheckCircle className="text-success" size={22} />
              : <span className="spinner-border spinner-border-sm text-primary" />}
            <span className="fw-semibold">{job.status}</span>
          </div>
          <div className="progress mb-3" style={{ height: 10 }}>
            <div className="progress-bar" style={{ width: `${job.progressPercent ?? 0}%` }} />
          </div>
          <div className="row text-center g-2 mb-3">
            {[['Total', job.totalRows], ['Success', job.successCount], ['Errors', job.errorCount], ['Duplicates', job.duplicateCount]].map(([k, v]) => (
              <div className="col-3" key={k}>
                <div className="card"><div className="card-body py-2">
                  <div className="h5 mb-0">{v ?? 0}</div><div className="text-muted small">{k}</div>
                </div></div>
              </div>
            ))}
          </div>
          {job.errorCount > 0 && (
            <div className="alert alert-warning py-2 d-flex flex-wrap align-items-center gap-2 mb-0">
              <FiAlertTriangle className="flex-shrink-0" />
              <span className="small flex-grow-1">
                <strong>{job.errorCount}</strong> row{job.errorCount === 1 ? '' : 's'} could not be
                imported. The rest were saved.
              </span>
              {/* The endpoint returns a file, not a list — so it is offered as
                  one rather than half-rendered as a table. */}
              {errorReport && (
                <Button variant="light" icon={FiDownload} onClick={saveErrorReport}>
                  Download the failed rows
                </Button>
              )}
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}
