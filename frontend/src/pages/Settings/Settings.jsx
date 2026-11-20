import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import { useAuth } from '../../context/AuthContext'
import {
  FiShield, FiShoppingBag, FiUser, FiMail, FiSend, FiBell, FiSliders, FiRepeat,
} from 'react-icons/fi'
import { CAPABILITIES } from '../../constants'
import EmailTemplates from './EmailTemplates'
import EmailConfig from './EmailConfig'
import NotificationChannels from './NotificationChannels'
import PricingPolicies from './PricingPolicies'
import ExchangeRates from './ExchangeRates'
import ProfileTab from './ProfileTab'
import MarketplacesSettings from './MarketplacesSettings'
import SecurityTab, { TwoFactorSettings } from './SecurityTab'

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
