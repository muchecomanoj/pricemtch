import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { FiArrowRight, FiArrowLeft, FiShield, FiCheck } from 'react-icons/fi'
import { Input } from '../../../components/onboarding/StepFields'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'
import { useNotification } from '../../../context/NotificationContext'
import { signupService } from '../../../services/signupService'
import { CURRENCIES } from '../../../mock/onboarding'
import { TIMEZONES, DEFAULT_TIMEZONE, timezoneLabel } from '../../../constants'
import { formatCurrency } from '../../../utils/format'
import { isFreePlan } from '../../../utils/plans'
import { Header } from './Header'
import { errorMessage } from '../../../services/apiError'

/* ── Step 2: Profile → POST /register (returns the token, emails the code) ── */
// Fields map to SelfRegisterRequest. The company code is assigned by the backend.
export default function Profile({ plan, cycle, initial, onRegistered, onBack }) {
  const { notify } = useNotification()
  const { register, handleSubmit, formState: { errors } } = useForm({
    defaultValues: { country: 'India', currency: 'USD', timezone: DEFAULT_TIMEZONE, ...initial },
  })
  const [busy, setBusy] = useState(false)

  const onSubmit = async (v) => {
    setBusy(true)
    try {
      const payload = {
        planCode: plan.code,
        billingCycle: cycle,                // MONTHLY | YEARLY
        companyName: v.companyName,
        email: v.email,                     // receives the verification code
        contactPerson: v.contactPerson || null,
        companyPhone: v.companyPhone || null,
        companyAddress: v.companyAddress || null,
        city: v.city || null,
        state: v.state || null,
        country: v.country || null,
        postalCode: v.postalCode || null,
        timezone: v.timezone || null,
        currency: v.currency || null,
        website: v.website || null,
        industry: v.industry || null,
        gstin: v.gstin || null,
        taxId: v.taxId || null,
      }
      const res = await signupService.register(payload)
      notify.success(`We sent a code to ${v.email}`)
      onRegistered({ token: res.token, email: res.email || v.email, profile: v })
    } catch (e) { notify.error(errorMessage(e, 'Could not start registration')) }
    finally { setBusy(false) }
  }

  return (
    <div>
      <Header title="Tell us about your organization" sub="This creates your workspace. We’ll email a code to verify your address." />
      <form onSubmit={handleSubmit(onSubmit)}>
        {/* Form on the left, a running summary of the plan already chosen on
            the right — the same detail that used to sit in the footnote under
            the buttons, given the weight it deserves. */}
        <div className="ob-form-grid">
          <div>
            <Card className="ob-section mb-3">
              <SectionHead n="01" title="Organization"
                sub="How your workspace is identified across the platform." />
              <div className="row g-3">
                <Input col={6} label="Organization name" required err={errors.companyName}
                  reg={register('companyName', { required: 'Required' })} />
                <Input col={6} label="Website" reg={register('website')} placeholder="https://" />
                <Input col={6} label="Tax ID (EIN)" reg={register('taxId')} placeholder="e.g. 12-3456789" />
                <Input col={6} label="Phone" reg={register('companyPhone')} />
                <Input col={6} label="Industry" reg={register('industry')} />
                <Input col={12} label="Address" reg={register('companyAddress')} />
                <Input col={3} label="City" reg={register('city')} />
                <Input col={3} label="State" reg={register('state')} />
                <Input col={3} label="Country" reg={register('country')} />
                <Input col={3} label="Postal code" reg={register('postalCode')} />
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

            <Card className="ob-section mb-3">
              <SectionHead n="02" title="Your admin account"
                sub="You’ll be the first administrator of this workspace." />
              <div className="row g-3">
                <Input col={6} label="Contact person" required err={errors.contactPerson}
                  reg={register('contactPerson', { required: 'Required' })} />
                <Input col={6} label="Work email" type="email" required err={errors.email}
                  reg={register('email', { required: 'Required' })} />
              </div>
            </Card>

            <div className="d-flex justify-content-between">
              <Button variant="light" type="button" icon={FiArrowLeft} onClick={onBack}>Back</Button>
              <Button type="submit" loading={busy}>Send verification code <FiArrowRight /></Button>
            </div>
          </div>

          <aside className="ob-aside">
            <PlanSummary plan={plan} cycle={cycle} />
          </aside>
        </div>
      </form>
    </div>
  )
}

// Ruled section heading — a numbered plate, a title and a hairline, so a long
// form reads as a sequence of steps rather than one undifferentiated wall.
const SectionHead = ({ n, title, sub }) => (
  <div className="ob-section-head">
    <span className="ob-num">{n}</span>
    <div>
      <h6 className="mb-0 fw-bold">{title}</h6>
      {sub && <div className="text-muted small mt-1">{sub}</div>}
    </div>
  </div>
)

// Read-only recap of the plan picked on step 1. Every value here already
// existed on `plan`/`cycle`; nothing new is fetched or decided.
function PlanSummary({ plan, cycle }) {
  if (!plan) return null
  const yearly = cycle === 'YEARLY'
  const price = plan.price?.[yearly ? 'yearly' : 'monthly'] ?? plan.price?.monthly
  const isFree = isFreePlan(plan)

  return (
    <div className="ob-summary">
      <span className="ob-eyebrow">Your plan</span>

      <div className="d-flex align-items-center justify-content-between gap-2 mt-2">
        <span className="ob-summary__name">{plan.name}</span>
        {/* No cycle on Free: "Yearly" there reads as free for a year. */}
        {!isFree && <span className="ob-summary__cycle">{yearly ? 'Yearly' : 'Monthly'}</span>}
      </div>

      <div className="ob-summary__price">
        {isFree ? 'Free' : formatCurrency(price, plan.currency)}
        {!isFree && <span>{yearly ? '/year' : '/month'}</span>}
      </div>
      {plan.tagline && <p className="ob-summary__tagline">{plan.tagline}</p>}

      {plan.features?.length > 0 && (
        <>
          <div className="ob-summary__rule" />
          <ul className="ob-summary__list">
            {plan.users != null && <li><FiCheck size={14} />{plan.users} users</li>}
            {plan.features.map((f) => <li key={f}><FiCheck size={14} />{f}</li>)}
          </ul>
        </>
      )}

      <div className="ob-summary__rule" />
      <div className="ob-summary__note">
        <FiShield size={14} />
        <span>
          {isFree
            ? `Free${plan.trialDays > 0 ? ` — ${plan.trialDays} days` : ''}, no card needed.`
            : plan.trialDays > 0
              ? `${plan.trialDays}-day free trial, then billed.`
              : 'Billed today, on a secure payment page.'}
        </span>
      </div>
    </div>
  )
}
