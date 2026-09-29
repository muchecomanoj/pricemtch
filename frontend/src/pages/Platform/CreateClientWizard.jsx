import { useState, useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  FiArrowRight, FiArrowLeft, FiMail, FiSend, FiClock, FiEye,
  FiCheck, FiUsers, FiCloud, FiCpu, FiRefreshCw, FiUser,
} from 'react-icons/fi'
import StepBar from '../../components/onboarding/StepBar'
import PageHeader from '../../components/common/PageHeader'
import PricingCard from '../../components/onboarding/PricingCard'
import SuccessScreen from '../../components/onboarding/SuccessScreen'
import LogoUpload from '../../components/onboarding/LogoUpload'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Modal from '../../components/common/Modal'
import { useNotification } from '../../context/NotificationContext'
import { clientService } from '../../services/clientService'
import { ADMIN_STEPS, CLIENT_NEXT_STEPS, INDUSTRIES, CURRENCIES } from '../../mock/onboarding'
import { TIMEZONES, DEFAULT_TIMEZONE, timezoneLabel } from '../../constants'
import { generateCompanyCode } from '../../utils/format'
import { publicService } from '../../services/publicService'
import { normalizePlans, catalogDiscountPercent } from '../../utils/plans'

// Super-admin side of onboarding: Steps 1–3.
const SCREENS = [
  { key: 'details', rail: 0 },
  { key: 'plan', rail: 1 },
  { key: 'sent', rail: 2 },
]

export default function CreateClientWizard() {
  const navigate = useNavigate()
  const { notify } = useNotification()
  const [screen, setScreen] = useState(0)
  const [client, setClient] = useState({})
  const [planCode, setPlanCode] = useState('PRO')
  const [cycle, setCycle] = useState('YEARLY')   // MONTHLY | YEARLY — sent to the client
  const [invite, setInvite] = useState(null)
  const [saving, setSaving] = useState(false)

  // Live plan catalog — prices and any yearly discount come from the backend,
  // so the admin sees exactly what the client will be assigned.
  const plansQuery = useQuery({
    queryKey: ['public', 'plans'],
    queryFn: publicService.plans,
    staleTime: 5 * 60 * 1000,
  })
  const plans = normalizePlans(plansQuery.data)

  const plan = plans.find((p) => p.code === planCode)
  const current = SCREENS[screen]

  const createClient = async () => {
    setSaving(true)
    try {
      // Send ONLY the fields CreateTenantRequest accepts — extras are ignored by
      // the API and just add noise. UI-only fields (industry, trial, quotas) are
      // intentionally excluded; quotas come from the plan.
      const payload = {
        companyName: client.companyName,
        companyCode: client.companyCode,
        companyEmail: client.companyEmail,
        companyPhone: client.companyPhone || null,
        companyAddress: client.companyAddress || null,
        city: client.city || null,
        state: client.state || null,
        country: client.country || null,
        postalCode: client.postalCode || null,
        timezone: client.timezone,
        currency: client.currency,
        website: client.website || null,
        logo: null,
        subscriptionPlan: planCode,          // FREE | BASIC | PRO | ENTERPRISE
        billingCycle: cycle,                 // MONTHLY | YEARLY — client sees this cycle
        adminFirstName: client.adminFirstName,
        adminLastName: client.adminLastName,
        adminEmail: client.adminEmail,
      }
      const created = await clientService.create(payload)
      // The BACKEND generates the encrypted token and emails the activation link.
      // We never see the token — we only reflect whether the email was sent.
      setInvite({
        id: created?.id,
        email: created?.adminEmail || client.adminEmail,
        emailSent: created?.activationEmailSent ?? false,
        companyName: created?.companyName || client.companyName,
      })
      setScreen(2)
    } catch (e) {
      notify.error(e.message || 'Could not create client')
    } finally { setSaving(false) }
  }

  // Rendered inside MainLayout, so the admin keeps the sidebar, navbar and
  // footer — this is a routine admin task, not a standalone onboarding flow.
  return (
    <>
      <PageHeader
        title="Create client"
        subtitle="Set up an organization and send their invitation."
        breadcrumb={[
          { label: 'Home', to: '/dashboard' },
          { label: 'Platform' },
          { label: 'Clients', to: '/platform/clients' },
          { label: 'New' },
        ]}
      />

      <StepBar inline steps={ADMIN_STEPS} currentIndex={current.rail} />

      <AnimatePresence mode="wait">
        <motion.div key={current.key}
          initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -12 }}
          transition={{ duration: 0.25 }}>
          {current.key === 'details' && (
            <ClientDetails initial={client} onNext={(v) => { setClient(v); setScreen(1) }}
              onCancel={() => navigate('/platform/clients')} />
          )}
          {current.key === 'plan' && (
            <AssignPlan planCode={planCode} onSelect={setPlanCode} cycle={cycle} onCycle={setCycle}
              plans={plans} loading={plansQuery.isLoading} client={client} saving={saving}
              onBack={() => setScreen(0)} onSave={createClient} />
          )}
          {current.key === 'sent' && (
            <InvitationSent invite={invite} plan={plan}
              onDone={() => navigate('/platform/clients')} />
          )}
        </motion.div>
      </AnimatePresence>
    </>
  )
}

