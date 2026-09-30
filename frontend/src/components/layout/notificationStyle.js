import {
  FiBell, FiInfo, FiArrowUpCircle, FiArrowDownCircle, FiClock,
  FiAlertCircle, FiCheckCircle, FiUserCheck, FiAlertTriangle, FiInbox,
} from 'react-icons/fi'

// Shared by the bell dropdown and the full Notifications page, so one
// notification looks the same in both places.

// Icon + tint per notification type. More types will be added server-side, so
// styleFor() falls back to GENERAL rather than assuming this list is complete.
export const TYPE_STYLE = {
  PLAN_ASSIGNED:            { icon: FiInfo, cls: 'text-primary', bg: 'rgba(63,169,245,0.12)' },
  PLAN_UPGRADED:            { icon: FiArrowUpCircle, cls: 'text-success', bg: 'rgba(16,185,129,0.14)' },
  PLAN_DOWNGRADE_SCHEDULED: { icon: FiClock, cls: 'text-warning', bg: 'rgba(245,158,11,0.16)' },
  PLAN_DOWNGRADED:          { icon: FiArrowDownCircle, cls: 'text-secondary', bg: 'rgba(148,163,184,0.18)' },
  PAYMENT_PENDING:          { icon: FiAlertCircle, cls: 'text-warning', bg: 'rgba(245,158,11,0.16)' },
  PAYMENT_SUCCESS:          { icon: FiCheckCircle, cls: 'text-success', bg: 'rgba(16,185,129,0.14)' },
  ACCOUNT_ACTIVATED:        { icon: FiUserCheck, cls: 'text-success', bg: 'rgba(16,185,129,0.14)' },
  // Fired by the hourly alert-rule engine; links to the product that tripped.
  ALERT_TRIGGERED:          { icon: FiAlertTriangle, cls: 'text-danger', bg: 'rgba(239,68,68,0.14)' },
  // A demo request or contact message from the public site (super admins);
  // links to that tab of Submissions.
  LEAD_RECEIVED:            { icon: FiInbox, cls: 'text-info', bg: 'rgba(14,165,233,0.14)' },
  GENERAL:                  { icon: FiBell, cls: 'text-secondary', bg: 'rgba(148,163,184,0.18)' },
}

// Plan-expiry reminders arrive as GENERAL with a link to My Subscription.
// Styled like a pending payment, because that is what they are about to become
// — in grey among routine notices, "your plan ends tomorrow" read as trivia.
// Both spellings, since this app maps the backend's older /client/subscription
// onto the same page.
const SUBSCRIPTION_LINKS = new Set(['/my-subscription', '/client/subscription'])
export const styleFor = (type, link) => (type === 'GENERAL' && SUBSCRIPTION_LINKS.has(link)
  ? TYPE_STYLE.PAYMENT_PENDING
  : TYPE_STYLE[type] || TYPE_STYLE.GENERAL)

// The backend stores an in-app path on each notification, but some of those
// paths don't match this app's routes — e.g. it sends /client/subscription
// while the route is /my-subscription, which lands the user on the 404 page.
// Translate the known mismatches here. Anything unrecognised is passed through
// unchanged so newly added backend routes keep working.
const LINK_ALIASES = {
  '/client/subscription': '/my-subscription',
  '/client/billing': '/my-subscription',
  '/subscription': '/my-subscription',
  '/client/company': '/company',
  '/client/products': '/products',
}
export function resolveLink(link) {
  if (!link) return null
  const [path, rest] = link.split(/(?=[?#])/)
  const key = path.replace(/\/+$/, '').toLowerCase()
  return (LINK_ALIASES[key] || path) + (rest || '')
}
