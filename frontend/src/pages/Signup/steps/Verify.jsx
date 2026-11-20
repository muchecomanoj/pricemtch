import { useState } from 'react'
import { FiArrowLeft, FiMail } from 'react-icons/fi'
import OtpInput from '../../../components/common/OtpInput'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'
import { useNotification } from '../../../context/NotificationContext'
import { signupService } from '../../../services/signupService'
import { errorMessage } from '../../../services/apiError'

/* ── Step 3: Verify email ────────────────────────────────────── */
export default function Verify({ email, token, onVerified, onBack }) {
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
    } catch (e) { notify.error(errorMessage(e, 'Verification failed')); setCode('') }
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
