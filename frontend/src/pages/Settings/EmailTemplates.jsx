import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { FiMail, FiSave, FiEye, FiEdit2, FiSend, FiCheckCircle } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Loader from '../../components/common/Loader'
import { templateService } from '../../services/templateService'
import { TEMPLATE_SAMPLE } from '../../mock/emailTemplates'
import { useNotification } from '../../context/NotificationContext'
import { useAuth } from '../../context/AuthContext'

const SUBJECT_MAX = 300
const BODY_MAX = 8000

// ── Client-side preview of UNSAVED edits ────────────────────────────────────
// Mirrors what the backend does when sending: fill listed {{variables}} with
// sample data (unknown ones render blank), escape, convert newlines to <br> and
// auto-link URLs. Used only while there are unsaved changes; the saved preview
// comes from the server so it's always the source of truth.
const escapeHtml = (s) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))
const substitute = (text = '') => text.replace(/\{\{\s*(\w+)\s*\}\}/g, (m, v) => (TEMPLATE_SAMPLE[v] ?? ''))
const autolink = (html) => html.replace(/(https?:\/\/[^\s<]+)/g, '<a href="$1" target="_blank" rel="noreferrer">$1</a>')
const clientHtml = (body) => autolink(escapeHtml(substitute(body))).replace(/\n/g, '<br>')

// Wrap a preview body in a minimal, self-contained document for the iframe.
const previewDoc = (htmlBody) => `<!doctype html><html><head><meta charset="utf-8">
<style>body{font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#0a1020;
line-height:1.6;padding:16px;margin:0} a{color:#4f46e5}</style></head>
<body>${htmlBody || ''}</body></html>`

