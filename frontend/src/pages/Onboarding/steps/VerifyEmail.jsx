import { useState } from 'react'
import { FiArrowLeft, FiMail } from 'react-icons/fi'
import OtpInput from '../../../components/common/OtpInput'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'
import { useNotification } from '../../../context/NotificationContext'
import { onboardingService } from '../../../services/onboardingService'
import { errorMessage } from '../../../services/apiError'

/* ── Step 5: Email verification ──────────────────────────────── */
// The verified code is emitted upward — set-password re-checks it server-side.
export default function VerifyEmail({ invite, token, onVerified, onBack }) {
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
    } catch (e) { notify.error(errorMessage(e, 'Verification failed')); setCode('') }
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
