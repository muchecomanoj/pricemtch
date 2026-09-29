import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useForm, Controller } from 'react-hook-form'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import { useAuth } from '../../context/AuthContext'
import { useNotification } from '../../context/NotificationContext'
import { authService } from '../../services/authService'
import { twoFactorService } from '../../services/twoFactorService'
import OtpInput from '../../components/common/OtpInput'
import QrCode from '../../components/common/QrCode'
import StatusBadge from '../../components/common/StatusBadge'
import {
  FiShield, FiCheckCircle, FiShoppingBag, FiRefreshCw, FiSearch, FiUser, FiMail,
  FiSend, FiSave, FiRotateCcw, FiBell, FiSliders, FiRepeat,
} from 'react-icons/fi'
import { useEffect } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import ImageInput from '../../components/forms/ImageInput'
import { ROLE_LABELS, CAPABILITIES } from '../../constants'
import { profileService, PHONE_PATTERN } from '../../services/profileService'
import { formatDate, formatRelativeTime } from '../../utils/format'
import { marketplaceService } from '../../services/marketplaceService'
import { marketplaceConfigService } from '../../services/marketplaceConfigService'
import { mpLabel, marketplaceCodes, healthFor, healthBadge, isUsable, mpStatusBadge } from '../../utils/marketplaces'
import EmailTemplates from './EmailTemplates'
import EmailConfig from './EmailConfig'
import MarketplaceConfigModal from './MarketplaceConfigModal'
import NotificationChannels from './NotificationChannels'
import PricingPolicies from './PricingPolicies'
import ExchangeRates from './ExchangeRates'

// Icon + one-line description per tab, for the redesigned side navigation.
const TAB_META = {
  Profile: { icon: FiUser, desc: 'Name, email & language' },
  Marketplaces: { icon: FiShoppingBag, desc: 'Search sources & integrations' },
  'Email Sending': { icon: FiSend, desc: 'SMTP server & sender address' },
  'Email Templates': { icon: FiMail, desc: 'Emails sent to clients' },
  Notifications: { icon: FiBell, desc: 'Slack & Teams alert delivery' },
  'Pricing Policies': { icon: FiSliders, desc: 'Limits every recommendation obeys' },
  'Exchange Rates': { icon: FiRepeat, desc: 'Currency conversion for statistics' },
  Security: { icon: FiShield, desc: 'Password & 2FA' },
}

export default function Settings() {
  // ?tab= makes a tab linkable. Without it, "Add a rate" on the product page
  // landed on Profile and left the reader to find Exchange Rates themselves —
  // a link that does not arrive where it promised is worse than no link.
  const [searchParams, setSearchParams] = useSearchParams()
  const [tab, setTabState] = useState(searchParams.get('tab') || 'Profile')
  const setTab = (next) => {
    setTabState(next)
    setSearchParams(next === 'Profile' ? {} : { tab: next }, { replace: true })
  }
  const { user, hasRole } = useAuth()

  // Email Templates stays platform-owner only. Email Sending is self-service:
  // a tenant admin configures their own SMTP (their domain, their credentials),
  // and the super admin manages the platform default.
  const isSuperAdmin = !!user?.superAdmin
  // Tab visibility follows the ROLE, not the subscription. An expired plan
  // keeps viewing these; the pages themselves lock their Save and Delete
  // buttons. Going through can() here would hide the tabs outright.
  const canManageEmail = hasRole(CAPABILITIES.MANAGE_EMAIL)
  const canManagePricing = hasRole(CAPABILITIES.MANAGE_PRICING)
  const TABS = [
    'Profile',
    'Marketplaces',
    ...(canManageEmail ? ['Email Sending'] : []),
    ...(isSuperAdmin ? ['Email Templates'] : []),
    // Webhook URLs are credentials for the whole workspace, so this stays
    // with the roles that already manage tenant-wide configuration.
    ...(canManageEmail ? ['Notifications'] : []),
    // Both set tenant-wide numbers that feed real prices, so they follow the
    // pricing permission rather than plain viewing.
    ...(canManagePricing ? ['Pricing Policies', 'Exchange Rates'] : []),
    'Security',
  ]

  // A tab named in the URL that this role cannot see falls back to the first
  // one it can, rather than rendering an empty panel.
  const activeTab = TABS.includes(tab) ? tab : TABS[0]

  return (
    <>
      <PageHeader title="Settings" subtitle="Manage your account, workspace and platform preferences."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Settings' }]} />
      <div className="row g-3">
        <div className="col-lg-3">
          <Card bodyClassName="p-2" className="settings-nav-card">
            <div className="d-flex flex-column gap-1">
              {TABS.map((t) => {
                const meta = TAB_META[t] || {}
                const Icon = meta.icon
                const active = activeTab === t
                return (
                  <button key={t} onClick={() => setTab(t)}
                    className={`btn text-start d-flex align-items-center gap-3 py-2 ${active ? 'btn-primary' : 'btn-light'}`}>
                    {Icon && (
                      <span className="d-grid rounded-2 flex-shrink-0"
                        style={{ width: 30, height: 30, placeItems: 'center', background: active ? 'rgba(255,255,255,0.2)' : 'var(--app-bg)' }}>
                        <Icon size={15} />
                      </span>
                    )}
                    <span style={{ minWidth: 0, lineHeight: 1.25 }}>
                      <span className="fw-semibold d-block">{t}</span>
                      {meta.desc && <span className={`small ${active ? 'text-white-50' : 'text-muted'}`}>{meta.desc}</span>}
                    </span>
                  </button>
                )
              })}
            </div>
          </Card>
        </div>
        <div className="col-lg-9">
          {activeTab === 'Profile' && <ProfileTab />}
          {activeTab === 'Marketplaces' && <MarketplacesSettings isSuperAdmin={isSuperAdmin} />}
          {activeTab === 'Email Sending' && canManageEmail && <EmailConfig />}
          {activeTab === 'Email Templates' && isSuperAdmin && <EmailTemplates />}
          {activeTab === 'Notifications' && canManageEmail && <NotificationChannels />}
        {activeTab === 'Pricing Policies' && canManagePricing && <PricingPolicies />}
        {activeTab === 'Exchange Rates' && canManagePricing && <ExchangeRates />}
          {activeTab === 'Security' && (
            <>
              <SecurityTab />
              <div className="mt-3"><TwoFactorSettings /></div>
            </>
          )}
        </div>
      </div>
    </>
  )
}