/* ── Step 1: Create client ───────────────────────────────────── */
function ClientDetails({ initial, onNext, onCancel }) {
  const { register, handleSubmit, watch, setValue, formState: { errors } } = useForm({
    defaultValues: {
      country: 'India', currency: 'USD', timezone: DEFAULT_TIMEZONE, ...initial,
    },
  })
  const [logo, setLogo] = useState(null)
  const [preview, setPreview] = useState(false)
  const values = watch()
  const companyName = watch('companyName')
  const companyCode = watch('companyCode')

  // Company code is auto-generated from the organization name (regenerable).
  useEffect(() => {
    if (companyName && !initial?.companyCode) {
      setValue('companyCode', generateCompanyCode(companyName))
    }
  }, [companyName, setValue, initial?.companyCode])

  return (
    <div>
      <form onSubmit={handleSubmit((v) => onNext({ ...v, logo }))}>
        <Card title="Organization" className="mb-3">
          <div className="row g-3">
            <div className="col-12 col-md-3">
              <label className="form-label">Company logo</label>
              <LogoUpload value={logo} onChange={setLogo} />
            </div>
            <div className="col-12 col-md-9">
              <div className="row g-3">
                <Field col={6} label="Organization name" required err={errors.companyName}
                  reg={register('companyName', { required: 'Required' })} />

                {/* Auto-generated tenant code */}
                <div className="col-12 col-md-6">
                  <label className="form-label d-flex align-items-center gap-2">
                    <span>Company code<span className="text-danger ms-1">*</span></span>
                    <span className="badge text-bg-secondary" style={{ fontSize: '0.62rem' }}>auto</span>
                  </label>
                  <div className="input-group">
                    <input className={`form-control ${errors.companyCode ? 'is-invalid' : ''}`} readOnly
                      {...register('companyCode', { required: 'Required' })} />
                    <button type="button" className="btn btn-light" title="Regenerate"
                      onClick={() => setValue('companyCode', generateCompanyCode(companyName || ''))}>
                      <FiRefreshCw />
                    </button>
                    {errors.companyCode && <div className="invalid-feedback">{errors.companyCode.message}</div>}
                  </div>
                  <div className="form-text">Generated from the organization name.</div>
                </div>

                <Field col={6} label="Company email" type="email" required err={errors.companyEmail}
                  reg={register('companyEmail', { required: 'Required' })} />
                <Field col={6} label="Phone number" reg={register('companyPhone')} />
                <Field col={6} label="Website" reg={register('website')} placeholder="https://" />
                <div className="col-12 col-md-6">
                  <label className="form-label">Industry</label>
                  <select className="form-select" {...register('industry')}>
                    {INDUSTRIES.map((i) => <option key={i}>{i}</option>)}
                  </select>
                  <div className="form-text">For your reference — not stored by the API yet.</div>
                </div>
              </div>
            </div>
          </div>
        </Card>

        <Card title="Address & locale" className="mb-3">
          <div className="row g-3">
            <Field col={12} label="Address" reg={register('companyAddress')} />
            <Field col={3} label="City" reg={register('city')} />
            <Field col={3} label="State" reg={register('state')} />
            <Field col={3} label="Country" reg={register('country')} />
            <Field col={3} label="Postal code" reg={register('postalCode')} />
            <div className="col-12 col-md-8">
              <label className="form-label">Time zone</label>
              <select className="form-select" {...register('timezone')}>
                {TIMEZONES.map((tz) => <option key={tz} value={tz}>{timezoneLabel(tz)}</option>)}
              </select>
            </div>
            <div className="col-12 col-md-4">
              <label className="form-label">Currency</label>
              <select className="form-select" {...register('currency')}>
                {CURRENCIES.map((c) => <option key={c}>{c}</option>)}
              </select>
            </div>
          </div>
        </Card>

        {/* These three are required by the API and are the invitation recipient. */}
        <Card title="Client admin" subtitle="Receives the secure invitation to set their password." className="mb-3">
          <div className="row g-3">
            <Field col={4} label="Admin first name" required err={errors.adminFirstName}
              reg={register('adminFirstName', { required: 'Required' })} />
            <Field col={4} label="Admin last name" required err={errors.adminLastName}
              reg={register('adminLastName', { required: 'Required' })} />
            <Field col={4} label="Admin email" type="email" required err={errors.adminEmail}
              reg={register('adminEmail', { required: 'Required' })} />
          </div>
        </Card>

        <div className="d-flex flex-wrap gap-2 justify-content-between">
          <Button variant="light" type="button" onClick={onCancel}>Cancel</Button>
          <div className="d-flex gap-2">
            <Button variant="light" type="button" icon={FiEye} onClick={() => setPreview(true)}>Preview invitation</Button>
            <Button type="submit">Continue <FiArrowRight /></Button>
          </div>
        </div>
      </form>

      <Modal show={preview} title="Invitation preview" onClose={() => setPreview(false)} size="lg"
        footer={<Button onClick={() => setPreview(false)}>Close</Button>}>
        <EmailPreview values={{ ...values, companyCode }} />
      </Modal>
    </div>
  )
}

