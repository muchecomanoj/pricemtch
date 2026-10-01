import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { FiAlertTriangle, FiLock } from 'react-icons/fi'
import { useAuth } from '../../context/AuthContext'
import { useNotification } from '../../context/NotificationContext'
import { FREE_PLAN_CODE } from '../../utils/plans'
import { formatDate } from '../../utils/format'
import { appPath } from '../../utils/appPath'

// The company's subscription state, on every page.
//
// Two cases, and only two. A plan in its grace period still has full access,
// so it gets a warning that says how long is left. An expired plan has gone
// read-only, which is the case that needs to be impossible to miss: searching,
// adding and editing are gone, and without this the missing buttons would read
// as a bug. BLOCKED never reaches here — it signs the user out instead.
//
// The sentence is the backend's, shown as sent. It already carries the right
// dates and the right tense, and rebuilding it here would be a second copy of
// the rules to keep in step.
//
// The exception is a free plan. Its sentence talks of renewing, but Free can't
// be renewed — the trial ended, and the way on is a paid plan — so that one
// case is worded here.
function freeTrialMessage(account, readOnly) {
  if (readOnly) return 'Your free trial has ended. Choose a plan to carry on — your data is kept.'
  const days = account.graceDaysLeft
  const ended = account.paidThrough ? ` on ${formatDate(account.paidThrough)}` : ''
  return `Your free trial ended${ended}. Choose a plan within ${days} day${days === 1 ? '' : 's'} to keep full access.`
}

export default function SubscriptionBanner() {
  const { account, access, can } = useAuth()
  const { notify } = useNotification()
  const isFree = account?.plan === FREE_PLAN_CODE

  // Refused mid-session because the plan lapsed (api.js raises the event).
  // The banner appears by itself as the state changes; the toast says why the
  // thing just clicked did nothing.
  useEffect(() => {
    // A plain anchor, not a router Link: toasts render outside any page, and a
    // full navigation to My Subscription is harmless here.
    const onReadOnly = (e) => notify.warn(
      <span>
        {isFree
          ? 'Your free trial has ended. Choose a plan to use this feature.'
          : e.detail?.message || 'Your plan has expired. Renew to use this feature.'}{' '}
        <a href={appPath('/my-subscription')} className="fw-semibold text-reset">{isFree ? 'Choose a plan' : 'Renew'}</a>
      </span>,
    )
    window.addEventListener('account:read-only', onReadOnly)
    return () => window.removeEventListener('account:read-only', onReadOnly)
  }, [notify, isFree])

  if (!account) return null
  const inGrace = access === 'FULL' && account.graceDaysLeft != null
  const readOnly = access === 'READ_ONLY'
  if (!inGrace && !readOnly) return null

  // Only someone who can manage the company can renew it. Everyone else is
  // told who can, rather than shown a button that leads nowhere.
  const canRenew = can('MANAGE_COMPANY')

  return (
    <div className={`subscription-banner ${readOnly ? 'subscription-banner--locked' : ''}`} role="status">
      {readOnly ? <FiLock size={16} /> : <FiAlertTriangle size={16} />}
      <span className="flex-grow-1">
        {readOnly && <strong>Read-only. </strong>}
        {isFree ? freeTrialMessage(account, readOnly) : account.message}
        {!canRenew && (isFree ? ' Ask your company admin to choose a plan.' : ' Ask your company admin to renew.')}
      </span>
      {canRenew && (
        <Link to="/my-subscription" className="btn btn-sm btn-light fw-semibold flex-shrink-0">
          {isFree ? 'Choose a plan' : readOnly ? 'Renew / Upgrade' : 'Renew'}
        </Link>
      )}
    </div>
  )
}