// ── Profile — the signed-in user's own details ──────────────────────────────
// PUT /users/{id} with only the fields UpdateUserRequest accepts. Language is
// deliberately absent: there is no such field, and FRD §2.4 puts the UI at
// English-only for this release.
function ProfileTab() {
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
    onError: (e) => notify.error(e.message || 'Could not save your profile.'),
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

// ── Marketplaces — the sources product searches run against ─────────────────
// Two role-specific views over GET /marketplaces + /marketplaces/health:
//   Super admin → platform integration status (all marketplaces + health).
//   Client      → the marketplaces their searches cover (available + health).
// No enable/disable endpoint exists yet, so both are read-only — no toggle is
// shown until it can actually persist (see utils/marketplaces + the spec).
function MarketplacesSettings({ isSuperAdmin }) {
  // Super admin gets the full integration console (credentials, status, test);
  // clients get a read-only view of which sources their searches cover.
  return isSuperAdmin ? <SuperAdminMarketplaces /> : <ClientMarketplaces />
}

// Super-admin integration console — GET /admin/marketplaces (status, capabilities,
// masked credentials) + Configure modal (PUT + POST /test).
function SuperAdminMarketplaces() {
  const listQ = useQuery({ queryKey: ['admin-marketplaces'], queryFn: marketplaceConfigService.list })
  const [configuring, setConfiguring] = useState(null)   // the entry being integrated

  const refresh = () => listQ.refetch()

  // One card per backend entry, and nothing else. This used to append an
  // "Open Web" card marked Connected whenever the API didn't return one —
  // so a channel that has since been removed still showed as available, and
  // anything a user configured from it produced requests the API rejects.
  const shown = Array.isArray(listQ.data) ? listQ.data : []

  return (
    <Card
      title="Marketplaces"
      subtitle="Integrate and monitor the search sources used across all clients."
      actions={(
        <Button size="sm" variant="light" icon={FiRefreshCw} loading={listQ.isFetching} onClick={refresh}>Refresh</Button>
      )}>
      {listQ.isError ? (
        <div className="alert alert-danger py-2 small mb-0">
          Couldn&apos;t load marketplaces. <button className="btn btn-link btn-sm p-0 align-baseline" onClick={refresh}>Retry</button>
        </div>
      ) : listQ.isLoading ? (
        <div className="text-muted small py-3">Loading marketplaces…</div>
      ) : shown.length === 0 ? (
        <div className="text-muted small py-3">No marketplaces available.</div>
      ) : (
        shown.map((e) => {
          const badge = mpStatusBadge(e.status)
          const name = e.name || mpLabel(e.code)
          const connected = e.status === 'CONNECTED'
          return (
            <div className="d-flex align-items-start justify-content-between gap-3 py-3 border-bottom" key={e.code}>
              <div className="d-flex align-items-start gap-3">
                <div className={`icon-box flex-shrink-0 ${connected ? 'icon-grad-primary' : ''}`}
                  style={connected ? undefined : { background: 'var(--app-bg)', color: 'var(--text-secondary)' }}>
                  <FiShoppingBag />
                </div>
                <div>
                  <div className="fw-semibold">{name}</div>
                  {e.description && <div className="text-muted small">{e.description}</div>}
                  {/* Capabilities as chips */}
                  {e.capabilities?.length > 0 && (
                    <div className="d-flex flex-wrap gap-1 mt-1">
                      {e.capabilities.map((c) => <span key={c} className="badge text-bg-light border">{c}</span>)}
                    </div>
                  )}
                  {/* Show the failure reason on ERROR */}
                  {e.status === 'ERROR' && e.lastMessage && <div className="text-danger small mt-1">{e.lastMessage}</div>}
                </div>
              </div>
              <div className="d-flex align-items-center gap-2 flex-shrink-0">
                <StatusBadge status={badge.tone} label={badge.label} />
                <Button size="sm" variant="light" onClick={() => setConfiguring(e)}>Configure</Button>
              </div>
            </div>
          )
        })
      )}

      {configuring && (
        <MarketplaceConfigModal
          show={!!configuring}
          entry={configuring}
          name={configuring.name || mpLabel(configuring.code)}
          onClose={() => setConfiguring(null)}
          onSaved={refresh}
        />
      )}
    </Card>
  )
}

// Client read-only view — which marketplaces their product searches cover, from
// the public /marketplaces + /marketplaces/health endpoints.
function ClientMarketplaces() {
  const listQ = useQuery({ queryKey: ['marketplaces'], queryFn: marketplaceService.list })
  const healthQ = useQuery({ queryKey: ['marketplaces', 'health'], queryFn: () => marketplaceService.health() })

  const codes = marketplaceCodes(listQ.data)
  const health = Array.isArray(healthQ.data) ? healthQ.data : []
  const failed = listQ.isError || healthQ.isError
  const checkedAt = health.find((h) => h.checkedAt)?.checkedAt
  const refresh = () => { listQ.refetch(); healthQ.refetch() }

  return (
    <Card
      title="Marketplaces"
      subtitle="Marketplaces your product searches cover."
      actions={(
        <Button size="sm" variant="light" icon={FiRefreshCw}
          loading={listQ.isFetching || healthQ.isFetching} onClick={refresh}>Refresh</Button>
      )}>
      {failed ? (
        <div className="alert alert-danger py-2 small mb-0">
          Couldn&apos;t load marketplaces. <button className="btn btn-link btn-sm p-0 align-baseline" onClick={refresh}>Retry</button>
        </div>
      ) : listQ.isLoading ? (
        <div className="text-muted small py-3">Loading marketplaces…</div>
      ) : codes.length === 0 ? (
        <div className="text-muted small py-3">No marketplaces available.</div>
      ) : (
        <>
          {codes.map((code) => {
            const h = healthFor(health, code)
            const badge = healthBadge(h)
            const usable = isUsable(h)
            return (
              <div className="d-flex align-items-center justify-content-between gap-3 py-3 border-bottom" key={code}>
                <div className="d-flex align-items-center gap-3">
                  <div className={`icon-box flex-shrink-0 ${usable ? 'icon-grad-primary' : ''}`}
                    style={usable ? undefined : { background: 'var(--app-bg)', color: 'var(--text-secondary)' }}>
                    <FiShoppingBag />
                  </div>
                  <div>
                    <div className="fw-semibold d-flex align-items-center gap-2">
                      {mpLabel(code)}
                      {usable
                        ? <span className="badge text-bg-light text-success d-inline-flex align-items-center gap-1"><FiSearch size={11} /> Included in your searches</span>
                        : <span className="text-muted small">Temporarily unavailable</span>}
                    </div>
                  </div>
                </div>
                <div className="d-flex align-items-center gap-2">
                  <StatusBadge status={badge.tone} label={badge.label} />
                </div>
              </div>
            )
          })}
          {checkedAt && (
            <div className="form-text mt-2">Last checked {new Date(checkedAt).toLocaleString()}.</div>
          )}
        </>
      )}
    </Card>
  )
}

// Change password — wired to POST /auth/change-password (ChangePasswordRequest).
function SecurityTab() {
  const { notify } = useNotification()
  const { register, handleSubmit, watch, reset, clearErrors, formState: { errors, isSubmitting } } = useForm()
  const newPassword = watch('newPassword')

  const onSubmit = async (v) => {
    try {
      await authService.changePassword({ currentPassword: v.currentPassword, newPassword: v.newPassword })
      notify.success('Password updated')
      reset()
    } catch (e) {
      notify.error(e.message || 'Could not update password')
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
function TwoFactorSettings() {
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
