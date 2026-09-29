import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearchParams, useLocation, Link } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  FiArrowRight, FiArrowLeft, FiLock, FiEye, FiEyeOff, FiMail, FiShield,
  FiCreditCard, FiCheck,
} from 'react-icons/fi'
import StepBar from '../../components/onboarding/StepBar'
import PricingCard from '../../components/onboarding/PricingCard'
import PasswordStrength, { isStrongPassword } from '../../components/onboarding/PasswordStrength'
import SuccessScreen from '../../components/onboarding/SuccessScreen'
import { priceBreakdown } from '../../components/onboarding/SubscriptionSummary'
import OtpInput from '../../components/common/OtpInput'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import { useNotification } from '../../context/NotificationContext'
import { signupService } from '../../services/signupService'
import { SIGNUP_STEPS, CURRENCIES } from '../../mock/onboarding'
import { TIMEZONES, DEFAULT_TIMEZONE, timezoneLabel, APP_NAME } from '../../constants'
import { formatCurrency } from '../../utils/format'
import { publicService } from '../../services/publicService'
import { usePaymentConfirm } from '../../hooks/usePaymentConfirm'
import { normalizePlans, catalogDiscountPercent } from '../../utils/plans'

// Public self-signup: Plan → Profile → Verify → Password → Payment → Done.
const SCREENS = ['plan', 'profile', 'verify', 'password', 'payment', 'done']

export default function SelfSignup() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const [screen, setScreen] = useState(0)
  const [data, setData] = useState({
    planCode: params.get('plan') || 'PRO',
    cycle: 'YEARLY',
    profile: {},
    email: '',
    token: '',      // from POST /register, carried through the rest of the flow
    otpCode: '',    // carried into set-password
    result: null,   // final SelfRegisterResponse
  })

  const goToLogin = (loginUrl) => {
    const url = loginUrl || '/login'
    if (/^https?:\/\//i.test(url)) window.location.assign(url)
    else navigate(url)
  }

  // Live plan catalog — falls back to the bundled list if the API is unreachable,
  // so signup never dead-ends on a marketing endpoint being down.
  const plansQuery = useQuery({
    queryKey: ['public', 'plans'],
    queryFn: publicService.plans,
    staleTime: 5 * 60 * 1000,
  })
  const plans = normalizePlans(plansQuery.data)

  // Return leg from the hosted (Stripe) checkout. The backend sets its own
  // redirect URLs, so the browser lands on /register/success (or /register/cancel)
  // rather than the URLs the client sent. The in-memory token/state is gone, so
  // instead of dropping the user back on the plan step we read the path and show
  // the right screen; the payment itself is recorded server-side.
  const path = location.pathname
  const paidReturn = /\/register\/success/.test(path)
    || params.get('paid') === '1' || params.get('redirect_status') === 'succeeded'
  const canceledReturn = /\/register\/cancel/.test(path) || params.get('canceled') === '1'

  // Confirm the checkout session against the public endpoint so activation is
  // instant (the backend reconciler would also complete it within ~60s).
  const { status: confirmStatus } = usePaymentConfirm({
    active: paidReturn, sessionId: params.get('session_id'),
  })

  const key = SCREENS[screen]
  const plan = plans.find((p) => p.code === data.planCode)
    || plans.find((p) => p.recommended)
    || plans[0]
  const patch = (p) => setData((d) => ({ ...d, ...p }))
  const next = () => setScreen((s) => Math.min(s + 1, SCREENS.length - 1))
  const back = () => setScreen((s) => Math.max(s - 1, 0))

  // Steps shown in the top bar (Done is the success screen, not a step).
  const stepIndex = { plan: 0, profile: 1, verify: 2, password: 3, payment: 4, done: 4 }[key]

  if (paidReturn) {
    return (
      <div className="ob-shell flex-column">
        <StepBar steps={SIGNUP_STEPS} currentIndex={SIGNUP_STEPS.length}
          title={`Create your ${APP_NAME} account`} subtitle="Start your free trial"
          onExit={() => navigate('/login')} />
        <div className="ob-main">
          <div className="ob-body ob-center">
            <div className="ob-narrow">
              <SuccessScreen title="Payment successful 🎉"
                message={confirmStatus === 'success'
                  ? 'Your subscription is now active. You can sign in to your account.'
                  : 'Your subscription is being activated. You can sign in to your account now.'}>
                <Button icon={FiCheck} onClick={() => navigate('/login')}>Go to sign in</Button>
              </SuccessScreen>
            </div>
          </div>
        </div>
      </div>
    )
  }

  if (canceledReturn) {
    return (
      <div className="ob-shell flex-column">
        <StepBar steps={SIGNUP_STEPS} currentIndex={4}
          title={`Create your ${APP_NAME} account`} subtitle="Start your free trial"
          onExit={() => navigate('/login')} />
        <div className="ob-main">
          <div className="ob-body ob-center">
            <Card className="text-center p-4 ob-narrow">
              <div className="icon-box icon-grad-warning mx-auto mb-3"><FiCreditCard size={22} /></div>
              <h4 className="fw-bold mb-2">Checkout canceled</h4>
              <p className="text-muted mb-4">Your payment was not completed and you have not been charged. You can try again from the sign-up page.</p>
              <div className="d-flex gap-2 justify-content-center">
                <Button variant="light" onClick={() => navigate('/login')}>Back to sign in</Button>
                <Button onClick={() => navigate('/signup')}>Start over</Button>
              </div>
            </Card>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="ob-shell flex-column">
      <StepBar steps={SIGNUP_STEPS} currentIndex={stepIndex}
        title={`Create your ${APP_NAME} account`} subtitle="Start your free trial"
        onExit={() => navigate('/login')} />

      <div className="ob-main">
        <div className={`ob-body ${['verify', 'password', 'done'].includes(key) ? 'ob-center' : ''}`}>
          <AnimatePresence>
            <motion.div key={key} className="ob-content"
              initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.25 }}>

              {key === 'plan' && (
                <ChoosePlan plans={plans} loading={plansQuery.isLoading} selected={plan}
                  cycle={data.cycle}
                  onPlan={(c) => patch({ planCode: c })} onCycle={(c) => patch({ cycle: c })} onNext={next} />
              )}
              {key === 'profile' && (
                <Profile plan={plan} cycle={data.cycle} initial={data.profile}
                  onRegistered={({ token, email, profile }) => { patch({ token, email, profile }); next() }}
                  onBack={back} />
              )}
              {key === 'verify' && (
                <Verify email={data.email} token={data.token}
                  onVerified={(code) => { patch({ otpCode: code }); next() }} onBack={back} />
              )}
              {key === 'password' && (
                <CreatePassword token={data.token} code={data.otpCode} onNext={next} onBack={back} />
              )}
              {key === 'payment' && (
                <Payment plan={plan} cycle={data.cycle} token={data.token}
                  onPaid={(result) => { patch({ result }); next() }} onBack={back} />
              )}
              {key === 'done' && (
                <Done email={data.email} plan={plan} result={data.result}
                  onGo={() => goToLogin(data.result?.loginUrl)} />
              )}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </div>
  )
}

