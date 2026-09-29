import { FiLock } from 'react-icons/fi'
import { useAuth } from '../../context/AuthContext'

// Thin wrapper over Bootstrap button classes with a loading state.
// NOTE: defaults to type="button". The browser's native default is "submit",
// which silently submits any surrounding <form>. Pass type="submit" explicitly
// on the button that is meant to submit.
//
// `write` marks a button that changes something, searches a marketplace or
// spends AI quota. While the company's plan is expired (read-only) it stays on
// screen but locked, with a lock icon and the reason — so the feature is
// visibly still there and one renewal away, rather than silently gone.
export default function Button({
  type = 'button',
  variant = 'primary',
  size,
  loading = false,
  icon: Icon,
  children,
  className = '',
  disabled = false,
  write = false,
  title,
  ...rest
}) {
  const auth = useAuth()
  const locked = write && !!auth?.readOnly
  const ShownIcon = locked ? FiLock : Icon
  return (
    <button
      type={type}
      className={`btn btn-${variant} ${size ? `btn-${size}` : ''} d-inline-flex align-items-center gap-2 ${className}`}
      {...rest}
      // After the spread, deliberately: `disabled` used to be read from rest
      // and then overwritten by it, so disabled={false} on a loading button
      // re-enabled it mid-request.
      disabled={loading || disabled || locked}
      title={locked ? 'Renew to use this' : title}
    >
      {loading && <span className="spinner-border spinner-border-sm" role="status" />}
      {!loading && ShownIcon && <ShownIcon />}
      {children}
    </button>
  )
}
