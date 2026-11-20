import { useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { FiArrowRight, FiArrowLeft, FiLock, FiEye, FiEyeOff, FiShield } from 'react-icons/fi'
import PasswordStrength, { isStrongPassword } from '../../../components/onboarding/PasswordStrength'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'
import { useNotification } from '../../../context/NotificationContext'
import { signupService } from '../../../services/signupService'

/* ── Step 4: Create password → POST /register/set-password ───── */
// On a free plan this is the last step: onNext also creates the account, so the
// button says so. If the password is saved but that call fails, a retry must
// not send the password again — the ref remembers it is already set.
export default function CreatePassword({ token, code, free, onNext, onBack }) {
  const { notify } = useNotification()
  const { register, handleSubmit, watch, formState: { errors } } = useForm()
  const [show, setShow] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const passwordSet = useRef(false)
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
      if (!passwordSet.current) {
        await signupService.setPassword(token, code, v.password)
        passwordSet.current = true
        if (!free) notify.success('Password created')
      }
      await onNext()
    } catch (e) {
      notify.error(e.message || (passwordSet.current ? 'Could not create your account' : 'Could not set password'))
    } finally { setBusy(false) }
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
            {free ? 'Create account' : 'Continue'} <FiArrowRight />
          </Button>
        </div>
      </form>
    </Card>
  )
}