const Header = ({ title, sub }) => (
  <div className="mb-4 text-center">
    <h3 className="fw-bold mb-1" style={{ fontFamily: 'Plus Jakarta Sans' }}>{title}</h3>
    <p className="text-muted mb-0">{sub}</p>
  </div>
)

/* ── Step 1: Choose plan (landing) ───────────────────────────── */
function ChoosePlan({ plans, loading, selected, cycle, onPlan, onCycle, onNext }) {
  const trial = plans.find((p) => p.trialDays)?.trialDays
  const yearlyDiscount = catalogDiscountPercent(plans)
  return (
    <div>
      <Header title="Choose the plan that fits your business"
        sub={`Every plan starts with a ${trial || 14}-day free trial. No card charged today.`} />

      <div className="d-flex justify-content-center mb-4">
        <div className="btn-group">
          {[['MONTHLY', 'Monthly'], ['YEARLY', 'Yearly']].map(([c, label]) => (
            <button key={c} className={`btn btn-sm ${cycle === c ? 'btn-primary' : 'btn-light'}`} onClick={() => onCycle(c)}>
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
            <div className="col-12 col-sm-6 col-xl-3" key={i}>
              <div className="skeleton" style={{ height: 320 }} />
            </div>
          ))
          : plans.map((p) => (
            <div className={`col-12 col-sm-6 ${plans.length > 3 ? 'col-xl-3' : 'col-xl-4'}`} key={p.code}>
              <PricingCard plan={p} cycle={cycle === 'YEARLY' ? 'yearly' : 'monthly'}
                selected={selected?.code === p.code} onSelect={onPlan} />
            </div>
          ))}
      </div>

      <div className="d-flex justify-content-between align-items-center">
        <Link to="/login" className="text-decoration-none small">Already have an account? Sign in</Link>
        <Button onClick={onNext} disabled={loading}>Continue with {selected?.name} <FiArrowRight /></Button>
      </div>
    </div>
  )
}