export default function EmailTemplates() {
  const qc = useQueryClient()
  const { notify } = useNotification()
  const { user } = useAuth()
  const { data: templates, isLoading } = useQuery({ queryKey: ['email-templates'], queryFn: templateService.list })

  const [selectedKey, setSelectedKey] = useState(null)
  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')
  const [preview, setPreview] = useState(false)
  const [testEmail, setTestEmail] = useState('')
  const bodyRef = useRef(null)

  const selected = useMemo(
    () => (templates || []).find((t) => t.key === selectedKey) || (templates || [])[0],
    [templates, selectedKey],
  )

  // Load the selected template into the editable fields.
  useEffect(() => {
    if (selected) {
      setSelectedKey(selected.key)
      setSubject(selected.subject || '')
      setBody(selected.body || '')
      setPreview(false)
    }
  }, [selected])

  useEffect(() => { if (user?.email && !testEmail) setTestEmail(user.email) }, [user, testEmail])

  const dirty = selected && (subject !== selected.subject || body !== selected.body)
  const subjectOver = subject.length > SUBJECT_MAX
  const bodyOver = body.length > BODY_MAX
  const invalid = !subject.trim() || !body.trim() || subjectOver || bodyOver

  const saveMut = useMutation({
    mutationFn: () => templateService.update(selected.key, { subject: subject.trim(), body }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['email-templates'] }); notify.success('Template saved') },
    onError: (e) => notify.error(e.message || 'Could not save template'),
  })

  const testMut = useMutation({
    mutationFn: () => templateService.sendTest(selected.key, testEmail.trim()),
    onSuccess: (r) => notify.success(r?.message || `Test email sent to ${testEmail.trim()}`),
    onError: (e) => notify.error(e.message || 'Could not send test email'),
  })

  // Server-rendered preview of the SAVED template. Only fetched when previewing
  // with no unsaved edits — otherwise we render the current draft client-side.
  const previewQ = useQuery({
    queryKey: ['email-template-preview', selected?.key],
    queryFn: () => templateService.preview(selected.key),
    enabled: !!selected && preview && !dirty,
    staleTime: 0,
  })

  // Insert a {{variable}} at the cursor in the body textarea.
  const insertVar = (v) => {
    const token = `{{${v}}}`
    const el = bodyRef.current
    if (!el) { setBody((b) => b + token); return }
    const start = el.selectionStart ?? body.length
    const end = el.selectionEnd ?? body.length
    setBody(body.slice(0, start) + token + body.slice(end))
    requestAnimationFrame(() => { el.focus(); el.selectionStart = el.selectionEnd = start + token.length })
  }

  if (isLoading) return <Loader label="Loading templates…" />

  // Choose the preview to show: server render when saved & available, else the
  // client-side render of the current draft.
  const showDraftPreview = dirty || previewQ.isError
  const previewSubject = showDraftPreview ? substitute(subject) : (previewQ.data?.subject ?? substitute(subject))
  const previewHtml = showDraftPreview ? clientHtml(body) : previewQ.data?.htmlBody

  return (
    <div className="row g-3">
      {/* Template list */}
      <div className="col-12 col-lg-4">
        <Card title="Templates" bodyClassName="p-2">
          <div className="d-flex flex-column gap-1">
            {(templates || []).map((t) => (
              <button key={t.key}
                className={`btn text-start ${selected?.key === t.key ? 'btn-primary' : 'btn-light'}`}
                onClick={() => setSelectedKey(t.key)}>
                <div className="d-flex align-items-center justify-content-between gap-2">
                  <span className="d-flex align-items-center gap-2 fw-semibold"><FiMail size={14} /> {t.name}</span>
                  {t.edited
                    ? <span className={`badge ${selected?.key === t.key ? 'text-bg-light' : 'text-bg-success'}`}>Edited</span>
                    : <span className={`badge ${selected?.key === t.key ? 'text-bg-light' : 'text-bg-secondary'} bg-opacity-75`}>Not edited</span>}
                </div>
                <div className={`small ${selected?.key === t.key ? 'text-white-50' : 'text-muted'}`}>{t.description}</div>
              </button>
            ))}
          </div>
        </Card>
      </div>

      {/* Editor / preview */}
      <div className="col-12 col-lg-8">
        {selected && (
          <Card
            title={selected.name}
            subtitle={selected.edited ? 'Edited' : 'Not edited yet'}
            actions={(
              <div className="btn-group btn-group-sm">
                <button className={`btn ${!preview ? 'btn-primary' : 'btn-light'}`} onClick={() => setPreview(false)}><FiEdit2 /> Edit</button>
                <button className={`btn ${preview ? 'btn-primary' : 'btn-light'}`} onClick={() => setPreview(true)}><FiEye /> Preview</button>
              </div>
            )}>

            {preview ? (
              <div>
                <div className="text-muted small mb-1">Subject</div>
                <div className="fw-semibold mb-3">{previewSubject}</div>
                <div className="text-muted small mb-1">Body</div>
                {previewQ.isFetching && !showDraftPreview ? (
                  <Loader label="Rendering preview…" />
                ) : (
                  <iframe title="Email preview" sandbox="" srcDoc={previewDoc(previewHtml)}
                    className="w-100 rounded-3" style={{ border: '1px solid var(--border-soft)', minHeight: 320, background: '#fff' }} />
                )}
                <div className="form-text mt-2">
                  {showDraftPreview
                    ? 'Preview of your unsaved draft, rendered with sample data. Save to preview exactly what the server sends.'
                    : 'Rendered by the server with realistic sample data — this is what clients receive.'}
                </div>
              </div>
            ) : (
              <>
                <label className="form-label d-flex justify-content-between">
                  <span>Subject</span>
                  <span className={`small ${subjectOver ? 'text-danger' : 'text-muted'}`}>{subject.length}/{SUBJECT_MAX}</span>
                </label>
                <input className={`form-control mb-3 ${subjectOver ? 'is-invalid' : ''}`} value={subject}
                  onChange={(e) => setSubject(e.target.value)} />

                <label className="form-label d-flex justify-content-between">
                  <span>Body</span>
                  <span className={`small ${bodyOver ? 'text-danger' : 'text-muted'}`}>{body.length}/{BODY_MAX}</span>
                </label>
                <textarea ref={bodyRef} className={`form-control mb-2 ${bodyOver ? 'is-invalid' : ''}`} rows={12}
                  style={{ fontFamily: 'inherit' }} value={body} onChange={(e) => setBody(e.target.value)} />

                <div className="mb-1 small text-muted">Insert a variable:</div>
                <div className="d-flex flex-wrap gap-2 mb-1">
                  {(selected.variables || []).map((v) => (
                    <button key={v} type="button" className="badge text-bg-light border" style={{ cursor: 'pointer' }}
                      onClick={() => insertVar(v)}>{`{{${v}}}`}</button>
                  ))}
                </div>
                <div className="form-text">Click a variable to insert it at the cursor. It’s filled with real data when the email is sent; URLs become clickable links.</div>
              </>
            )}

            <hr />
            <div className="d-flex flex-wrap gap-2 justify-content-between align-items-end">
              <div style={{ minWidth: 240 }}>
                <label className="form-label small text-muted mb-1">Send a test to</label>
                <div className="input-group input-group-sm">
                  <input type="email" className="form-control" placeholder="you@example.com"
                    value={testEmail} onChange={(e) => setTestEmail(e.target.value)} />
                  <Button variant="light" icon={FiSend} loading={testMut.isPending}
                    disabled={!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(testEmail.trim())}
                    onClick={() => testMut.mutate()}>Send test</Button>
                </div>
                <div className="form-text">Sends the rendered template (sample data, subject prefixed [TEST]).</div>
              </div>
              <div className="d-flex gap-2">
                {dirty && <Button variant="light" onClick={() => { setSubject(selected.subject || ''); setBody(selected.body || '') }}>Reset</Button>}
                <Button icon={saveMut.isSuccess && !dirty ? FiCheckCircle : FiSave} loading={saveMut.isPending}
                  disabled={!dirty || invalid} onClick={() => saveMut.mutate()}>Save changes</Button>
              </div>
            </div>
            {invalid && dirty && (
              <div className="text-danger small mt-2">
                {(!subject.trim() || !body.trim()) ? 'Subject and body are both required.'
                  : subjectOver ? `Subject must be ${SUBJECT_MAX} characters or fewer.`
                    : `Body must be ${BODY_MAX} characters or fewer.`}
              </div>
            )}
          </Card>
        )}
      </div>
    </div>
  )
}
