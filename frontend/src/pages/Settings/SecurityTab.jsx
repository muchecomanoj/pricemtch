import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { FiShield, FiCheckCircle, FiRotateCcw } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import OtpInput from '../../components/common/OtpInput'
import QrCode from '../../components/common/QrCode'
import StatusBadge from '../../components/common/StatusBadge'
import { useNotification } from '../../context/NotificationContext'
import { authService } from '../../services/authService'
import { twoFactorService } from '../../services/twoFactorService'
import { errorMessage } from '../../services/apiError'

// Change password — wired to POST /auth/change-password (ChangePasswordRequest).
export default function SecurityTab() {
  const { notify } = useNotification()
  const { register, handleSubmit, watch, reset, clearErrors, formState: { errors, isSubmitting } } = useForm()
  const newPassword = watch('newPassword')

  const onSubmit = async (v) => {
    try {
      await authService.changePassword({ currentPassword: v.currentPassword, newPassword: v.newPassword })
      notify.success('Password updated')
      reset()
    } catch (e) {
      notify.error(errorMessage(e, 'Could not update password'))
    }
  }

  return (
    <Card title="Change Password">
      <form className="row g-3" onSubmit={handleSubmit(onSubmit)}>
        <div className="col-md-6">
          <label className="form-label">Current Password</label>
          <input type="password" className={`form-control ${errors.currentPassword ? 'is-invalid' : ''}`}
            {...register('currentPassword', { required: 'Required' })} />
          {errors.currentPassword && <div className="invalid-feedback">{errors.currentPassword.message}</div>}
        </div>
        <div className="col-md-6" />
        <div className="col-md-6">
          <label className="form-label">New Password</label>
          <input type="password" className={`form-control ${errors.newPassword ? 'is-invalid' : ''}`}
            {...register('newPassword', { required: 'Required', minLength: { value: 8, message: 'Min 8 characters' } })} />
          {errors.newPassword && <div className="invalid-feedback">{errors.newPassword.message}</div>}
        </div>
        <div className="col-md-6">
          <label className="form-label">Confirm Password</label>
          <input type="password" className={`form-control ${errors.confirm ? 'is-invalid' : ''}`}
            {...register('confirm', { validate: (v) => v === newPassword || 'Passwords do not match' })} />
          {errors.confirm && <div className="invalid-feedback">{errors.confirm.message}</div>}
        </div>
        <div className="col-12 d-flex justify-content-end gap-2 pt-2 border-top-soft">
          {/* type="button" so it clears the fields instead of submitting —
              Button defaults to that, but it is load-bearing here. */}
          <Button type="button" variant="light" icon={FiRotateCcw} disabled={isSubmitting}
            onClick={() => { reset(); clearErrors() }}>Reset</Button>
          <Button type="submit" loading={isSubmitting}>Update Password</Button>
        </div>
      </form>
    </Card>
  )
}

// Two-factor authentication — enable (QR + verify + backup codes) / disable.
// Backend TBD; wired to twoFactorService (mock). Demo code: 123456
export function TwoFactorSettings() {
  const { notify } = useNotification()
  const [enabled, setEnabled] = useState(false)
  const [loading, setLoading] = useState(true)
  const [mode, setMode] = useState('idle')       // idle | enroll | disable
  const [setupData, setSetupData] = useState(null)
  const [code, setCode] = useState('')
  const [backupCodes, setBackupCodes] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    twoFactorService.status().then((s) => setEnabled(!!s.enabled)).finally(() => setLoading(false))
  }, [])

  const startEnroll = async () => {
    setBusy(true)
    try { setSetupData(await twoFactorService.setup()); setMode('enroll'); setCode('') }
    catch (e) { notify.error(e.message) } finally { setBusy(false) }
  }

  const confirmEnable = async (value) => {
    setBusy(true)
    try {
      const res = await twoFactorService.enable(value ?? code)
      setBackupCodes(res.backupCodes || [])
      setEnabled(true); setMode('idle'); setSetupData(null)
      notify.success('Two-factor authentication enabled')
    } catch (e) { notify.error(e.message) } finally { setBusy(false) }
  }

  const confirmDisable = async (value) => {
    setBusy(true)
    try {
      await twoFactorService.disable(value ?? code)
      setEnabled(false); setMode('idle'); setBackupCodes(null); setCode('')
      notify.success('Two-factor authentication disabled')
    } catch (e) { notify.error(e.message) } finally { setBusy(false) }
  }

  return (
    <Card
      title="Two-Factor Authentication"
      actions={loading ? null : <StatusBadge status={enabled ? 'success' : 'secondary'} label={enabled ? 'Enabled' : 'Disabled'} />}
    >
      <div className="d-flex align-items-start gap-3">
        <div className="icon-box icon-grad-primary flex-shrink-0"><FiShield /></div>
        <div className="flex-grow-1">
          <p className="text-muted mb-3">
            Add an extra layer of security by requiring a 6-digit code from your authenticator app at sign-in.
          </p>

          {/* Idle */}
          {mode === 'idle' && !loading && (
            enabled
              ? <Button variant="light" onClick={() => { setMode('disable'); setCode('') }}>Disable 2FA</Button>
              : <Button icon={FiShield} loading={busy} onClick={startEnroll}>Enable 2FA</Button>
          )}

          {/* Enrollment */}
          {mode === 'enroll' && setupData && (
            <div>
              <div className="row g-3 align-items-center mb-3">
                <div className="col-auto">
                  {/* Real QR rendered from the backend's otpauthUrl */}
                  <QrCode value={setupData.otpauthUrl} size={148} />
                </div>
                <div className="col">
                  <div className="small text-muted mb-1">
                    Scan the QR with your authenticator app, or enter this secret key manually:
                  </div>
                  <div className="twofa-secret mb-2">{setupData.secret}</div>
                  <div className="small text-muted">Then enter the 6-digit code to confirm.</div>
                </div>
              </div>
              <OtpInput className="otp-light otp-start" value={code} onChange={setCode}
                onComplete={confirmEnable} disabled={busy} />
              <div className="d-flex gap-2 mt-3">
                <Button variant="light" onClick={() => { setMode('idle'); setSetupData(null) }}>Cancel</Button>
                <Button loading={busy} onClick={() => confirmEnable()}>Confirm & Enable</Button>
              </div>
            </div>
          )}

          {/* Disable confirmation */}
          {mode === 'disable' && (
            <div>
              <div className="small text-muted mb-2">Enter a current 6-digit code to turn off 2FA.</div>
              <OtpInput className="otp-light otp-start" value={code} onChange={setCode}
                onComplete={confirmDisable} disabled={busy} />
              <div className="d-flex gap-2 mt-3">
                <Button variant="light" onClick={() => setMode('idle')}>Cancel</Button>
                <Button variant="danger" loading={busy} onClick={() => confirmDisable()}>Disable</Button>
              </div>
            </div>
          )}

          {/* Backup codes (shown once after enabling) */}
          {backupCodes && (
            <div className="mt-4">
              <div className="d-flex align-items-center gap-2 mb-2 text-success">
                <FiCheckCircle /> <span className="fw-semibold">Save your backup codes</span>
              </div>
              <p className="text-muted small">Store these somewhere safe. Each can be used once if you lose access to your app.</p>
              <div className="row g-2" style={{ maxWidth: 360 }}>
                {backupCodes.map((c) => <div className="col-6" key={c}><div className="backup-code">{c}</div></div>)}
              </div>
            </div>
          )}
        </div>
      </div>
    </Card>
  )
}
