import { useState, useEffect } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { motion } from 'framer-motion'
import { FiShield, FiArrowRight, FiArrowLeft } from 'react-icons/fi'
import { useAuth } from '../../context/AuthContext'
import { useNotification } from '../../context/NotificationContext'
import { twoFactorService } from '../../services/twoFactorService'
import { postLoginPath } from '../../constants'
import OtpInput from '../../components/common/OtpInput'

// OTP step of login. Reached from Login when the backend flags twoFactorRequired.
export default function TwoFactor() {
  const navigate = useNavigate()
  const location = useLocation()
  const { applySession } = useAuth()
  const { notify } = useNotification()
  const email = location.state?.email
  const challengeToken = location.state?.challengeToken
  const from = location.state?.from?.pathname

  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)

  // If someone hits /two-factor directly without a challenge, send them to login.
  useEffect(() => { if (!challengeToken) navigate('/login', { replace: true }) }, [challengeToken, navigate])

  const submit = async (value) => {
    const otp = value ?? code
    if (otp.length !== 6) return notify.warn('Enter the 6-digit code')
    setBusy(true)
    try {
      // POST /auth/2fa/verify returns the full session (AuthResponse).
      const session = await twoFactorService.verify({ code: otp, challengeToken })
      const user = applySession(session)
      notify.success('Verified')
      const target = postLoginPath(user, from)
      navigate(target, { replace: true })
    } catch (e) {
      notify.error(e.message || 'Verification failed')
      setCode('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-aurora a1" />
      <div className="auth-aurora a2" />
      <div className="auth-aurora a3" />

      <motion.div
        initial={{ opacity: 0, y: 24, scale: 0.98 }} animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.45, ease: 'easeOut' }}
        className="auth-card p-4 p-md-5"
      >
        <div className="d-flex flex-column align-items-center text-center mb-4">
          <div className="auth-logo mb-3"><FiShield /></div>
          <h3 className="fw-bold mb-1 text-white" style={{ fontFamily: 'Plus Jakarta Sans' }}>Two-factor verification</h3>
          <p className="mb-0" style={{ color: '#9aa6c4', fontSize: '0.92rem' }}>
            Enter the 6-digit code{email ? <> sent to <span className="text-white">{email}</span></> : ' from your authenticator app'}.
          </p>
        </div>

        <OtpInput value={code} onChange={setCode} onComplete={submit} disabled={busy} />

        <button
          className="auth-btn d-inline-flex align-items-center justify-content-center gap-2 mt-4"
          onClick={() => submit()} disabled={busy}
        >
          {busy ? <><span className="spinner-border spinner-border-sm" /> Verifying…</> : <>Verify <FiArrowRight /></>}
        </button>

        <div className="d-flex align-items-center justify-content-center mt-4" style={{ fontSize: '0.85rem' }}>
          <button className="btn btn-sm p-0 border-0 d-inline-flex align-items-center gap-1"
            style={{ color: '#9aa6c4', background: 'transparent' }} onClick={() => navigate('/login')}>
            <FiArrowLeft size={14} /> Back to login
          </button>
        </div>

        <p className="text-center mt-4 mb-0" style={{ color: '#5f6b88', fontSize: '0.75rem' }}>
          Lost access to your device? Contact your administrator.
        </p>
      </motion.div>
    </div>
  )
}
