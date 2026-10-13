import { useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { FiCheckCircle, FiAlertCircle } from 'react-icons/fi'
import Button from '../common/Button'
import { publicService } from '../../services/publicService'

// The three public lead forms: Book a demo, Contact us, Newsletter.
//
// What the backend sends back, and where it goes:
//   success   → its `message`, shown as sent (the wording is tuned per form
//               on the backend and can change without a frontend release)
//   400 + errors { field: "…" } → under that field
//   400 without errors → above the button; this is the hourly throttle
//               ("That is a lot of submissions…"), shared across all three forms
//
// Limits match the backend's, so most mistakes are caught before sending.
const LIMITS = { name: 150, email: 150, companyName: 200, phone: 30, subject: 200 }
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

// Blank optional fields go as absent, not as "".
function clean(values) {
  const out = {}
  for (const [k, v] of Object.entries(values)) {
    const t = typeof v === 'string' ? v.trim() : v
    if (t !== '' && t != null) out[k] = t
  }
  return out
}

function useLeadForm(send, fields) {
  const form = useForm()
  const [status, setStatus] = useState(null)   // { ok: bool, text }
  const [busy, setBusy] = useState(false)
  // A double-click lands both clicks before React re-renders; the ref stops
  // the second from sending (and from spending one of the five per hour).
  const inFlight = useRef(false)

  const onSubmit = form.handleSubmit(async (values) => {
    if (inFlight.current) return
    inFlight.current = true
    setBusy(true)
    setStatus(null)
    try {
      const { message } = await send(clean(values))
      setStatus({ ok: true, text: message })
      form.reset()
    } catch (e) {
      const byField = e.fieldErrors || {}
      const onForm = Object.keys(byField).filter((k) => fields.includes(k))
      onForm.forEach((k) => form.setError(k, { type: 'server', message: byField[k] }))
      // An error for a field this form doesn't show still has to be seen.
      const stray = Object.keys(byField).filter((k) => !fields.includes(k)).map((k) => byField[k])
      if (!onForm.length || stray.length) {
        setStatus({ ok: false, text: stray[0] || e.message || 'Something went wrong. Please try again.' })
      }
    } finally {
      inFlight.current = false
      setBusy(false)
    }
  })

  return { form, onSubmit, status, busy }
}

function Status({ status }) {
  if (!status) return null
  return (
    <div className={`col-12 small d-flex align-items-start gap-2 ${status.ok ? 'text-success' : 'text-danger'}`} role="status">
      {status.ok ? <FiCheckCircle className="flex-shrink-0 mt-1" /> : <FiAlertCircle className="flex-shrink-0 mt-1" />}
      <span>{status.text}</span>
    </div>
  )
}

function Field({ form, name, col = 'col-md-6', rules, textarea, rows = 3, ...rest }) {
  const err = form.formState.errors[name]
  const Tag = textarea ? 'textarea' : 'input'
  return (
    <div className={col}>
      <Tag className={`form-control ${err ? 'is-invalid' : ''}`} aria-label={rest.placeholder}
        rows={textarea ? rows : undefined} {...form.register(name, rules)} {...rest} />
      {err && <div className="invalid-feedback">{err.message}</div>}
    </div>
  )
}

const required = (label) => ({ value: true, message: `${label} is required` })
const max = (n) => ({ value: n, message: `At most ${n} characters` })
const emailRules = {
  required: required('Email'),
  maxLength: max(LIMITS.email),
  pattern: { value: EMAIL, message: 'Enter a valid email address' },
}

export function DemoForm({ submitLabel = 'Request Demo' }) {
  const { form, onSubmit, status, busy } = useLeadForm(publicService.requestDemo,
    ['name', 'email', 'phone', 'companyName', 'message'])
  return (
    <form onSubmit={onSubmit} className="row g-2" noValidate>
      <Field form={form} name="name" placeholder="Name" rules={{ required: required('Name'), maxLength: max(LIMITS.name) }} />
      <Field form={form} name="email" type="email" placeholder="Email" rules={emailRules} />
      <Field form={form} name="phone" placeholder="Phone" rules={{ maxLength: max(LIMITS.phone) }} />
      <Field form={form} name="companyName" placeholder="Company" rules={{ maxLength: max(LIMITS.companyName) }} />
      <Field form={form} name="message" col="col-12" textarea rows={2} placeholder="Message" rules={{ maxLength: max(1000) }} />
      <Status status={status} />
      <div className="col-12 d-flex justify-content-end">
        <Button type="submit" size="sm" loading={busy}>{submitLabel}</Button>
      </div>
    </form>
  )
}

export function ContactForm() {
  const { form, onSubmit, status, busy } = useLeadForm(publicService.contact,
    ['name', 'email', 'phone', 'subject', 'message'])
  return (
    <form onSubmit={onSubmit} className="row g-2" noValidate>
      <Field form={form} name="name" placeholder="Name" rules={{ required: required('Name'), maxLength: max(LIMITS.name) }} />
      <Field form={form} name="email" type="email" placeholder="Email" rules={emailRules} />
      <Field form={form} name="subject" col="col-12" placeholder="Subject" rules={{ maxLength: max(LIMITS.subject) }} />
      <Field form={form} name="message" col="col-12" textarea rows={2} placeholder="Message"
        rules={{ required: required('Message'), maxLength: max(2000) }} />
      <Status status={status} />
      <div className="col-12 d-flex justify-content-end">
        <Button type="submit" size="sm" loading={busy}>Send</Button>
      </div>
    </form>
  )
}

// Subscribing an address that is already on the list gets the same success
// message on purpose: "already subscribed" would let a stranger use this form
// to find out who is on the list. So there is no such case to handle here.
//
// `landing` styles it for the landing page's dark footer instead of Bootstrap.
export function NewsletterForm({ landing = false }) {
  const { form, onSubmit, status, busy } = useLeadForm(({ email }) => publicService.newsletter(email), ['email'])
  const err = form.formState.errors.email
  if (landing) {
    return (
      <form onSubmit={onSubmit} noValidate>
        <div className="lp-newsletter-row">
          <input type="email" className={`lp-input ${err ? 'is-invalid' : ''}`} placeholder="you@company.com"
            aria-label="Email for the newsletter" aria-invalid={!!err} {...form.register('email', emailRules)} />
          <button type="submit" className="lp-btn lp-btn-primary" disabled={busy}>
            {busy ? 'Subscribing…' : 'Subscribe'}
          </button>
        </div>
        {err && <div className="lp-form-msg is-error">{err.message}</div>}
        {status && <div className={`lp-form-msg ${status.ok ? 'is-ok' : 'is-error'}`} role="status">{status.text}</div>}
      </form>
    )
  }
  return (
    <form onSubmit={onSubmit} noValidate>
      <div className="input-group has-validation">
        <input type="email" className={`form-control ${err ? 'is-invalid' : ''}`} placeholder="you@company.com"
          aria-label="Email" {...form.register('email', emailRules)} />
        <Button type="submit" loading={busy}>Subscribe</Button>
        {err && <div className="invalid-feedback">{err.message}</div>}
      </div>
      {status && <div className="row mt-2"><Status status={status} /></div>}
    </form>
  )
}
