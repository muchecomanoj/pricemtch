import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { FiArrowRight, FiArrowLeft, FiLock, FiEye, FiEyeOff, FiShield } from 'react-icons/fi'
import PasswordStrength, { isStrongPassword } from '../../../components/onboarding/PasswordStrength'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'
import { useNotification } from '../../../context/NotificationContext'
import { onboardingService } from '../../../services/onboardingService'
import { errorMessage } from '../../../services/apiError'

/* ── Step 6: Create password ─────────────────────────────────── */
export default function CreatePassword({ token, code, onNext, onBack }) {
  const { notify } = useNotification()
  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm()
  const [show, setShow] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const pw = watch('password') || ''

  const onSubmit = async (v) => {
    if (!isStrongPassword(v.password)) return notify.warn('Password does not meet all requirements')
    try { await onboardingService.setPassword(token, code, v.password); notify.success('Password created'); onNext() }
    catch (e) { notify.error(errorMessage(e, 'Could not set password')) }
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
