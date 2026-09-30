import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { FiExternalLink, FiSave, FiRotateCcw, FiEye, FiEyeOff } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Loader from '../../components/common/Loader'
import StatusBadge from '../../components/common/StatusBadge'
import { siteAdminService } from '../../services/siteAdminService'
import { useNotification } from '../../context/NotificationContext'
import { formatDateTime } from '../../utils/format'

// The landing page's text, one section at a time, as JSON. The sections have
// different shapes, so a JSON box per section is the honest v1 — a form per
// shape would be seven forms to keep in step with the backend.
//
// Not here, on purpose: pricing and the trial length. Both come from Plans,
// so the page can't advertise something the catalog doesn't sell.
// Page order, top to bottom.
const ORDER = ['HERO', 'FEATURES', 'HOW_IT_WORKS', 'ONE_WORKSPACE', 'STATS', 'AI_RECOMMENDATIONS', 'TESTIMONIALS', 'FAQS', 'CTA']
const LABELS = {
  HERO: 'Hero', FEATURES: 'Features', HOW_IT_WORKS: 'How it works', ONE_WORKSPACE: 'One workspace',
  STATS: 'Stats band', AI_RECOMMENDATIONS: 'AI recommendations', TESTIMONIALS: 'Testimonials',
  FAQS: 'FAQs', CTA: 'Closing call to action',
}
const NOTES = {
  HERO: 'The trial length is added between the first and second assurance automatically, from the Free plan.',
  STATS: 'A value starting with a number (“12M+”, “45 min”) counts up; anything else is shown as written.',
  FEATURES: 'icon is one of: box, search, trend, pie, bell, report.',
  ONE_WORKSPACE: 'Only the words are editable — the product table beside them is an illustration. A "\\n" in title sets the line break.',
  AI_RECOMMENDATIONS: 'Only the words are editable — the recommendation card beside them is an illustration. A "\\n" in title sets the line break.',
  TESTIMONIALS: 'initials is optional — it is worked out from the name when left out.',
}

const pretty = (payload) => {
  try { return JSON.stringify(JSON.parse(payload), null, 2) } catch { return payload ?? '' }
}

export default function LandingContentAdmin() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['landing-content'],
    queryFn: siteAdminService.landingSections,
  })

  const sections = [...(data || [])].sort((a, b) => {
    const ia = ORDER.indexOf(a.section); const ib = ORDER.indexOf(b.section)
    return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib)
  })

  return (
    <>
      <PageHeader title="Landing Page Content" subtitle="Platform — the text on the public website. Changes show on the next page load, no release needed."
        breadcrumb={[{ label: 'Home', to: '/platform/clients' }, { label: 'Platform' }, { label: 'Landing content' }]}
        actions={<Link to="/home" target="_blank" className="btn btn-light d-inline-flex align-items-center gap-2"><FiExternalLink /> View live page</Link>} />

      {isLoading && <Loader label="Loading sections…" />}
      {isError && <div className="alert alert-danger">{error?.message || 'Could not load the landing content.'}</div>}
      {!isLoading && !isError && sections.length === 0 && (
        <div className="alert alert-info">No sections yet. The live page is showing its built-in copy.</div>
      )}

      {sections.map((s) => (
        // Keyed by the save time so a successful save resets the editor to what was stored.
        <SectionEditor key={`${s.section}-${s.updatedAt}`} section={s} />
      ))}
    </>
  )
}

function SectionEditor({ section }) {
  const qc = useQueryClient()
  const { notify } = useNotification()
  const original = pretty(section.payload)
  const [text, setText] = useState(original)
  const [active, setActive] = useState(section.active !== false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const dirty = text !== original || active !== (section.active !== false)

  const save = async () => {
    // Caught here first so a typo doesn't need a round trip; the backend
    // checks again and its parser message is shown if it disagrees.
    let parsed
    try { parsed = JSON.parse(text) } catch (e) { setError(`Not valid JSON — ${e.message}`); return }
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      setError('Section content must be a JSON object — { … }, not a list or a single value.')
      return
    }
    setBusy(true)
    setError('')
    try {
      // The API takes the payload as a JSON *string*.
      await siteAdminService.saveLandingSection(section.section, { payload: JSON.stringify(parsed), active })
      notify.success(`${LABELS[section.section] || section.section} saved`)
      await qc.invalidateQueries({ queryKey: ['landing-content'] })
      qc.invalidateQueries({ queryKey: ['public', 'landing'] })
    } catch (e) {
      setError(e.message || 'Could not save this section')
    } finally { setBusy(false) }
  }

  const reset = () => { setText(original); setActive(section.active !== false); setError('') }

  return (
    <Card className="mb-3"
      title={<span className="d-inline-flex align-items-center gap-2">
        {LABELS[section.section] || section.section}
        <code className="small text-muted">{section.section}</code>
        {!active && <StatusBadge status="secondary" label="Hidden" />}
      </span>}
      subtitle={section.updatedAt
        ? `Last saved ${formatDateTime(section.updatedAt)}${section.updatedBy ? ` by ${section.updatedBy}` : ''}`
        : 'Never edited'}
      actions={(
        <div className="form-check form-switch mb-0">
          <input className="form-check-input" type="checkbox" role="switch" id={`active-${section.section}`}
            checked={active} onChange={(e) => setActive(e.target.checked)} />
          <label className="form-check-label small d-inline-flex align-items-center gap-1" htmlFor={`active-${section.section}`}>
            {active ? <><FiEye /> Shown</> : <><FiEyeOff /> Hidden</>}
          </label>
        </div>
      )}>
      {NOTES[section.section] && <div className="text-muted small mb-2">{NOTES[section.section]}</div>}
      <textarea className={`form-control font-monospace small ${error ? 'is-invalid' : ''}`} spellCheck={false}
        rows={Math.min(24, Math.max(6, text.split('\n').length + 1))}
        value={text} onChange={(e) => { setText(e.target.value); setError('') }} />
      {error && <div className="invalid-feedback d-block" style={{ whiteSpace: 'pre-wrap' }}>{error}</div>}
      <div className="d-flex justify-content-end gap-2 mt-2">
        <Button variant="light" size="sm" icon={FiRotateCcw} disabled={!dirty || busy} onClick={reset}>Discard changes</Button>
        <Button size="sm" icon={FiSave} loading={busy} disabled={!dirty} onClick={save}>Save</Button>
      </div>
    </Card>
  )
}
