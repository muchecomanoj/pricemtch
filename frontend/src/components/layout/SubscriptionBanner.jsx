import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { FiAlertTriangle, FiLock } from 'react-icons/fi'
import { useAuth } from '../../context/AuthContext'
import { useNotification } from '../../context/NotificationContext'

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
export default function SubscriptionBanner() {
  const { account, access, can } = useAuth()
  const { notify } = useNotification()

  // Refused mid-session because the plan lapsed (api.js raises the event).
  // The banner appears by itself as the state changes; the toast says why the
  // thing just clicked did nothing.
  useEffect(() => {
    // A plain anchor, not a router Link: toasts render outside any page, and a
    // full navigation to My Subscription is harmless here.
    const onReadOnly = (e) => notify.warn(
      <span>
        {e.detail?.message || 'Your plan has expired. Renew to use this feature.'}{' '}
        <a href="/my-subscription" className="fw-semibold text-reset">Renew</a>
      </span>,
    )
    window.addEventListener('account:read-only', onReadOnly)
    return () => window.removeEventListener('account:read-only', onReadOnly)
  }, [notify])

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
        {account.message}
        {!canRenew && ' Ask your company admin to renew.'}
      </span>
      {canRenew && (
        <Link to="/my-subscription" className="btn btn-sm btn-light fw-semibold flex-shrink-0">
          {readOnly ? 'Renew / Upgrade' : 'Renew'}
        </Link>
      )}
    </div>
  )
}
