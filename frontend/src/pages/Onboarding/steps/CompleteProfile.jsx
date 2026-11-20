import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { FiArrowRight, FiArrowLeft } from 'react-icons/fi'
import LogoUpload from '../../../components/onboarding/LogoUpload'
import { Input } from '../../../components/onboarding/StepFields'
import Card from '../../../components/common/Card'
import Button from '../../../components/common/Button'
import { useNotification } from '../../../context/NotificationContext'
import { onboardingService } from '../../../services/onboardingService'
import { CURRENCIES } from '../../../mock/onboarding'
import { TIMEZONES, DEFAULT_TIMEZONE, timezoneLabel } from '../../../constants'
import { Header } from './Header'
import { errorMessage } from '../../../services/apiError'

/* ── Step 7: Complete profile ────────────────────────────────── */
// Field names match CompleteProfileRequest exactly (gstin, taxId, postalCode…).
export default function CompleteProfile({ invite, token, initial, onSave, onBack }) {
  const { notify } = useNotification()
  const { register, handleSubmit, formState: { errors } } = useForm({
    defaultValues: {
      companyName: invite.companyName, country: 'India',
      currency: 'USD', timezone: DEFAULT_TIMEZONE,
      ...initial,
    },
  })
  const [logo, setLogo] = useState(null)
  const [busy, setBusy] = useState(false)

  const onSubmit = async (v) => {
    setBusy(true)
    try {
      await onboardingService.completeProfile(token, v)
      notify.success('Profile saved')
      onSave({ ...v, logo })
    } catch (e) { notify.error(errorMessage(e, 'Could not save profile')) }
    finally { setBusy(false) }
  }

  return (
    <div>
      <Header title="Complete your organization profile" sub="This helps us tailor pricing, tax and reporting." />
      <form onSubmit={handleSubmit(onSubmit)}>
        <Card title="Organization information" className="mb-3">
          <div className="row g-3">
            <div className="col-12 col-md-3">
              <label className="form-label">Company logo</label>
              <LogoUpload value={logo} onChange={setLogo} />
            </div>
            <div className="col-12 col-md-9">
              <div className="row g-3">
                <Input col={6} label="Organization name" required reg={register('companyName', { required: 'Required' })} err={errors.companyName} />
                <Input col={6} label="Website" reg={register('website')} placeholder="https://" />
                <Input col={6} label="Tax ID (EIN)" reg={register('taxId')} placeholder="e.g. 12-3456789" />
                <Input col={12} label="Address" reg={register('companyAddress')} />
                <Input col={6} label="Phone" reg={register('companyPhone')} />
                <Input col={6} label="Country" reg={register('country')} />
              </div>
            </div>
            <Input col={3} label="State" reg={register('state')} />
            <Input col={3} label="City" reg={register('city')} />
            <Input col={3} label="Postal code" reg={register('postalCode')} />
          </div>
        </Card>

        <Card title="Preferences" className="mb-3">
          <div className="row g-3">
            <Select col={4} label="Currency" reg={register('currency')} options={CURRENCIES} />
            <div className="col-12 col-md-8">
              <label className="form-label">Time zone</label>
              <select className="form-select" {...register('timezone')}>
                {TIMEZONES.map((tz) => <option key={tz} value={tz}>{timezoneLabel(tz)}</option>)}
              </select>
            </div>
          </div>
        </Card>

        <div className="d-flex flex-wrap gap-2 justify-content-between">
          <Button variant="light" type="button" icon={FiArrowLeft} onClick={onBack}>Back</Button>
          <Button type="submit" loading={busy}>Continue <FiArrowRight /></Button>
        </div>
      </form>
    </div>
  )
}

const Select = ({ col = 6, label, reg, options }) => (
  <div className={`col-12 col-md-${col}`}>
    <label className="form-label">{label}</label>
    <select className="form-select" {...reg}>{options.map((o) => <option key={o}>{o}</option>)}</select>
  </div>
)