const Field = ({ col = 6, label, reg, err, required, ...rest }) => (
  <div className={`col-12 col-md-${col}`}>
    <label className="form-label">{label}{required && <span className="text-danger ms-1">*</span>}</label>
    <input className={`form-control ${err ? 'is-invalid' : ''}`} {...reg} {...rest} />
    {err && <div className="invalid-feedback">{err.message}</div>}
  </div>
)

// A styled mock of the email the client will receive.
function EmailPreview({ values }) {
  return (
    <div className="rounded-3 p-4" style={{ background: 'var(--app-bg)', border: '1px solid var(--border-soft)' }}>
      <div className="text-center mb-3">
        <div className="d-inline-grid rounded-3 text-white fw-bold mb-2"
          style={{ width: 44, height: 44, placeItems: 'center', background: 'var(--grad-primary)' }}>PI</div>
        <h5 className="fw-bold mb-1">You’re invited to Price Intelligence</h5>
        <p className="text-muted small mb-0">Hi {values.adminFirstName || 'there'},</p>
      </div>
      <p className="small text-muted">
        <strong>{values.companyName || 'Your organization'}</strong>
        {values.companyCode && <> (<code>{values.companyCode}</code>)</>} has been set up on Price Intelligence.
        Click below to verify your email, create a password and activate your subscription.
      </p>
      <div className="text-center my-3">
        <span className="btn btn-primary btn-sm px-4">Accept invitation</span>
      </div>
      <p className="text-muted text-center" style={{ fontSize: 11 }}>
        This secure link expires in 7 days. If you didn’t expect this, ignore this email.
      </p>
    </div>
  )
}

