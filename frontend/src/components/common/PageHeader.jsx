import { useNavigate, useLocation } from 'react-router-dom'
import { FiArrowLeft } from 'react-icons/fi'
import Breadcrumb from './Breadcrumb'

// `back` controls the arrow shown left of the breadcrumb:
//   undefined → derive the parent from the breadcrumb (the default)
//   false     → hide it (top-level screens that have nowhere to go back to)
//   '/path'   → force a specific destination
export default function PageHeader({ title, subtitle, breadcrumb, actions, back }) {
  const navigate = useNavigate()
  const { pathname } = useLocation()

  // Parent = the last LINKED crumb before the current one, so every nested page
  // gets a sensible target without having to declare it. A crumb pointing at the
  // page you are already on (Dashboard's "Home") is not a parent.
  const parent = back === false
    ? null
    : typeof back === 'string'
      ? { to: back }
      : [...(breadcrumb || [])].slice(0, -1).reverse()
        .find((c) => c.to && c.to !== pathname)

  const goBack = () => {
    // Prefer real history so Back returns exactly where the user came from.
    // On a direct load or fresh tab there is none, so use the breadcrumb parent
    // rather than leaving the app.
    if (window.history.state?.idx > 0) navigate(-1)
    else if (parent?.to) navigate(parent.to)
  }

  return (
    <div className="mb-4">
      {(parent || breadcrumb) && (
        <div className="d-flex align-items-center gap-2">
          {parent && (
            <button type="button" className="icon-btn flex-shrink-0"
              style={{ width: 32, height: 32, borderRadius: '0.55rem' }}
              onClick={goBack} title="Go back" aria-label="Go back">
              <FiArrowLeft size={15} />
            </button>
          )}
          {breadcrumb && <Breadcrumb items={breadcrumb} />}
        </div>
      )}

      {(title || actions) && (
        <div className="d-flex flex-wrap justify-content-between align-items-center gap-2 mt-2">
          <div>
            {title && (
              <h3 className="section-title fw-bold mb-0" style={{ fontSize: '1.6rem' }}>{title}</h3>
            )}
            {subtitle && <p className="text-muted mb-0 mt-1">{subtitle}</p>}
          </div>
          {actions && <div className="d-flex flex-wrap gap-2">{actions}</div>}
        </div>
      )}
    </div>
  )
}
