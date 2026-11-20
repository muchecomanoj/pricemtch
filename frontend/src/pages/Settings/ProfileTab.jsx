import { useEffect } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { useQuery, useMutation } from '@tanstack/react-query'
import { FiSave } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import StatusBadge from '../../components/common/StatusBadge'
import ImageInput from '../../components/forms/ImageInput'
import { useAuth } from '../../context/AuthContext'
import { useNotification } from '../../context/NotificationContext'
import { ROLE_LABELS } from '../../constants'
import { profileService, PHONE_PATTERN } from '../../services/profileService'
import { formatDate, formatRelativeTime } from '../../utils/format'
import { errorMessage } from '../../services/apiError'

// ── Profile — the signed-in user's own details ──────────────────────────────
// PUT /users/{id} with only the fields UpdateUserRequest accepts. Language is
// deliberately absent: there is no such field, and FRD §2.4 puts the UI at
// English-only for this release.
export default function ProfileTab() {
  const { refreshUser } = useAuth()
  const { notify } = useNotification()
  const { register, handleSubmit, reset, control, formState: { errors } } = useForm()

  const { data: user, isLoading } = useQuery({ queryKey: ['profile'], queryFn: profileService.get })

  useEffect(() => {
    if (user) {
      reset({
        firstName: user.firstName || '',
        lastName: user.lastName || '',
        phone: user.phone || '',
        profileImage: user.profileImage || '',
      })
    }
  }, [user, reset])

  const save = useMutation({
    // `user` is passed as the baseline so only changed keys are sent.
    mutationFn: (values) => profileService.update(values, user),
    onSuccess: async () => {
      notify.success('Profile updated')
      await refreshUser()      // navbar name + avatar follow the change
    },
    onError: (e) => notify.error(errorMessage(e, 'Could not save your profile.')),
  })

  if (isLoading) return <Card title="Your details"><div className="skeleton" style={{ height: 260 }} /></Card>

  return (
    <div className="row g-3">
      <div className="col-12 col-lg-7">
        <Card title="Your details">
          <form onSubmit={handleSubmit((v) => save.mutate(v))} className="row g-3">
            <div className="col-md-6">
              <label className="form-label" htmlFor="pf-first">First name</label>
              <input id="pf-first" className={`form-control ${errors.firstName ? 'is-invalid' : ''}`}
                {...register('firstName', {
                  maxLength: { value: 100, message: 'Cannot exceed 100 characters.' },
                })} />
              {errors.firstName && <div className="invalid-feedback">{errors.firstName.message}</div>}
            </div>
            <div className="col-md-6">
              <label className="form-label" htmlFor="pf-last">Last name</label>
              <input id="pf-last" className={`form-control ${errors.lastName ? 'is-invalid' : ''}`}
                {...register('lastName', {
                  maxLength: { value: 100, message: 'Cannot exceed 100 characters.' },
                })} />
              {errors.lastName && <div className="invalid-feedback">{errors.lastName.message}</div>}
            </div>
            <div className="col-md-6">
              {/* Same pattern the backend enforces, so a bad number is caught
                  here rather than coming back as a 400. */}
              <label className="form-label" htmlFor="pf-phone">Phone</label>
              <input id="pf-phone" type="tel" className={`form-control ${errors.phone ? 'is-invalid' : ''}`}
                {...register('phone', {
                  pattern: {
                    value: PHONE_PATTERN,
                    message: '7–20 digits, optionally with +, spaces, dashes or brackets.',
                  },
                })} />
              {errors.phone && <div className="invalid-feedback">{errors.phone.message}</div>}
            </div>
            <div className="col-md-6">
              {/* Email is not on UpdateUserRequest — it identifies the account
                  and cannot be changed from here. */}
              <label className="form-label" htmlFor="pf-email">Email</label>
              <input id="pf-email" className="form-control" readOnly value={user?.email || ''} />
              <div className="form-text">Contact an administrator to change your email.</div>
            </div>
            <div className="col-12">
              <label className="form-label">Profile photo</label>
              <Controller name="profileImage" control={control}
                render={({ field }) => (
                  <ImageInput value={field.value} onChange={field.onChange}
                    maxDim={192} mime="image/jpeg" height={96} />
                )} />
            </div>
            <div className="col-12 d-flex justify-content-end pt-2 border-top-soft">
              <Button type="submit" icon={FiSave} loading={save.isPending}>Save changes</Button>
            </div>
          </form>
        </Card>
      </div>

      <div className="col-12 col-lg-5">
        <Card title="Account" subtitle="Set by your administrator.">
          <InfoRow label="Role"
            value={user?.role ? <StatusBadge status="info" label={ROLE_LABELS[user.role] || user.role} /> : null} />
          <InfoRow label="Status" value={user?.status ? <StatusBadge status={user.status} /> : null} />
          <InfoRow label="Last sign-in" value={user?.lastLogin ? formatRelativeTime(user.lastLogin) : null} />
          <InfoRow label="Member since" value={user?.createdAt ? formatDate(user.createdAt) : null} />
        </Card>
        <p className="form-text mt-2 mb-0">
          To change your password, use the <strong>Security</strong> tab.
        </p>
      </div>
    </div>
  )
}

function InfoRow({ label, value }) {
  return (
    <div className="d-flex align-items-center justify-content-between gap-2 py-2 border-bottom-soft">
      <span className="text-muted small">{label}</span>
      <span className="fw-semibold text-end">{value ?? '—'}</span>
    </div>
  )
}
