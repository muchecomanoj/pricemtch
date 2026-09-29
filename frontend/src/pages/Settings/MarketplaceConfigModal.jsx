import { useEffect, useState } from 'react'
import { FiZap, FiCheckCircle, FiXCircle, FiAlertTriangle, FiInfo } from 'react-icons/fi'
import Modal from '../../components/common/Modal'
import Button from '../../components/common/Button'
import { marketplaceConfigService } from '../../services/marketplaceConfigService'
import { useNotification } from '../../context/NotificationContext'

// Configure a marketplace's integration credentials. Secrets are write-only:
// the live entry returns them masked (••••9999) which we show as the placeholder;
// the field starts empty and a secret is sent only when the admin types a new one.
export default function MarketplaceConfigModal({ entry, name, show, onClose, onSaved }) {
  const { notify } = useNotification()
  const code = entry?.code
  const schema = marketplaceConfigService.schema(code)

  const [enabled, setEnabled] = useState(false)
  const [values, setValues] = useState({})    // non-secret field values (real, prefilled)
  const [secrets, setSecrets] = useState({})   // secret fields the admin typed
  const [busy, setBusy] = useState(false)
  const [testResult, setTestResult] = useState(null)

  // Masked secret values come back on the entry (e.g. { certId: '••••9999' }).
  const creds = entry?.credentials || {}

  // Hydrate the form from the entry each time the modal opens.
  useEffect(() => {
    if (!show || !entry) return
    setEnabled(!!entry.enabled)
    // Prefill only non-secret fields with their real stored values.
    const v = {}
    for (const f of schema.fields) if (!f.secret) v[f.key] = creds[f.key] ?? ''
    setValues(v)
    setSecrets({})
    setTestResult(
      entry.status === 'ERROR' && entry.lastMessage ? { ok: false, message: entry.lastMessage }
        : entry.status === 'CONNECTED' && entry.lastMessage ? { ok: true, message: entry.lastMessage }
          : null,
    )
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [show, entry])

  // Build the { enabled, credentials } payload: every non-secret field, plus only
  // the secrets the admin actually typed (blank = keep the stored value).
  const buildPayload = () => {
    const credentials = {}
    for (const f of schema.fields) {
      if (f.secret) { if (secrets[f.key]) credentials[f.key] = secrets[f.key] }
      else if (values[f.key] !== '' && values[f.key] != null) credentials[f.key] = values[f.key]
    }
    return { enabled, credentials }
  }

  const persist = async () => {
    const updated = await marketplaceConfigService.save(code, buildPayload())
    setSecrets({})
    onSaved?.(updated)   // refresh the card from the response
    return updated
  }

  const save = async () => {
    setBusy(true)
    try {
      await persist()
      notify.success(`${name} configuration saved`)
      onClose()
    } catch (e) { notify.error(e.message || 'Could not save configuration') }
    finally { setBusy(false) }
  }

  const runTest = async () => {
    setBusy(true); setTestResult(null)
    try {
      // Test uses stored credentials — persist the current form first.
      await persist()
      const res = await marketplaceConfigService.test(code)
      const ok = res?.status === 'CONNECTED'
      setTestResult({ ok, message: res?.lastMessage || (ok ? 'Connection successful.' : 'Connection failed.') })
    } catch (e) { setTestResult({ ok: false, message: e.message || 'Test failed' }) }
    finally { setBusy(false) }
  }

  return (
    <Modal show={show} title={`Configure ${name}`} onClose={onClose} size="lg"
      footer={(
        <>
          <Button variant="light" onClick={onClose}>Cancel</Button>
          <Button loading={busy} onClick={save}>Save</Button>
        </>
      )}>
      <>
        {schema.docsHint && <p className="text-muted small">{schema.docsHint}</p>}

        {/* Declared adapter capabilities (from the live entry). */}
        {entry?.capabilities?.length > 0 && (
          <div className="mb-3">
            <div className="text-muted small mb-1">Supported capabilities</div>
            <div className="d-flex flex-wrap gap-2">
              {entry.capabilities.map((c) => (
                <span key={c} className="badge text-bg-light border d-inline-flex align-items-center gap-1"><FiCheckCircle size={11} className="text-success" /> {c}</span>
              ))}
            </div>
          </div>
        )}

        {/* Access / policy notes (e.g. eBay Marketplace Insights restricted) */}
        {schema.notes?.map((n, i) => (
          <div key={i} className={`d-flex align-items-start gap-2 small mb-3 p-2 rounded-3 ${n.tone === 'warning' ? 'text-warning-emphasis' : 'text-muted'}`}
            style={{ background: 'var(--app-bg)', border: '1px solid var(--border-soft)' }}>
            {n.tone === 'warning' ? <FiAlertTriangle className="flex-shrink-0 mt-1" /> : <FiInfo className="flex-shrink-0 mt-1" />}
            <span>{n.text}</span>
          </div>
        ))}

        <div className="form-check form-switch mb-3">
          <input type="checkbox" role="switch" className="form-check-input" id="mp-enabled"
            checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
          <label className="form-check-label" htmlFor="mp-enabled">
            Enabled <span className="text-muted small">— include this marketplace in searches</span>
          </label>
        </div>

        {schema.fields.length === 0 ? (
          <div className="text-muted small">No configuration required for this marketplace.</div>
        ) : (
          <div className="row g-3">
            {schema.fields.map((f) => {
              const col = f.type === 'select' ? 'col-md-4' : 'col-md-6'
              const setVal = (val) => setValues((v) => ({ ...v, [f.key]: val }))
              return (
                <div className={col} key={f.key}>
                  <label className="form-label">{f.label}{f.required && <span className="text-danger ms-1">*</span>}</label>
                  {f.type === 'select' ? (
                    <select className="form-select" value={values[f.key] || ''} onChange={(e) => setVal(e.target.value)}>
                      <option value="">Choose…</option>
                      {f.options.map((o) => <option key={o} value={o}>{f.optionLabels?.[o] || o}</option>)}
                    </select>
                  ) : f.secret ? (
                    <input type="password" className="form-control" autoComplete="new-password"
                      placeholder={creds[f.key] ? `${creds[f.key]} — leave blank to keep` : 'Enter value'}
                      value={secrets[f.key] || ''} onChange={(e) => setSecrets((s) => ({ ...s, [f.key]: e.target.value }))} />
                  ) : (
                    <input type="text" className="form-control" placeholder={f.placeholder}
                      value={values[f.key] || ''} onChange={(e) => setVal(e.target.value)} />
                  )}
                </div>
              )
            })}
          </div>
        )}

        {schema.fields.length > 0 && (
          <div className="d-flex align-items-center gap-3 mt-3 flex-wrap">
            <Button variant="light" icon={FiZap} loading={busy} onClick={runTest}>Save &amp; test connection</Button>
            {testResult && (
              <span className={`small d-inline-flex align-items-center gap-1 ${testResult.ok ? 'text-success' : 'text-danger'}`}>
                {testResult.ok ? <FiCheckCircle /> : <FiXCircle />} {testResult.message}
              </span>
            )}
          </div>
        )}
      </>
    </Modal>
  )
}
