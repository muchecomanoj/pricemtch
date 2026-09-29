import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import {
  FiMail, FiLock, FiEye, FiEyeOff, FiArrowRight, FiArrowLeft,
  FiShield, FiCheck, FiKey,
} from 'react-icons/fi'
import OtpInput from '../../components/common/OtpInput'
import PasswordStrength, { isStrongPassword } from '../../components/onboarding/PasswordStrength'
import { authService } from '../../services/authService'
import { useNotification } from '../../context/NotificationContext'
import { APP_NAME } from '../../constants'

// ── Self-service password reset ─────────────────────────────────────────────
// Mirrors the live 3-step contract:
//   1. POST /auth/forgot-password    { email }                     -> emails a code
//   2. POST /auth/verify-reset-code  { email, code }
//   3. POST /auth/reset-password     { email, code, newPassword }
// The email and code are carried forward across all three steps.

const STEPS = ['email', 'code', 'password', 'done']

export default function ForgotPassword() {
  const [params] = useSearchParams()
  const [step, setStep] = useState(0)
  const [email, setEmail] = useState(params.get('email') || '')
  const [code, setCode] = useState('')
  const key = STEPS[step]

  return (
    <div className="auth-page">
      <div className="auth-aurora a1" />
      <div className="auth-aurora a2" />
      <div className="auth-aurora a3" />

      <motion.div
        initial={{ opacity: 0, y: 24, scale: 0.98 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.45, ease: 'easeOut' }}
        className="auth-card p-4 p-md-5"
      >
        <div className="d-flex flex-column align-items-center text-center mb-4">
          <div className="auth-logo mb-3">PI</div>
          <span className="auth-badge mb-3"><FiShield size={12} /> Account recovery</span>
          <h3 className="fw-bold mb-1 text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>
            {key === 'done' ? 'Password updated' : 'Reset your password'}
          </h3>
          <p className="mb-0" style={{ color: '#9aa6c4', fontSize: '0.92rem' }}>
            {key === 'email' && `We'll email a 6-digit code to your ${APP_NAME} address.`}
            {key === 'code' && <>Enter the code we sent to <strong className="text-white">{email}</strong>.</>}
            {key === 'password' && 'Choose a new password for your account.'}
            {key === 'done' && 'You can now sign in with your new password.'}
          </p>
        </div>

        {key !== 'done' && <ResetProgress step={step} />}

        <AnimatePresence>
          <motion.div key={key}
            initial={{ opacity: 0, x: 14 }} animate={{ opacity: 1, x: 0 }}
            transition={{ duration: 0.22 }}>
            {key === 'email' && (
              <EmailStep initial={email} onSent={(e) => { setEmail(e); setStep(1) }} />
            )}
            {key === 'code' && (
              <CodeStep email={email} onVerified={(c) => { setCode(c); setStep(2) }} onBack={() => setStep(0)} />
            )}
            {key === 'password' && (
              <PasswordStep email={email} code={code} onDone={() => setStep(3)} onBack={() => setStep(1)} />
            )}
            {key === 'done' && <DoneStep />}
          </motion.div>
        </AnimatePresence>

        {key !== 'done' && (
          <p className="text-center mt-4 mb-0" style={{ color: '#9aa6c4', fontSize: '0.85rem' }}>
            Remembered it? <Link to="/login" className="fw-semibold text-decoration-none" style={{ color: '#9fd6ff' }}>Back to sign in</Link>
          </p>
        )}
      </motion.div>
    </div>
  )
}

// Three-dot progress rail so the user knows how far through recovery they are.
function ResetProgress({ step }) {
  const labels = ['Email', 'Code', 'New password']
  return (
    <div className="d-flex align-items-center gap-2 mb-4">
      {labels.map((l, i) => (
        <div key={l} className="flex-fill text-center">
          <div className="rounded-pill" style={{
            height: 4,
            background: i <= step ? 'linear-gradient(90deg,#3fa9f5,#2b8fd8)' : 'rgba(255,255,255,0.12)',
            transition: 'background 0.3s',
          }} />
          <span className="small" style={{ color: i <= step ? '#c7d2fe' : '#6b7794', fontSize: '0.74rem' }}>{l}</span>
        </div>
      ))}
    </div>
  )
}

/* ── Step 1: email ─────────────────────────────────────────────── */
function EmailStep({ initial, onSent }) {
  const { notify } = useNotification()
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { email: initial },
  })

  const onSubmit = async ({ email }) => {
    try {
      await authService.forgotPassword(email)
      notify.success(`We sent a reset code to ${email}`)
      onSent(email)
    } catch (e) {
      notify.error(e.message || 'Could not send the reset code')
    }
  }

  // Submitting an empty/malformed email only marked the field inline, which is
  // easy to miss — raise it as a toast too.
  const onInvalid = (errs) => notify.error(errs.email?.message || 'Please enter your email address')

  return (
    <form onSubmit={handleSubmit(onSubmit, onInvalid)} noValidate>
      <div className="mb-4">
        <label className="form-label">Email address</label>
        <div className="input-group">
          <span className="input-group-text"><FiMail /></span>
          <input type="email" autoFocus
            className={`form-control ${errors.email ? 'is-invalid' : ''}`}
            placeholder="you@company.com"
            {...register('email', {
              required: 'Email is required',
              pattern: { value: /^\S+@\S+\.\S+$/, message: 'Enter a valid email' },
            })} />
          {errors.email && <div className="invalid-feedback">{errors.email.message}</div>}
        </div>
      </div>

      <motion.button type="submit" whileTap={{ scale: 0.98 }} disabled={isSubmitting}
        className="auth-btn d-inline-flex align-items-center justify-content-center gap-2">
        {isSubmitting
          ? <><span className="spinner-border spinner-border-sm" /> Sending code…</>
          : <>Send reset code <FiArrowRight /></>}
      </motion.button>
    </form>
  )
}