/* ── Step 2: Profile → POST /register (returns the token, emails the code) ── */
// Fields map to SelfRegisterRequest. The company code is assigned by the backend.
function Profile({ plan, cycle, initial, onRegistered, onBack }) {
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
    } catch (e) { notify.error(e.message || 'Could not start registration') }
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
  const isFree = !Number(price)

  return (
    <div className="ob-summary">
      <span className="ob-eyebrow">Your plan</span>

      <div className="d-flex align-items-center justify-content-between gap-2 mt-2">
        <span className="ob-summary__name">{plan.name}</span>
        <span className="ob-summary__cycle">{yearly ? 'Yearly' : 'Monthly'}</span>
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
          {plan.trialDays > 0 ? `${plan.trialDays}-day free trial. ` : ''}
          No card charged today.
        </span>
      </div>
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

/* ── Step 3: Verify email ────────────────────────────────────── */
function Verify({ email, token, onVerified, onBack }) {
  const { notify } = useNotification()
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (value) => {
    const otp = value ?? code
    if (otp.length !== 6) return notify.warn('Enter the 6-digit code')
    setBusy(true)
    try {
      await signupService.verifyCode(token, otp)
      notify.success('Email verified')
      onVerified(otp)
    } catch (e) { notify.error(e.message || 'Verification failed'); setCode('') }
    finally { setBusy(false) }
  }

  return (
    <Card className="text-center p-2 ob-narrow">
      <div className="icon-box icon-grad-primary mx-auto mb-3"><FiMail size={22} /></div>
      <h4 className="fw-bold mb-1">Verify your email</h4>
      <p className="text-muted mb-4">Enter the 6-digit code sent to <span className="fw-semibold text-body">{email}</span></p>

      <OtpInput className="otp-light mb-4" value={code} onChange={setCode} onComplete={submit} disabled={busy} />

      <Button className="w-100 justify-content-center py-2" loading={busy} onClick={() => submit()}>Verify email</Button>

      <button className="btn btn-link btn-sm text-muted mt-3" onClick={onBack}>
        <FiArrowLeft size={13} /> Change details
      </button>
    </Card>
  )
}

/* ── Step 4: Create password → POST /register/set-password ───── */
function CreatePassword({ token, code, onNext, onBack }) {
  const { notify } = useNotification()
  const { register, handleSubmit, watch, formState: { errors } } = useForm()
  const [show, setShow] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const pw = watch('password') || ''

  // Force the user to type the password (and confirmation) — blocking paste/copy
  // stops autofilled or clipboard values slipping in unchecked.
  const noClipboard = {
    onPaste: (e) => { e.preventDefault(); notify.warn('For security, please type your password') },
    onCopy: (e) => e.preventDefault(),
    onCut: (e) => e.preventDefault(),
  }

  const onSubmit = async (v) => {
    if (!isStrongPassword(v.password)) return notify.warn('Password does not meet all requirements')
    setBusy(true)
    try {
      await signupService.setPassword(token, code, v.password)
      notify.success('Password created')
      onNext()
    } catch (e) { notify.error(e.message || 'Could not set password') }
    finally { setBusy(false) }
  }

  return (
    <Card className="p-2 ob-narrow">
      <div className="text-center mb-4">
        <div className="icon-box icon-grad-success mx-auto mb-3"><FiShield size={22} /></div>
        <h4 className="fw-bold mb-1">Create your password</h4>
        <p className="text-muted mb-0">Secure your admin account.</p>
      </div>
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <div className="mb-3">
          <label className="form-label">Password</label>
          <div className="input-group">
            <span className="input-group-text"><FiLock /></span>
            <input type={show ? 'text' : 'password'} className="form-control" {...noClipboard} {...register('password', { required: true })} />
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
              {...noClipboard}
              {...register('confirm', { validate: (v) => v === pw || 'Passwords do not match' })} />
            <button type="button" className="input-group-text cursor-pointer" onClick={() => setShowConfirm((s) => !s)}>
              {showConfirm ? <FiEyeOff /> : <FiEye />}
            </button>
            {errors.confirm && <div className="invalid-feedback">{errors.confirm.message}</div>}
          </div>
        </div>
        <div className="d-flex gap-2">
          <Button variant="light" type="button" icon={FiArrowLeft} onClick={onBack}>Back</Button>
          <Button type="submit" className="flex-grow-1 justify-content-center" loading={busy} disabled={!isStrongPassword(pw)}>
            Continue <FiArrowRight />
          </Button>
        </div>
      </form>
    </Card>
  )
}

/* ── Step 5: Payment (hosted checkout) ───────────────────────── */
function Payment({ plan, cycle, token, onPaid, onBack }) {
  const { notify } = useNotification()
  const [busy, setBusy] = useState(false)
  const [session, setSession] = useState(null) // CheckoutResponse (mock mode)
  const b = priceBreakdown(plan, cycle)
  const amount = session?.amount ?? b.total
  const currency = session?.currency ?? plan.currency
  // Read the real trial length rather than hardcoding it — the plan catalog is
  // editable in Plans admin, so a fixed "14-day" would drift out of date.
  const trialLabel = plan.trialDays > 0 ? `${plan.trialDays}-day free trial` : 'free trial'

  const startCheckout = async () => {
    setBusy(true)
    try {
      const res = await signupService.checkout(token)
      if (res?.checkoutUrl) { window.location.assign(res.checkoutUrl); return } // real provider → redirect
      setSession(res) // mock provider → confirm button
    } catch (e) { notify.error(e.message || 'Could not start checkout') }
    finally { setBusy(false) }
  }

  const confirmMock = async () => {
    setBusy(true)
    try {
      const result = await signupService.mockConfirm(token) // SelfRegisterResponse { completed, loginUrl }
      onPaid(result)
    } catch (e) { notify.error(e.message || 'Payment failed') }
    finally { setBusy(false) }
  }

  return (
    <div>
      <Header title="Start your subscription"
        sub={`${trialLabel.charAt(0).toUpperCase()}${trialLabel.slice(1)} — you won’t be charged until it ends.`} />
      <div className="row g-3 justify-content-center">
        <div className="col-12 col-lg-6">
          <Card title="Order summary">
            <Row label={plan.name} value={formatCurrency(b.base, plan.currency)} />
            <Row label={`${b.cycle.label} billing`} value={b.cycle.months > 1 ? `${b.cycle.months} months` : '1 month'} muted />
            {b.discount > 0 && <Row label={`Cycle discount (${b.discountPercent}%)`} value={`− ${formatCurrency(b.discount, plan.currency)}`} success />}
            <hr />
            <div className="d-flex justify-content-between align-items-center mb-1">
              <span className="fw-bold">Due after trial</span>
              <span className="h4 mb-0 fw-bold">{formatCurrency(amount, currency)}</span>
            </div>
            <div className="text-muted mb-3" style={{ fontSize: 11 }}>
              {formatCurrency(0, currency)} due today · {trialLabel}
            </div>

            {!session ? (
              <Button className="w-100 justify-content-center py-2" icon={FiCreditCard} loading={busy} onClick={startCheckout}>
                Proceed to secure checkout
              </Button>
            ) : (
              <>
                <div className="alert alert-info py-2 small mb-3">{session.note || 'Test mode — confirm to start your trial.'}</div>
                <Button className="w-100 justify-content-center py-2" loading={busy} onClick={confirmMock}>
                  Start free trial
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

/* ── Step 6: Done + welcome email ────────────────────────────── */
// Uses the final SelfRegisterResponse. The welcome email is sent server-side.
function Done({ email, plan, result, onGo }) {
  const a = result || {}
  const rows = [
    ['Plan', a.subscriptionPlan || plan.name],
    ['Company code', a.companyCode],
    ['Status', a.subscriptionStatus],
    ['Trial ends', a.trialEndDate],
    ['Renews', a.subscriptionEndDate],
  ].filter(([, v]) => v)

  return (
    <div className="ob-narrow">
      <SuccessScreen title="Welcome aboard 🎉" message={`Your ${a.subscriptionPlan || plan.name} workspace is ready.`}>
        <div className="d-flex justify-content-center mb-4">
          <Button icon={FiCheck} onClick={onGo}>Go to sign in</Button>
        </div>
      </SuccessScreen>

      <div className="d-flex align-items-center gap-3 p-3 rounded-3 mt-2"
        style={{ background: 'rgba(34,197,94,0.06)', border: '1px solid rgba(34,197,94,0.25)' }}>
        <div className="icon-box icon-grad-success flex-shrink-0"><FiMail /></div>
        <div>
          <div className="fw-semibold">A welcome email is on its way</div>
          <div className="text-muted small">Your receipt and login details were emailed to <strong>{email}</strong>.</div>
        </div>
      </div>

      {rows.length > 0 && (
        <Card title="Subscription details" className="mt-3">
          <div className="row g-2 small">
            {rows.map(([k, v]) => (
              <div className="col-12 d-flex justify-content-between border-bottom py-1" key={k}>
                <span className="text-muted">{k}</span><span className="fw-semibold">{v}</span>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  )
}
