import { useState, useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  FiArrowRight, FiArrowLeft, FiLock, FiEye, FiEyeOff, FiMail, FiClock, FiShield,
  FiCreditCard, FiCheck, FiUsers, FiCloud, FiCpu,
} from 'react-icons/fi'
import OnboardingStepper from '../../components/onboarding/OnboardingStepper'
import PricingCard from '../../components/onboarding/PricingCard'
import PasswordStrength, { isStrongPassword } from '../../components/onboarding/PasswordStrength'
import OptionCard from '../../components/onboarding/OptionCard'
import SuccessScreen from '../../components/onboarding/SuccessScreen'
import SubscriptionSummary, { priceBreakdown } from '../../components/onboarding/SubscriptionSummary'
import LogoUpload from '../../components/onboarding/LogoUpload'
import OtpInput from '../../components/common/OtpInput'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Loader from '../../components/common/Loader'
import { useNotification } from '../../context/NotificationContext'
import { onboardingService } from '../../services/onboardingService'
import {
  CLIENT_STEPS, BILLING_CYCLES, CURRENCIES,
} from '../../mock/onboarding'
import { TIMEZONES, DEFAULT_TIMEZONE, timezoneLabel } from '../../constants'
import { formatCurrency } from '../../utils/format'
import { publicService } from '../../services/publicService'
import { usePaymentConfirm } from '../../hooks/usePaymentConfirm'
import { normalizePlans, catalogDiscountPercent, isFreePlan } from '../../utils/plans'

// The activation payload names the assigned plan/cycle differently depending on
// backend version, so accept any of these rather than silently falling back.
// The live contract uses assignedPlan / assignedBillingCycle.
const readAssignedPlan = (inv) =>
  inv?.assignedPlan || inv?.subscriptionPlan || inv?.planCode || inv?.plan || ''
const readAssignedCycle = (inv) =>
  inv?.assignedBillingCycle || inv?.billingCycle || inv?.subscriptionBillingCycle || ''

// Wizard screens -> which CLIENT_STEPS milestone is "current".
// (The admin's create/plan/invite steps are deliberately not shown here — the
// client didn't perform them.)
const SCREENS = [
  { key: 'welcome', step: 'verified' },
  { key: 'otp', step: 'verified' },
  { key: 'password', step: 'password' },
  { key: 'profile', step: 'profile' },
  { key: 'review', step: 'review' },
  { key: 'billing', step: 'billing' },
  { key: 'payment', step: 'payment' },
  { key: 'done', step: 'active' },
]
// A free plan has no billing cycle and nothing to pay: Review Plan activates
// the workspace directly, so those two steps aren't shown — the same as
// self sign-up on Free.
const FREE_STEPS = CLIENT_STEPS.filter((s) => !['billing', 'payment'].includes(s.key))