/* ── Step 2: verify the emailed code ───────────────────────────── */
function CodeStep({ email, onVerified, onBack }) {
  const { notify } = useNotification()
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [resending, setResending] = useState(false)

  const submit = async (value = code) => {
    if (value.length !== 6) return notify.error('Enter all 6 digits')
    setBusy(true)
    try {
      await authService.verifyResetCode(email, value)
      onVerified(value)
    } catch (e) {
      notify.error(e.message || 'That code is not valid')
    } finally { setBusy(false) }
  }

  const resend = async () => {
    setResending(true)
    try {
      await authService.forgotPassword(email)
      notify.success('We sent a new code')
      setCode('')
    } catch (e) {
      notify.error(e.message || 'Could not resend the code')
    } finally { setResending(false) }
  }

  return (
    <div>
      <div className="d-flex justify-content-center mb-4">
        <OtpInput value={code} onChange={setCode} onComplete={submit} disabled={busy} />
      </div>

      <motion.button type="button" whileTap={{ scale: 0.98 }} onClick={() => submit()} disabled={busy}
        className="auth-btn d-inline-flex align-items-center justify-content-center gap-2">
        {busy
          ? <><span className="spinner-border spinner-border-sm" /> Verifying…</>
          : <>Verify code <FiArrowRight /></>}
      </motion.button>

      <div className="d-flex justify-content-between align-items-center mt-3">
        <button type="button" className="btn btn-sm btn-link text-decoration-none p-0"
          style={{ color: '#9aa6c4' }} onClick={onBack} disabled={busy}>
          <FiArrowLeft size={13} /> Change email
        </button>
        <button type="button" className="btn btn-sm btn-link text-decoration-none p-0"
          style={{ color: '#9fd6ff' }} onClick={resend} disabled={busy || resending}>
          {resending ? 'Sending…' : 'Resend code'}
        </button>
      </div>
    </div>
  )
}

/* ── Step 3: choose the new password ───────────────────────────── */
function PasswordStep({ email, code, onDone, onBack }) {
  const { notify } = useNotification()
  const [show, setShow] = useState(false)
  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm()
  const password = watch('password', '')

  const onSubmit = async (v) => {
    try {
      await authService.resetPassword(email, code, v.password)
      notify.success('Your password has been reset')
      onDone()
    } catch (e) {
      notify.error(e.message || 'Could not reset your password')
    }
  }

  const onInvalid = (errs) =>
    notify.error(errs.password?.message || errs.confirm?.message || 'Please complete both password fields')

  return (
    <form onSubmit={handleSubmit(onSubmit, onInvalid)} noValidate>
      <div className="mb-3">
        <label className="form-label">New password</label>
        <div className="input-group">
          <span className="input-group-text"><FiLock /></span>
          <input type={show ? 'text' : 'password'} autoFocus
            className={`form-control ${errors.password ? 'is-invalid' : ''}`}
            placeholder="••••••••"
            {...register('password', {
              required: 'Password is required',
              validate: (v) => isStrongPassword(v) || 'Password does not meet all requirements',
            })} />
          <button type="button" className="input-group-text auth-pw-toggle" tabIndex={-1}
            onClick={() => setShow((s) => !s)}>
            {show ? <FiEyeOff /> : <FiEye />}
          </button>
          {errors.password && <div className="invalid-feedback">{errors.password.message}</div>}
        </div>
      </div>

      <div className="mb-3"><PasswordStrength value={password} /></div>

      <div className="mb-4">
        <label className="form-label">Confirm password</label>
        <div className="input-group">
          <span className="input-group-text"><FiKey /></span>
          <input type={show ? 'text' : 'password'}
            className={`form-control ${errors.confirm ? 'is-invalid' : ''}`}
            placeholder="Re-enter your password"
            {...register('confirm', {
              required: 'Please confirm your password',
              validate: (v) => v === password || 'Passwords do not match',
            })} />
          {errors.confirm && <div className="invalid-feedback">{errors.confirm.message}</div>}
        </div>
      </div>

      <motion.button type="submit" whileTap={{ scale: 0.98 }} disabled={isSubmitting}
        className="auth-btn d-inline-flex align-items-center justify-content-center gap-2">
        {isSubmitting
          ? <><span className="spinner-border spinner-border-sm" /> Updating…</>
          : <>Reset password <FiCheck /></>}
      </motion.button>

      <div className="text-center mt-3">
        <button type="button" className="btn btn-sm btn-link text-decoration-none p-0"
          style={{ color: '#9aa6c4' }} onClick={onBack} disabled={isSubmitting}>
          <FiArrowLeft size={13} /> Back to code
        </button>
      </div>
    </form>
  )
}

/* ── Done ──────────────────────────────────────────────────────── */
function DoneStep() {
  const navigate = useNavigate()
  return (
    <div className="text-center">
      <div className="d-grid mx-auto mb-4" style={{
        width: 68, height: 68, borderRadius: '50%', placeItems: 'center',
        background: 'linear-gradient(135deg,#10b981,#059669)',
        boxShadow: '0 12px 30px rgba(16,185,129,0.4)',
      }}>
        <FiCheck size={32} color="#fff" strokeWidth={3} />
      </div>
      <motion.button type="button" whileTap={{ scale: 0.98 }} onClick={() => navigate('/login', { replace: true })}
        className="auth-btn d-inline-flex align-items-center justify-content-center gap-2">
        Go to sign in <FiArrowRight />
      </motion.button>
    </div>
  )
}