/* ── Step 2: Assign plan ─────────────────────────────────────── */
function AssignPlan({ planCode, onSelect, cycle, onCycle, plans, loading, client, saving, onBack, onSave }) {
  // cycle is MONTHLY | YEARLY (lifted to the wizard so it's sent to the client);
  // PricingCard wants monthly | yearly.
  const cardCycle = cycle === 'YEARLY' ? 'yearly' : 'monthly'
  const yearlyDiscount = catalogDiscountPercent(plans)
  return (
    <div>
      <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-4">
        <div>
          <h3 className="fw-bold mb-1" style={{ fontFamily: 'Plus Jakarta Sans' }}>Assign a subscription plan</h3>
          <p className="text-muted mb-0">Choose the plan and billing cycle for <strong>{client.companyName}</strong>. This is what they&apos;ll see when they activate.</p>
        </div>
        {/* Monthly / Yearly toggle — the chosen cycle is sent to the client.
            The Save X% badge reflects the real backend discount, if any. */}
        <div className="btn-group">
          {[['MONTHLY', 'Monthly'], ['YEARLY', 'Yearly']].map(([c, label]) => (
            <button key={c} className={`btn btn-sm ${cycle === c ? 'btn-primary' : 'btn-light'}`}
              onClick={() => onCycle(c)}>
              {label}{c === 'YEARLY' && yearlyDiscount > 0 && (
                <span className="badge text-bg-success ms-2">Save {yearlyDiscount}%</span>
              )}
            </button>
          ))}
        </div>
      </div>

      <div className="row g-3 mb-4">
        {loading
          ? Array.from({ length: 4 }).map((_, i) => (
            <div className="col-12 col-sm-6 col-xl-3" key={i}><div className="skeleton" style={{ height: 320 }} /></div>
          ))
          : plans.map((p) => (
            <div className={`col-12 col-sm-6 ${plans.length > 3 ? 'col-xl-3' : 'col-xl-4'}`} key={p.code}>
              <PricingCard plan={p} cycle={cardCycle} selected={planCode === p.code} onSelect={onSelect} />
            </div>
          ))}
      </div>

      <div className="d-flex justify-content-between">
        <Button variant="light" icon={FiArrowLeft} onClick={onBack}>Back</Button>
        <Button icon={FiSend} loading={saving} onClick={onSave}>Save &amp; send invitation</Button>
      </div>
    </div>
  )
}

