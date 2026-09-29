import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FiSave, FiSend, FiInfo, FiAlertCircle } from 'react-icons/fi'
import { useAuth } from '../../context/AuthContext'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import StatusBadge from '../../components/common/StatusBadge'
import { emailConfigService } from '../../services/emailConfigService'
import { useNotification } from '../../context/NotificationContext'
import { mpStatusBadge } from '../../utils/marketplaces'
import { formatRelativeTime } from '../../utils/format'

// GET / PUT /settings/email-config  +  POST /settings/email-config/test
// Reuses mpStatusBadge — the connector status vocabulary is identical
// (NOT_CONFIGURED / CONFIGURED / CONNECTED / ERROR).
const LABELS = {
  NOT_CONFIGURED: 'Not set up',
  CONFIGURED: 'Saved (untested)',
  CONNECTED: 'Connected',
  ERROR: 'Connection failed',
}

// Saving and testing need ADMIN or SUPER_ADMIN. Surface a readable reason
// rather than a bare "Forbidden" if a lesser role ever reaches this form.
const authError = (err) => (err?.response?.status === 403
  ? 'Only an administrator can change the email settings.'
  : null)

export default function EmailConfig() {
  // Viewable while the plan is expired; nothing here can be changed until it is renewed.
  const { readOnly } = useAuth()
  const qc = useQueryClient()
  const { notify } = useNotification()
  const { data, isLoading } = useQuery({ queryKey: ['emailConfig'], queryFn: emailConfigService.get })

  const { register, handleSubmit, reset, setValue, watch } = useForm({
    defaultValues: { enabled: false, startTls: true, sslEnabled: false, port: 587 },
  })
  const enabled = watch('enabled')

  useEffect(() => {
    if (data) reset({ ...data, password: '' })   // never prefill the password
  }, [data, reset])

  const save = useMutation({
    mutationFn: (values) => emailConfigService.save(values),
    onSuccess: () => {
      notify.success('Email settings saved')
      qc.invalidateQueries({ queryKey: ['emailConfig'] })
    },
    onError: (err) => notify.error(authError(err) || err?.message || 'Could not save the email settings.'),
  })

  const test = useMutation({
    mutationFn: () => emailConfigService.test(),
    onSuccess: (res) => {
      if (res?.status === 'CONNECTED') notify.success(res.lastMessage || 'SMTP connected')
      else notify.error(res?.lastMessage || 'SMTP connection failed')
      qc.invalidateQueries({ queryKey: ['emailConfig'] })
    },
    onError: (err) => notify.error(authError(err) || err?.message || 'Could not reach the mail server.'),
  })

  // 587 is STARTTLS, 465 is implicit SSL — set the toggles to match so the two
  // don't end up contradicting each other.
  const onPortChange = (e) => {
    const p = Number(e.target.value)
    if (p === 587) { setValue('startTls', true); setValue('sslEnabled', false) }
    if (p === 465) { setValue('startTls', false); setValue('sslEnabled', true) }
  }

  const status = data?.status || 'NOT_CONFIGURED'
  const badge = mpStatusBadge(status)

  if (isLoading) {
    return <Card title="Email sending"><div className="skeleton" style={{ height: 260 }} /></Card>
  }

  return (
    <Card
      title="Email sending"
      subtitle="When enabled, your emails are sent from your own address. When off, we send them for you."
      actions={<StatusBadge status={badge.tone} label={LABELS[status] || badge.label} />}
    >
      {status === 'ERROR' && data?.lastMessage && (
        <div className="alert alert-danger d-flex align-items-start gap-2 py-2 small">
          <FiAlertCircle className="flex-shrink-0 mt-1" />
          <span>{data.lastMessage}</span>
        </div>
      )}

      <form onSubmit={handleSubmit((v) => save.mutate(v))} className="row g-3">
        <div className="col-12">
          <div className="form-check form-switch">
            <input className="form-check-input" type="checkbox" id="smtp-enabled" {...register('enabled')} />
            <label className="form-check-label fw-semibold" htmlFor="smtp-enabled">
              Use my own SMTP server
            </label>
          </div>
          <div className="form-text">
            Leave off to keep sending through the platform&apos;s default mail server.
          </div>
        </div>

        <div className="col-md-8">
          <label className="form-label" htmlFor="smtp-host">SMTP host</label>
          <input id="smtp-host" className="form-control" placeholder="smtp.gmail.com"
            disabled={!enabled} {...register('host')} />
        </div>
        <div className="col-md-4">
          <label className="form-label" htmlFor="smtp-port">Port</label>
          <input id="smtp-port" type="number" className="form-control" placeholder="587"
            disabled={!enabled} {...register('port', { onChange: onPortChange })} />
          <div className="form-text">587 for STARTTLS, 465 for SSL.</div>
        </div>

        <div className="col-md-6">
          <label className="form-label" htmlFor="smtp-username">Username</label>
          <input id="smtp-username" className="form-control" autoComplete="off"
            disabled={!enabled} {...register('username')} />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor="smtp-password">Password / app password</label>
          <input id="smtp-password" type="password" className="form-control" autoComplete="new-password"
            placeholder={data?.passwordSet ? '•••••••• (saved)' : 'Enter password'}
            disabled={!enabled} {...register('password')} />
          <div className="form-text">
            {data?.passwordSet
              ? 'Leave blank to keep the saved password.'
              : 'Gmail and most providers require an app password, not your login password.'}
          </div>
        </div>

        <div className="col-md-6">
          <label className="form-label" htmlFor="smtp-from">From address</label>
          <input id="smtp-from" type="email" className="form-control" placeholder="noreply@yourcompany.com"
            disabled={!enabled} {...register('fromAddress')} />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor="smtp-from-name">From name</label>
          <input id="smtp-from-name" className="form-control" placeholder="Your Company"
            disabled={!enabled} {...register('fromName')} />
        </div>

        <div className="col-md-6">
          <div className="form-check form-switch">
            <input className="form-check-input" type="checkbox" id="smtp-starttls"
              disabled={!enabled} {...register('startTls')} />
            <label className="form-check-label" htmlFor="smtp-starttls">Use STARTTLS</label>
          </div>
        </div>
        <div className="col-md-6">
          <div className="form-check form-switch">
            <input className="form-check-input" type="checkbox" id="smtp-ssl"
              disabled={!enabled} {...register('sslEnabled')} />
            <label className="form-check-label" htmlFor="smtp-ssl">Use SSL</label>
          </div>
        </div>

        <div className="col-12 d-flex flex-wrap align-items-center gap-2 pt-2 border-top-soft">
          <Button type="submit" icon={FiSave} loading={save.isPending} disabled={readOnly} title={readOnly ? 'Renew to use this' : undefined}>Save</Button>
          <Button type="button" variant="light" icon={FiSend} loading={test.isPending}
            disabled={!data?.configured || readOnly} onClick={() => test.mutate()}>
            Test connection
          </Button>
          {data?.lastCheckedAt && (
            <span className="text-muted small">Last tested {formatRelativeTime(data.lastCheckedAt)}</span>
          )}
          <span className="d-flex align-items-center gap-1 text-muted small ms-auto">
            <FiInfo size={13} /> Test uses the saved settings — save first.
          </span>
        </div>
      </form>
    </Card>
  )
}