export default function ClientOnboarding() {
  const [params] = useSearchParams()
  const location = useLocation()
  const token = params.get('token') || ''
  const navigate = useNavigate()

  // Return leg from the hosted (Stripe) checkout. The provider redirects the
  // browser to /onboarding/success (or our successUrl …&paid=1); the invitation
  // token is now consumed — so re-running the wizard would just hit the "invalid
  // link" page. Show the success state and confirm the payment (public, no auth)
  // so activation is instant instead of waiting on the ~60s reconciler.
  const paidReturn = /\/onboarding\/success/.test(location.pathname)
    || params.get('paid') === '1' || params.get('redirect_status') === 'succeeded'

  usePaymentConfirm({ active: paidReturn, sessionId: params.get('session_id') })

  const [screen, setScreen] = useState(0)
  const [invite, setInvite] = useState(null)
  const [invalid, setInvalid] = useState(false)
  // Collected across steps. `otpCode` is carried into set-password (backend re-checks it).
  // planCode starts empty: defaulting to a specific plan here would show the
  // client a plan their administrator never assigned.
  const [data, setData] = useState({ planCode: '', cycle: 'YEARLY', otpCode: '', profile: {}, activation: null })

  useEffect(() => {
    if (paidReturn) return   // don't re-validate a token the checkout already consumed
    onboardingService.validate(token)
      .then((inv) => {
        if (inv?.valid === false) { setInvalid(true); return }
        setInvite(inv)
        setData((d) => ({
          ...d,
          planCode: readAssignedPlan(inv) || d.planCode,
          cycle: readAssignedCycle(inv) || d.cycle,
        }))
      })
      .catch(() => setInvalid(true))
  }, [token, paidReturn])

  // The backend may return loginUrl as an absolute URL (http://…/login) or a
  // relative path. React Router's navigate() only handles in-app paths, so an
  // absolute URL must go through window.location; a relative one through router.
  const goToLogin = (loginUrl) => {
    const url = loginUrl || '/login'
    if (/^https?:\/\//i.test(url)) window.location.assign(url)
    else navigate(url)
  }

  // Live plan catalog, so names/prices match what the admin assigned.
  const plansQuery = useQuery({
    queryKey: ['public', 'plans'],
    queryFn: publicService.plans,
    staleTime: 5 * 60 * 1000,
  })
  const plans = normalizePlans(plansQuery.data)

  const next = () => setScreen((s) => Math.min(s + 1, SCREENS.length - 1))
  const back = () => setScreen((s) => Math.max(s - 1, 0))
  const patch = (p) => setData((d) => ({ ...d, ...p }))

  // Resolve strictly against the assigned code — never fall back to another
  // plan, or the client is shown (and charged for) something else entirely.
  const plan = plans.find((p) => p.code === data.planCode) || null
  const current = SCREENS[screen]
  const free = !!plan && isFreePlan(plan)
  const steps = free ? FREE_STEPS : CLIENT_STEPS
  const goTo = (key) => setScreen(SCREENS.findIndex((s) => s.key === key))
  // Short, card-sized steps — centred rather than pinned to the top.
  const isNarrow = ['welcome', 'otp', 'password'].includes(current.key)

  if (paidReturn) return <PaidReturn onGo={() => navigate('/login')} />
  if (invalid) return <ExpiredLink onBack={() => navigate('/login')} />
  if (!invite) return <div className="ob-shell"><div className="ob-main"><Loader label="Verifying your invitation…" /></div></div>

  return (
    <div className="ob-shell">
      <OnboardingStepper
        steps={steps}
        currentIndex={Math.max(0, steps.findIndex((s) => s.key === current.step))}
        completed={current.key === 'done'}
        title="Set up your workspace"
        subtitle={invite.companyName}
      />

      <div className="ob-main">
        {/* Short steps get vertically centred; tall forms stay top-aligned. */}
        <div className={`ob-body ${isNarrow ? 'ob-center' : ''}`}>
          <AnimatePresence mode="wait">
            <motion.div
              key={current.key}
              className={isNarrow ? '' : 'ob-content'}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
              transition={{ duration: 0.25, ease: 'easeOut' }}
            >
              {isNarrow ? (
                <div className="ob-split">
                  <OnboardAside stepKey={current.key} invite={invite} plan={plan} />
                  <div className="ob-split-card">
                    {current.key === 'welcome' && <Welcome invite={invite} plan={plan} onNext={next} />}
                    {current.key === 'otp' && (
                      <VerifyEmail invite={invite} token={token} onBack={back}
                        onVerified={(code) => { patch({ otpCode: code }); next() }} />
                    )}
                    {current.key === 'password' && (
                      <CreatePassword token={token} code={data.otpCode} onNext={next} onBack={back} />
                    )}
                  </div>
                </div>
              ) : (<>
              {current.key === 'profile' && (
                <CompleteProfile invite={invite} token={token} initial={data.profile}
                  onSave={(p) => { patch({ profile: p }); next() }} onBack={back} />
              )}
              {current.key === 'review' && (
                <ReviewPlan plan={plan} plans={plans} cycle={data.cycle} token={token}
                  onChangePlan={(c) => patch({ planCode: c })}
                  onChangeCycle={(c) => patch({ cycle: c })}
                  onNext={next} onBack={back}
                  onActivated={(activation) => { patch({ activation }); goTo('done') }}
                  onNeedsPayment={() => goTo('billing')} />
              )}
              {current.key === 'billing' && (
                <ChooseBilling plan={plan} value={data.cycle} token={token}
                  onChange={(c) => patch({ cycle: c })}
                  onNext={() => next()} onBack={back} />
              )}
              {current.key === 'payment' && (
                <Payment plan={plan} cycle={data.cycle} token={token}
                  onPaid={(activation) => { patch({ activation }); next() }} onBack={back} />
              )}
              {current.key === 'done' && (
                <Activated activation={data.activation} plan={plan} cycle={data.cycle} free={free}
                  email={invite.email} onGo={() => goToLogin(data.activation?.loginUrl)} />
              )}
              </>)}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </div>
  )
}

// Soft info panel shown beside the short-step cards so they fill the width
// instead of floating in a wide empty band. Copy adapts to the current step.
function OnboardAside({ stepKey, invite, plan }) {
  const COPY = {
    welcome: { icon: FiCheck, title: 'Welcome to Price Intelligence', text: 'Let’s get your workspace ready — it only takes a few minutes.' },
    otp: { icon: FiMail, title: 'Verify it’s really you', text: 'We’ve emailed a 6-digit code to keep your account secure.' },
    password: { icon: FiShield, title: 'Secure your account', text: 'Choose a strong password. You can enable 2-factor auth later in settings.' },
  }
  const c = COPY[stepKey] || COPY.welcome
  const Icon = c.icon

  return (
    <aside className="ob-split-aside">
      <div className="position-relative">
        <div className="d-flex align-items-center gap-2 mb-4">
          <div className="d-grid rounded-3 fw-bold" style={{ width: 36, height: 36, placeItems: 'center', background: 'rgba(255,255,255,0.16)' }}>PI</div>
          <span className="fw-semibold" style={{ fontFamily: 'Plus Jakarta Sans' }}>{invite.companyName}</span>
        </div>

        <div className="icon-box mb-3" style={{ background: 'rgba(255,255,255,0.16)' }}><Icon /></div>
        <h4 className="fw-bold mb-2" style={{ fontFamily: 'Plus Jakarta Sans' }}>{c.title}</h4>
        <p className="mb-4" style={{ color: 'rgba(255,255,255,0.82)' }}>{c.text}</p>
      </div>

      {/* Plan quotas — reassurance about what they're getting */}
      <div className="mt-auto position-relative">
        {/* Only shown once a plan is known — quotas the API doesn't send are skipped. */}
        {plan && (
          <div className="rounded-3 p-3" style={{ background: 'rgba(255,255,255,0.1)' }}>
            <div className="d-flex justify-content-between align-items-center mb-2">
              <span className="small" style={{ color: 'rgba(255,255,255,0.75)' }}>Your plan</span>
              <span className="badge" style={{ background: 'rgba(255,255,255,0.22)' }}>{plan.name}</span>
            </div>
            {[
              plan.users != null && [FiUsers, `${plan.users} users`],
              plan.storage && [FiCloud, `${plan.storage} storage`],
              plan.aiCredits && [FiCpu, `${plan.aiCredits} AI credits`],
            ].filter(Boolean).map(([I, label]) => (
              <div key={label} className="d-flex align-items-center gap-2 py-1 small">
                <I size={14} style={{ opacity: 0.85 }} /> {label}
              </div>
            ))}
          </div>
        )}
        <div className="d-flex align-items-center gap-2 mt-3 small" style={{ color: 'rgba(255,255,255,0.7)' }}>
          <FiShield size={13} /> Secured with 256-bit encryption
        </div>
      </div>
    </aside>
  )
}

/* ── Step 4: Welcome ─────────────────────────────────────────── */
function Welcome({ invite, plan, onNext }) {
  const expires = new Date(invite.expiresAt)
  return (
    <Card className="text-center p-2">
      <motion.div className="mx-auto mb-3 d-grid rounded-4 text-white fw-bold"
        initial={{ scale: 0.7, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}
        style={{ width: 72, height: 72, placeItems: 'center', background: 'var(--grad-primary)', fontSize: 24 }}>
        {invite.companyName?.[0] || 'A'}
      </motion.div>
      <h3 className="fw-bold mb-1">Welcome, {invite.companyName}</h3>
      <p className="text-muted mb-4">
        You’ve been invited to set up your workspace. This takes about 3 minutes.
      </p>

      <div className="row g-2 text-start mb-4">
        <Detail label="Company code" value={invite.companyCode} />
        <Detail label="Contact" value={invite.contactPerson} />
        <Detail label="Email" value={invite.email} />
        {plan && <Detail label="Assigned plan" value={plan.name} badge={plan.color} />}
      </div>

      <div className="alert alert-warning py-2 small d-inline-flex align-items-center gap-2">
        <FiClock /> Invitation expires {expires.toLocaleDateString()} at {expires.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
      </div>

      <Button className="w-100 justify-content-center py-2 mt-2" icon={FiArrowRight} onClick={onNext}>
        Continue
      </Button>
    </Card>
  )
}

function Detail({ label, value, badge }) {
  return (
    <div className="col-6">
      <div className="p-2 rounded-3" style={{ background: 'var(--app-bg)' }}>
        <div className="text-muted" style={{ fontSize: 11 }}>{label}</div>
        {badge
          ? <span className={`badge text-bg-${badge}`}>{value}</span>
          : <div className="fw-semibold small text-truncate">{value}</div>}
      </div>
    </div>
  )
}

/* ── Step 5: Email verification ──────────────────────────────── */
// The verified code is emitted upward — set-password re-checks it server-side.
function VerifyEmail({ invite, token, onVerified, onBack }) {
  const { notify } = useNotification()
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (value) => {
    const otp = value ?? code
    if (otp.length !== 6) return notify.warn('Enter the 6-digit code')
    setBusy(true)
    try {
      await onboardingService.verifyCode(token, otp)
      notify.success('Email verified')
      onVerified(otp)
    } catch (e) { notify.error(e.message || 'Verification failed'); setCode('') }
    finally { setBusy(false) }
  }

  return (
    <Card className="text-center p-2">
      <div className="icon-box icon-grad-primary mx-auto mb-3"><FiMail size={22} /></div>
      <h4 className="fw-bold mb-1">Verify your email</h4>
      <p className="text-muted mb-4">
        We sent a 6-digit code to <span className="fw-semibold text-body">{invite.email}</span>
      </p>

      <OtpInput className="otp-light mb-4" value={code} onChange={setCode} onComplete={submit} disabled={busy} />

      <div className="d-flex gap-2">
        <Button variant="light" type="button" icon={FiArrowLeft} onClick={onBack} disabled={busy}>Back</Button>
        <Button className="flex-grow-1 justify-content-center py-2" loading={busy} onClick={() => submit()}>
          Verify email
        </Button>
      </div>
    </Card>
  )
}

/* ── Step 6: Create password ─────────────────────────────────── */
function CreatePassword({ token, code, onNext, onBack }) {
  const { notify } = useNotification()
  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm()
  const [show, setShow] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const pw = watch('password') || ''

  const onSubmit = async (v) => {
    if (!isStrongPassword(v.password)) return notify.warn('Password does not meet all requirements')
    try { await onboardingService.setPassword(token, code, v.password); notify.success('Password created'); onNext() }
    catch (e) { notify.error(e.message || 'Could not set password') }
  }

  return (
    <Card className="p-2">
      <div className="text-center mb-4">
        <div className="icon-box icon-grad-success mx-auto mb-3"><FiShield size={22} /></div>
        <h4 className="fw-bold mb-1">Create your password</h4>
        <p className="text-muted mb-0">Secure your account with a strong password.</p>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="mb-3">
          <label className="form-label">Password</label>
          <div className="input-group">
            <span className="input-group-text"><FiLock /></span>
            <input type={show ? 'text' : 'password'} className="form-control"
              {...register('password', { required: true })} />
            <button type="button" className="input-group-text cursor-pointer" onClick={() => setShow((s) => !s)}>
              {show ? <FiEyeOff /> : <FiEye />}
            </button>
          </div>
        </div>

        <div className="mb-3"><PasswordStrength value={pw} /></div>

        <div className="mb-4">
          <label className="form-label">Confirm password</label>
          <div className="input-group">
            <span className="input-group-text"><FiLock /></span>
            <input type={showConfirm ? 'text' : 'password'} className={`form-control ${errors.confirm ? 'is-invalid' : ''}`}
              {...register('confirm', { validate: (v) => v === pw || 'Passwords do not match' })} />
            <button type="button" className="input-group-text cursor-pointer" onClick={() => setShowConfirm((s) => !s)}>
              {showConfirm ? <FiEyeOff /> : <FiEye />}
            </button>
            {errors.confirm && <div className="invalid-feedback">{errors.confirm.message}</div>}
          </div>
        </div>

        <div className="d-flex gap-2">
          <Button variant="light" type="button" icon={FiArrowLeft} onClick={onBack}>Back</Button>
          <Button type="submit" className="flex-grow-1 justify-content-center" loading={isSubmitting}
            disabled={!isStrongPassword(pw)}>
            Continue <FiArrowRight />
          </Button>
        </div>
      </form>
    </Card>
  )
}

/* ── Step 7: Complete profile ────────────────────────────────── */
// Field names match CompleteProfileRequest exactly (gstin, taxId, postalCode…).
function CompleteProfile({ invite, token, initial, onSave, onBack }) {
  const { notify } = useNotification()
  const { register, handleSubmit, formState: { errors } } = useForm({
    defaultValues: {
      companyName: invite.companyName, country: 'India',
      currency: 'USD', timezone: DEFAULT_TIMEZONE,
      ...initial,
    },
  })
  const [logo, setLogo] = useState(null)
  const [busy, setBusy] = useState(false)

  const onSubmit = async (v) => {
    setBusy(true)
    try {
      await onboardingService.completeProfile(token, v)
      notify.success('Profile saved')
      onSave({ ...v, logo })
    } catch (e) { notify.error(e.message || 'Could not save profile') }
    finally { setBusy(false) }
  }

  return (
    <div>
      <Header title="Complete your organization profile" sub="This helps us tailor pricing, tax and reporting." />
      <form onSubmit={handleSubmit(onSubmit)}>
        <Card title="Organization information" className="mb-3">
          <div className="row g-3">
            <div className="col-12 col-md-3">
              <label className="form-label">Company logo</label>
              <LogoUpload value={logo} onChange={setLogo} />
            </div>
            <div className="col-12 col-md-9">
              <div className="row g-3">
                <Input col={6} label="Organization name" required reg={register('companyName', { required: 'Required' })} err={errors.companyName} />
                <Input col={6} label="Website" reg={register('website')} placeholder="https://" />
                <Input col={6} label="Tax ID (EIN)" reg={register('taxId')} placeholder="e.g. 12-3456789" />
                <Input col={12} label="Address" reg={register('companyAddress')} />
                <Input col={6} label="Phone" reg={register('companyPhone')} />
                <Input col={6} label="Country" reg={register('country')} />
              </div>
            </div>
            <Input col={3} label="State" reg={register('state')} />
            <Input col={3} label="City" reg={register('city')} />
            <Input col={3} label="Postal code" reg={register('postalCode')} />
          </div>
        </Card>

        <Card title="Preferences" className="mb-3">
          <div className="row g-3">
            <Select col={4} label="Currency" reg={register('currency')} options={CURRENCIES} />
            <div className="col-12 col-md-8">
              <label className="form-label">Time zone</label>
              <select className="form-select" {...register('timezone')}>
                {TIMEZONES.map((tz) => <option key={tz} value={tz}>{timezoneLabel(tz)}</option>)}
              </select>
            </div>
          </div>
        </Card>

        <div className="d-flex flex-wrap gap-2 justify-content-between">
          <Button variant="light" type="button" icon={FiArrowLeft} onClick={onBack}>Back</Button>
          <Button type="submit" loading={busy}>Continue <FiArrowRight /></Button>
        </div>
      </form>
    </div>
  )
}

const Input = ({ col = 6, label, reg, err, required, ...rest }) => (
  <div className={`col-12 col-md-${col}`}>
    <label className="form-label">{label}{required && <span className="text-danger ms-1">*</span>}</label>
    <input className={`form-control ${err ? 'is-invalid' : ''}`} {...reg} {...rest} />
    {err && <div className="invalid-feedback">{err.message}</div>}
  </div>
)

const Select = ({ col = 6, label, reg, options }) => (
  <div className={`col-12 col-md-${col}`}>
    <label className="form-label">{label}</label>
    <select className="form-select" {...reg}>{options.map((o) => <option key={o}>{o}</option>)}</select>
  </div>
)

const Header = ({ title, sub }) => (
  <div className="mb-4">
    <h3 className="fw-bold mb-1" style={{ fontFamily: 'Plus Jakarta Sans' }}>{title}</h3>
    <p className="text-muted mb-0">{sub}</p>
  </div>
)

/* ── Step 8: Review plan ─────────────────────────────────────── */
function ReviewPlan({ plan, plans, cycle, token, onChangePlan, onChangeCycle, onNext, onBack, onActivated, onNeedsPayment }) {
  const { notify } = useNotification()
  // With no assigned plan resolved, open straight into the picker — showing an
  // arbitrary plan as "assigned to your organization" would be a lie.
  const [choosing, setChoosing] = useState(!plan)
  const [busy, setBusy] = useState(false)
  const cardCycle = cycle === 'YEARLY' ? 'yearly' : 'monthly'
  const yearlyDiscount = catalogDiscountPercent(plans)
  const free = !!plan && isFreePlan(plan)

  // Free: confirm the plan, then call checkout — on a free plan that is what
  // activates the workspace (the backend answers mode FREE, nothing to pay).
  // Should the server still want payment (the plan list here was out of
  // date), carry on through the normal billing steps instead.
  const activateFree = async () => {
    setBusy(true)
    try {
      await onboardingService.choosePlan(token, plan.code, cycle)
      const res = await onboardingService.checkout(token)
      if (res?.mode === 'FREE') onActivated(res)
      else onNeedsPayment()
    } catch (e) { notify.error(e.message || 'Could not activate your workspace') }
    finally { setBusy(false) }
  }
  return (
    <div>
      <Header
        title={plan ? 'Review your plan' : 'Choose your plan'}
        sub={plan
          ? 'This is the plan assigned to your organization. You can upgrade any time.'
          : 'Select the plan that fits your organization. You can upgrade any time.'} />

      {choosing ? (
        <>
          {/* Billing toggle lives with the picker, so plan-card prices switch
              monthly/yearly. The review summary uses the dedicated Billing step
              instead, so no toggle there. */}
          <div className="d-flex justify-content-center mb-4">
            <div className="btn-group">
              {[['MONTHLY', 'Monthly'], ['YEARLY', 'Yearly']].map(([c, label]) => (
                <button key={c} type="button" className={`btn btn-sm ${cycle === c ? 'btn-primary' : 'btn-light'}`}
                  onClick={() => onChangeCycle(c)}>
                  {label}{c === 'YEARLY' && yearlyDiscount > 0 && (
                    <span className="badge text-bg-success ms-2">Save {yearlyDiscount}%</span>
                  )}
                </button>
              ))}
            </div>
          </div>

          <div className="row g-3 mb-3">
            {plans.map((p) => (
              <div className={`col-12 col-sm-6 ${plans.length > 3 ? 'col-xl-3' : 'col-xl-4'}`} key={p.code}>
                <PricingCard plan={p} cycle={cardCycle} selected={p.code === plan?.code}
                  onSelect={(c) => { onChangePlan(c); setChoosing(false) }} />
              </div>
            ))}
          </div>
          <div className="d-flex gap-2">
            <Button variant="light" icon={FiArrowLeft} onClick={onBack}>Back</Button>
            {plan && <Button variant="light" onClick={() => setChoosing(false)}>Cancel</Button>}
          </div>
        </>
      ) : (
        <>
          <SubscriptionSummary plan={plan} cycleCode={cycle} onChangePlan={() => setChoosing(true)} />
          <div className="d-flex justify-content-between mt-3">
            <Button variant="light" icon={FiArrowLeft} onClick={onBack} disabled={busy}>Back</Button>
            {free
              ? <Button loading={busy} onClick={activateFree}>Activate workspace <FiArrowRight /></Button>
              : <Button onClick={onNext}>Choose billing <FiArrowRight /></Button>}
          </div>
        </>
      )}
    </div>
  )
}

/* ── Step 9: Billing cycle ───────────────────────────────────── */
// Confirms plan + billing cycle server-side (choose-plan) before payment.
function ChooseBilling({ plan, value, token, onChange, onNext, onBack }) {
  const { notify } = useNotification()
  const [busy, setBusy] = useState(false)

  const proceed = async () => {
    setBusy(true)
    try {
      await onboardingService.choosePlan(token, plan?.code, value) // value is MONTHLY | YEARLY
      onNext()
    } catch (e) { notify.error(e.message || 'Could not confirm plan') }
    finally { setBusy(false) }
  }

  return (
    <div>
      <Header title="Choose your billing cycle" sub="Commit longer, save more. Cancel any time." />
      <div className="row g-3 mb-3">
        {BILLING_CYCLES.map((c) => {
          const { total, discount, discountPercent } = priceBreakdown(plan, c.code)
          const renew = new Date(); renew.setMonth(renew.getMonth() + c.months)
          // "Best value" only when yearly is genuinely cheaper than monthly×12.
          return (
            <div className="col-12 col-md-6" key={c.code}>
              <OptionCard
                selected={value === c.code}
                onSelect={() => onChange(c.code)}
                title={c.label}
                subtitle={`Renews ${renew.toLocaleDateString()}`}
                badge={discountPercent > 0 ? `Save ${discountPercent}%` : null}
                right={
                  <>
                    <div className="fw-bold">{formatCurrency(total, plan?.currency)}</div>
                    {discount > 0 && <div className="text-success" style={{ fontSize: 11 }}>save {formatCurrency(discount, plan?.currency)}</div>}
                  </>
                }
              />
            </div>
          )
        })}
      </div>
      <div className="d-flex justify-content-between">
        <Button variant="light" icon={FiArrowLeft} onClick={onBack}>Back</Button>
        <Button loading={busy} onClick={proceed}>Continue to payment <FiArrowRight /></Button>
      </div>
    </div>
  )
}

/* ── Step 10: Payment (hosted checkout) ──────────────────────── */
// Backend uses a hosted-checkout model: request a session, then either redirect
// to the provider (real) or confirm via mock-confirm (test mode). Card details
// are NEVER entered here — they live on the provider's PCI-compliant page.
function Payment({ plan, cycle, token, onPaid, onBack }) {
  const { notify } = useNotification()
  const [busy, setBusy] = useState(false)
  const [session, setSession] = useState(null) // CheckoutResponse when in mock mode
  const b = priceBreakdown(plan, cycle)

  const startCheckout = async () => {
    setBusy(true)
    try {
      const res = await onboardingService.checkout(token)
      if (res?.checkoutUrl) { window.location.assign(res.checkoutUrl); return } // real provider → redirect
      // FREE: nothing to pay, and the backend has already activated the
      // account — mock-confirm would find no payment to confirm.
      if (res?.mode === 'FREE') { onPaid(res); return }
      setSession(res) // mock provider → show confirm button
    } catch (e) { notify.error(e.message || 'Could not start checkout') }
    finally { setBusy(false) }
  }

  const confirmMock = async () => {
    setBusy(true)
    try {
      const activation = await onboardingService.mockConfirm(token)
      onPaid(activation)
    } catch (e) { notify.error(e.message || 'Payment failed') }
    finally { setBusy(false) }
  }

  const amount = session?.amount ?? b.total
  const currency = session?.currency ?? plan?.currency

  return (
    <div>
      <Header title="Review & pay" sub="Secure checkout powered by our payment provider." />
      <div className="row g-3 justify-content-center">
        <div className="col-12 col-lg-6">
          <Card title="Order summary">
            <Row label={plan?.name} value={formatCurrency(b.base, plan?.currency)} />
            <Row label={`${b.cycle.label} billing`} value={b.cycle.months > 1 ? `${b.cycle.months} months` : '1 month'} muted />
            {b.discount > 0 && <Row label={`Cycle discount (${b.discountPercent}%)`} value={`− ${formatCurrency(b.discount, plan?.currency)}`} success />}
            {b.tax > 0 && <Row label="Tax" value={formatCurrency(b.tax, plan?.currency)} />}
            <hr />
            <div className="d-flex justify-content-between align-items-center mb-1">
              <span className="fw-bold">Total due</span>
              <span className="h4 mb-0 fw-bold">{formatCurrency(amount, currency)}</span>
            </div>
            {/* Before checkout this is an estimate; the checkout session returns the authoritative amount. */}
            {!session && <div className="text-muted mb-3" style={{ fontSize: 11 }}>Final amount confirmed at checkout.</div>}
            {session && <div className="mb-3" />}

            {!session ? (
              <Button className="w-100 justify-content-center py-2" icon={FiCreditCard} loading={busy} onClick={startCheckout}>
                Proceed to secure checkout
              </Button>
            ) : (
              <>
                <div className="alert alert-info py-2 small mb-3">
                  {session.note || 'Test mode — no real charge. Confirm to activate your subscription.'}
                </div>
                <Button className="w-100 justify-content-center py-2" loading={busy} onClick={confirmMock}>
                  Confirm payment
                </Button>
              </>
            )}

            <div className="d-flex align-items-center justify-content-center gap-2 mt-3 text-muted" style={{ fontSize: 12 }}>
              <FiShield className="text-success" /> Card details are entered on the provider’s secure page
            </div>
          </Card>
          <Button variant="light" className="mt-3" icon={FiArrowLeft} onClick={onBack}>Back</Button>
        </div>
      </div>
    </div>
  )
}

const Row = ({ label, value, success, muted }) => (
  <div className="d-flex justify-content-between py-1 small">
    <span className="text-muted">{label}</span>
    <span className={`fw-semibold ${success ? 'text-success' : ''} ${muted ? 'text-muted' : ''}`}>{value}</span>
  </div>
)

/* ── Step 11/12: Activated ───────────────────────────────────── */
// Uses ActivationCompleteResponse. The welcome/receipt email is sent
// server-side after payment, so there's no client email call here.
// On a free plan `activation` is the checkout's { mode: FREE, trialDays, note }
// — that call is what activated it — so there is no cycle, renewal or receipt.
function Activated({ activation, plan, cycle, free, email, onGo }) {
  const a = activation || {}
  const cyc = BILLING_CYCLES.find((c) => c.code === cycle)?.label || cycle
  const days = a.trialDays ?? plan?.trialDays
  const rows = free ? [
    ['Plan', a.subscriptionPlan || plan?.name],
    ['Free for', days > 0 ? `${days} days` : null],
    ['Trial ends', a.trialEndDate],
  ] : [
    ['Plan', a.subscriptionPlan || plan?.name],
    ['Billing cycle', a.billingCycle || cyc],
    ['Status', a.subscriptionStatus || 'ACTIVE'],
    ['Starts', a.subscriptionStartDate],
    ['Renews', a.subscriptionEndDate],
    ['Trial ends', a.trialEndDate],
  ]
  const shown = rows.filter(([, v]) => v)

  return (
    <div>
      <SuccessScreen
        title={free ? 'You’re all set 🎉' : 'Subscription activated 🎉'}
        message={free
          ? (a.note || `Your ${plan?.name || 'Free'} workspace is ready. Sign in to start.`)
          : `Your ${a.subscriptionPlan || plan?.name} plan is live. Welcome to Price Intelligence!`}
      >
        <div className="d-flex justify-content-center gap-2 mb-4">
          <Button icon={FiCheck} onClick={onGo}>Go to sign in</Button>
        </div>
      </SuccessScreen>

      {/* Server sends a welcome + receipt email — informational note only. */}
      <div className="d-flex align-items-center gap-3 p-3 rounded-3 mt-2"
        style={{ background: 'rgba(34,197,94,0.06)', border: '1px solid rgba(34,197,94,0.25)' }}>
        <div className="icon-box icon-grad-success flex-shrink-0"><FiMail /></div>
        <div>
          <div className="fw-semibold">A welcome email is on its way</div>
          <div className="text-muted small">
            We’ve emailed your {free ? '' : 'receipt and '}login details to <strong>{a.email || email}</strong>.
          </div>
        </div>
      </div>

      {shown.length > 0 && (
        <Card title="Subscription details" className="mt-3">
          <div className="row g-2 small">
            {shown.map(([k, v]) => (
              <div className="col-12 col-sm-6 d-flex justify-content-between border-bottom py-1" key={k}>
                <span className="text-muted">{k}</span>
                <span className="fw-semibold">{v}</span>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}

/* ── Expired / invalid link ──────────────────────────────────── */
function ExpiredLink({ onBack }) {
  return (
    <div className="ob-shell">
      <div className="ob-main">
        <div className="ob-body">
          <Card className="ob-narrow text-center p-4">
            <div className="icon-box icon-grad-warning mx-auto mb-3"><FiClock size={22} /></div>
            <h4 className="fw-bold mb-2">Invitation expired</h4>
            <p className="text-muted">This invitation link is invalid or has already been used. Ask your administrator to resend it.</p>
            <Button onClick={onBack}>Go to sign in</Button>
          </Card>
        </div>
      </div>
    </div>
  )
}

// Shown when the hosted (Stripe) checkout redirects back with ?paid=1. The
// backend webhook activates the subscription server-side, so there's nothing
// more for the client to do here except sign in.
function PaidReturn({ onGo }) {
  return (
    <div className="ob-shell">
      <div className="ob-main">
        <div className="ob-body ob-center">
          <Card className="ob-narrow text-center p-4">
            <SuccessScreen
              title="Payment successful 🎉"
              message="Your subscription is being activated. You can sign in to your workspace now.">
              <Button icon={FiCheck} onClick={onGo}>Go to sign in</Button>
            </SuccessScreen>
          </Card>
        </div>
      </div>
    </div>
  )
}
