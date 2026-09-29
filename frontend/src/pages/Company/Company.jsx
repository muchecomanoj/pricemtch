import { useEffect } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  FiSave, FiImage, FiBriefcase, FiMail, FiMapPin, FiGlobe, FiCreditCard, FiUsers, FiCalendar,
} from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Loader from '../../components/common/Loader'
import StatusBadge from '../../components/common/StatusBadge'
import ImageInput from '../../components/forms/ImageInput'
import { tenantService } from '../../services/tenantService'
import { useNotification } from '../../context/NotificationContext'
import { TIMEZONES, timezoneLabel } from '../../constants'
import { CURRENCIES } from '../../mock/onboarding'
import { formatDate } from '../../utils/format'

// Tenant self-service — GET/PUT /client/company.
// The form covers every field on UpdateTenantRequest; TenantResponse carries
// extra read-only context (company code, plan, status) shown alongside it.
export default function Company() {
  const qc = useQueryClient()
  const { notify } = useNotification()
  const { data, isLoading } = useQuery({ queryKey: ['company'], queryFn: tenantService.company })
  const { register, handleSubmit, reset, control, watch } = useForm()
  const logo = watch('logo')
  const name = watch('companyName')

  useEffect(() => { if (data) reset(data) }, [data, reset])

  const save = useMutation({
    mutationFn: (v) => tenantService.updateCompany(v),
    onSuccess: () => {
      notify.success('Company profile saved')
      qc.invalidateQueries({ queryKey: ['company'] })
    },
    onError: (e) => notify.error(e.message || 'Save failed'),
  })

  if (isLoading) return <Loader />

  return (
    <>
      <PageHeader title="Company Profile" subtitle="Your organization's details."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Company' }]} />

      {/* Identity banner — the logo and name you are editing, shown as they
          appear elsewhere in the product, so edits have visible context. */}
      <div className="company-hero">
        <div className="company-hero__logo">
          {logo
            ? <img src={logo} alt="Company logo" />
            : <FiImage size={26} className="text-muted opacity-50" />}
        </div>
        <div className="flex-grow-1" style={{ minWidth: 0 }}>
          <div className="d-flex align-items-center gap-2 flex-wrap">
            <span className="company-hero__name text-truncate">{name || data?.companyName || 'Your company'}</span>
            {data?.subscriptionStatus && <StatusBadge status={data.subscriptionStatus} />}
          </div>
          <div className="d-flex flex-wrap align-items-center gap-3 mt-2 text-muted small">
            {data?.companyCode && (
              <span className="d-inline-flex align-items-center gap-1">
                <FiBriefcase size={12} /><span className="font-monospace">{data.companyCode}</span>
              </span>
            )}
            {data?.subscriptionPlan && (
              <span className="d-inline-flex align-items-center gap-1">
                <FiCreditCard size={12} />{data.subscriptionPlan}
                {data.billingCycle && <span className="text-muted">· {data.billingCycle.toLowerCase()}</span>}
              </span>
            )}
            {data?.createdAt && (
              <span className="d-inline-flex align-items-center gap-1">
                <FiCalendar size={12} />Since {formatDate(data.createdAt)}
              </span>
            )}
          </div>
        </div>
      </div>

      <div className="row g-3">
        <div className="col-12 col-xl-8">
          <Card title="Company details" subtitle="Shown on invoices, emails and reports.">
            <form onSubmit={handleSubmit((v) => save.mutate(v))} className="row g-3">

              <FormSection icon={FiBriefcase} title="Identity" first />

              <div className="col-md-7">
                <label className="form-label" htmlFor="c-name">Company name</label>
                <input id="c-name" className="form-control" {...register('companyName')} />
              </div>
              <div className="col-md-5">
                {/* PNG so a transparent logo stays transparent. There is no
                    upload endpoint, so the file is downscaled and stored as a
                    data URI in the same `logo` string column. */}
                <label className="form-label">Logo</label>
                <Controller name="logo" control={control}
                  render={({ field }) => (
                    <ImageInput value={field.value} onChange={field.onChange}
                      maxDim={256} mime="image/png" height={92} />
                  )} />
              </div>

              <FormSection icon={FiMail} title="Contact" />

              <div className="col-md-4">
                <label className="form-label" htmlFor="c-email">Email</label>
                <input id="c-email" type="email" className="form-control" placeholder="hello@company.com"
                  {...register('companyEmail')} />
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="c-phone">Phone</label>
                <input id="c-phone" type="tel" className="form-control" {...register('companyPhone')} />
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="c-website">Website</label>
                <input id="c-website" type="url" className="form-control" placeholder="https://…"
                  {...register('website')} />
              </div>

              <FormSection icon={FiMapPin} title="Address" />

              <div className="col-12">
                <label className="form-label" htmlFor="c-address">Street address</label>
                <input id="c-address" className="form-control" {...register('companyAddress')} />
              </div>
              <div className="col-md-5">
                <label className="form-label" htmlFor="c-city">City</label>
                <input id="c-city" className="form-control" {...register('city')} />
              </div>
              <div className="col-md-4">
                <label className="form-label" htmlFor="c-state">State</label>
                <input id="c-state" className="form-control" {...register('state')} />
              </div>
              <div className="col-md-3">
                <label className="form-label" htmlFor="c-postal">Postal code</label>
                <input id="c-postal" className="form-control" {...register('postalCode')} />
              </div>

              <FormSection icon={FiGlobe} title="Regional settings" />

              <div className="col-md-4">
                <label className="form-label" htmlFor="c-country">Country</label>
                <input id="c-country" className="form-control" {...register('country')} />
              </div>
              <div className="col-md-5">
                {/* Free text invited typos like "Asia/Calcutta" vs the canonical
                    IANA id — use the same picker as signup and client admin. */}
                <label className="form-label" htmlFor="c-tz">Timezone</label>
                <select id="c-tz" className="form-select" {...register('timezone')}>
                  <option value="">Select…</option>
                  {TIMEZONES.map((tz) => <option key={tz} value={tz}>{timezoneLabel(tz)}</option>)}
                </select>
                <div className="form-text">Used for scheduled reports and alert timing.</div>
              </div>
              <div className="col-md-3">
                <label className="form-label" htmlFor="c-currency">Currency</label>
                {/* §11 puts `currency` on the tenant, so this IS where it is
                    owned — there is no separate currency master entity. */}
                <select id="c-currency" className="form-select" {...register('currency')}>
                  <option value="">Select…</option>
                  {CURRENCIES.map((c) => <option key={c}>{c}</option>)}
                </select>
                <div className="form-text">Reporting currency.</div>
              </div>

              <div className="col-12 d-flex justify-content-end pt-3 border-top-soft">
                <Button type="submit" icon={FiSave} loading={save.isPending}>Save changes</Button>
              </div>
            </form>
          </Card>
        </div>

        {/* Read-only fields from TenantResponse — set during onboarding or by
            the platform owner, so they are shown for reference, not edited. */}
        <div className="col-12 col-xl-4">
          <Card title="Account" subtitle="Managed by your platform provider." className="mb-3">
            <Row icon={FiBriefcase} label="Company code" value={data?.companyCode} mono />
            <Row icon={FiUsers} label="Contact person" value={data?.contactPerson} />
            <Row icon={FiBriefcase} label="Industry" value={data?.industry} />
            <Row icon={FiMail} label="Admin email" value={data?.adminEmail} />
          </Card>

          <Card title="Subscription">
            <Row icon={FiCreditCard} label="Plan" value={data?.subscriptionPlan} />
            <Row icon={FiCalendar} label="Billing" value={data?.billingCycle} />
            <Row icon={FiCreditCard} label="Status"
              value={data?.subscriptionStatus ? <StatusBadge status={data.subscriptionStatus} /> : null} />
            <Row icon={FiCalendar} label="Renews"
              value={data?.subscriptionEndDate ? formatDate(data.subscriptionEndDate) : null} />
            <Row icon={FiUsers} label="Users allowed" value={data?.maxUsers} />
          </Card>
        </div>
      </div>
    </>
  )
}

function FormSection({ icon: Icon, title, first }) {
  return (
    <div className="col-12">
      <div className={`form-section ${first ? 'form-section--first' : ''}`}>
        <span className="form-section__icon"><Icon size={14} /></span>
        <span className="form-section__title">{title}</span>
      </div>
    </div>
  )
}

function Row({ icon: Icon, label, value, mono }) {
  return (
    <div className="d-flex align-items-center justify-content-between gap-2 py-2 border-bottom-soft">
      <span className="text-muted small d-inline-flex align-items-center gap-2 flex-shrink-0">
        {Icon && <Icon size={13} className="opacity-75" />}{label}
      </span>
      <span className={`fw-semibold text-end text-truncate ${mono ? 'font-monospace' : ''}`}>
        {value ?? '—'}
      </span>
    </div>
  )
}
