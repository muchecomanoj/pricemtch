import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate, useLocation, Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { FiEye, FiEyeOff, FiArrowRight, FiAlertCircle } from 'react-icons/fi'
import { useAuth } from '../../context/AuthContext'
import { useNotification } from '../../context/NotificationContext'
import { APP_NAME, postLoginPath } from '../../constants'

export default function Login() {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { email: 'admin@priceintel.com', password: 'password' },
  })
  const [showPw, setShowPw] = useState(false)
  // Seeded from a mid-session sign-out: a company suspended while its users
  // were working lands them here, and the reason travels with them (see
  // api.js). Read once and cleared, so it does not reappear on the next visit.
  const [loginError, setLoginError] = useState(() => {
    try {
      const notice = sessionStorage.getItem('loginNotice') || ''
      sessionStorage.removeItem('loginNotice')
      return notice
    } catch { return '' }
  })
  const { login } = useAuth()
  const { notify } = useNotification()
  const navigate = useNavigate()
  const location = useLocation()

  const onSubmit = async (values) => {
    setLoginError('')
    try {
      const res = await login(values)
      // Two-factor required — continue on the OTP page.
      if (res?.twoFactorRequired) {
        navigate('/two-factor', {
          state: { email: res.email, challengeToken: res.challengeToken, from: location.state?.from },
          replace: true,
        })
        return
      }
      const user = res
      notify.success(`Welcome back, ${user.name}`)
      // Return to the originally-requested page only if this user may open it;
      // otherwise send them to their role's home (never a 403).
      const from = location.state?.from?.pathname
      const target = postLoginPath(user, from)
      navigate(target, { replace: true })
    } catch (err) {
      // Surface the backend's exact message on-screen. For a tenant that hasn't
      // paid yet the API returns "Please complete your subscription payment…" —
      // show that verbatim rather than a generic "no permission".
      const msg = err.message || 'Login failed'
      setLoginError(msg)
      notify.error(msg)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-aurora a1" />
      <div className="auth-aurora a2" />
      <div className="auth-aurora a3" />

      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4, ease: 'easeOut' }}
        className="auth-stack"
      >
        {/* Brand sits above the card, so the card holds nothing but the task. */}
        <div className="auth-brand">
          <span className="auth-logo">PI</span>
          <span className="auth-wordmark">{APP_NAME}</span>
        </div>
        <p className="auth-tagline">AI product &amp; competitor price intelligence</p>

        <div className="auth-card">
          {/* The strapline above names the product; this names the task. */}
          <div className="auth-head">
            <h1 className="auth-title">Sign in</h1>
            <p className="auth-sub">Enter your credentials to continue.</p>
          </div>

          {loginError && (
            <div className="auth-alert">
              <FiAlertCircle className="flex-shrink-0 mt-1" />
              <span>{loginError}</span>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} noValidate>
            <div className="auth-group">
              <label className="auth-label" htmlFor="login-email">Email</label>
              <input
                id="login-email"
                type="email"
                className={`form-control ${errors.email ? 'is-invalid' : ''}`}
                placeholder="you@company.com"
                {...register('email', { required: 'Email is required' })}
              />
              {errors.email && <div className="auth-err">{errors.email.message}</div>}
            </div>

            <div className="auth-group">
              <div className="auth-label-row">
                <label className="auth-label mb-0" htmlFor="login-password">Password</label>
                <Link to="/forgot-password" className="auth-forgot">Forgot?</Link>
              </div>
              <div className="auth-field">
                <input
                  id="login-password"
                  type={showPw ? 'text' : 'password'}
                  className={`form-control ${errors.password ? 'is-invalid' : ''}`}
                  placeholder="••••••••"
                  {...register('password', { required: 'Password is required' })}
                />
                <button type="button" className="auth-eye" tabIndex={-1}
                  aria-label={showPw ? 'Hide password' : 'Show password'}
                  onClick={() => setShowPw((s) => !s)}>
                  {showPw ? <FiEyeOff /> : <FiEye />}
                </button>
              </div>
              {errors.password && <div className="auth-err">{errors.password.message}</div>}
            </div>

            <motion.button
              type="submit"
              className="auth-btn d-inline-flex align-items-center justify-content-center gap-2 mt-4"
              whileTap={{ scale: 0.98 }}
              disabled={isSubmitting}
            >
              {isSubmitting
                ? <><span className="spinner-border spinner-border-sm" /> Signing in…</>
                : <>Sign in <FiArrowRight /></>}
            </motion.button>
          </form>
        </div>

        <p className="auth-foot">© {new Date().getFullYear()} {APP_NAME} · All rights reserved</p>
      </motion.div>
    </div>
  )
}