/* ── Step 3: Invitation sent ─────────────────────────────────── */
function InvitationSent({ invite, plan, onDone }) {
  const { notify } = useNotification()
  const [resending, setResending] = useState(false)
  const [sent, setSent] = useState(invite?.emailSent ?? false)
  if (!invite) return null

  // Re-send the activation email via the real backend endpoint (regenerates token).
  const resend = async () => {
    if (!invite.id) return notify.error('Client id missing — reopen the client to resend.')
    setResending(true)
    try {
      const res = await clientService.resendActivation(invite.id)
      setSent(res?.activationEmailSent ?? true)
      notify.success('Invitation email resent')
    } catch (e) { notify.error(e.message || 'Could not resend invitation') }
    finally { setResending(false) }
  }

  return (
    <Card className="p-2">
      <SuccessScreen
        confetti={sent}
        title={sent ? 'Invitation sent' : 'Client created'}
        message={sent
          ? <>A secure activation link was emailed to <strong>{invite.email}</strong>.</>
          : <>The client was created, but the activation email hasn’t gone out yet.</>}
      >
        {/* Email delivery status — the truth, from the backend's activationEmailSent flag */}
        <div className={`d-flex align-items-center gap-3 p-3 rounded-3 mx-auto mt-2 text-start`}
          style={{ maxWidth: 560, background: sent ? 'rgba(34,197,94,0.06)' : 'rgba(245,158,11,0.08)',
                   border: `1px solid ${sent ? 'rgba(34,197,94,0.25)' : 'rgba(245,158,11,0.3)'}` }}>
          <div className={`icon-box ${sent ? 'icon-grad-success' : 'icon-grad-warning'} flex-shrink-0`}><FiMail /></div>
          <div>
            <div className="fw-semibold">
              {sent ? 'Activation email sent' : 'Activation email not sent'}
            </div>
            <div className="text-muted small">
              {sent
                ? <>To <strong>{invite.email}</strong>. It contains the encrypted link + verification code and expires in 7 days.</>
                : <>Check the mail server, then use <strong>Resend</strong> below. The client can’t onboard until they receive it.</>}
            </div>
          </div>
        </div>

        <div className="row g-2 text-start mx-auto mt-2" style={{ maxWidth: 560 }}>
          <Info icon={FiMail} label="Recipient" value={invite.email} />
          <Info icon={FiCheck} label="Company" value={invite.companyName} />
          <Info icon={FiCheck} label="Assigned plan" value={plan?.name} />
          <Info icon={FiClock} label="Link expires" value="in 7 days" />

          {/* Quotas granted */}
          <div className="col-12 mt-2">
            <div className="d-flex flex-wrap gap-3 justify-content-center p-3 rounded-3" style={{ background: 'var(--app-bg)' }}>
              <Quota icon={FiUsers} label={`${plan?.users} users`} />
              <Quota icon={FiCloud} label={plan?.storage} />
              <Quota icon={FiCpu} label={plan?.aiCredits} />
            </div>
          </div>
        </div>

        {/* What the CLIENT does next — relevant here, once the handoff happens. */}
        <div className="mt-4 p-3 rounded-3 text-start" style={{ background: 'var(--app-bg)', maxWidth: 560, margin: '1.5rem auto 0' }}>
          <div className="d-flex align-items-center gap-2 mb-2">
            <FiUser size={13} className="text-primary" />
            <span className="fw-semibold small">What {invite.email.split('@')[0]} does next</span>
          </div>
          <div className="d-flex flex-wrap gap-2">
            {CLIENT_NEXT_STEPS.map((s, i) => (
              <span key={s.key} className="badge text-bg-secondary d-inline-flex align-items-center gap-1"
                style={{ fontWeight: 500 }}>
                <span className="text-muted">{i + 1}</span> {s.label}
              </span>
            ))}
          </div>
          <div className="text-muted mt-2" style={{ fontSize: 11 }}>
            Nothing more is needed from you — they complete these from the invitation link.
          </div>
        </div>

        <div className="d-flex flex-wrap justify-content-center gap-2 mt-4">
          <Button variant="light" icon={FiSend} loading={resending} onClick={resend}>Resend invitation</Button>
          <Button onClick={onDone}>Back to clients</Button>
        </div>
      </SuccessScreen>
    </Card>
  )
}

const Info = ({ icon: Icon, label, value }) => (
  <div className="col-12 col-sm-6">
    <div className="p-2 rounded-3 d-flex align-items-center gap-2" style={{ background: 'var(--app-bg)' }}>
      <Icon className="text-primary flex-shrink-0" size={15} />
      <div className="overflow-hidden">
        <div className="text-muted" style={{ fontSize: 11 }}>{label}</div>
        <div className="fw-semibold small text-truncate">{value}</div>
      </div>
    </div>
  </div>
)

const Quota = ({ icon: Icon, label }) => (
  <span className="d-flex align-items-center gap-2 small text-muted">
    <Icon size={14} className="text-primary" /> {label}
  </span>
)
