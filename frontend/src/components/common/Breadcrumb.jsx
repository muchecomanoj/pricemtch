import { Link } from 'react-router-dom'
import { useHomePath, toHome, useVisibleCrumbs } from '../../hooks/useHomePath'

// items: [{ label, to }] — last item is rendered as the active (non-link) crumb.
// A crumb pointing at /dashboard goes to the user's own home instead, and the
// platform owner doesn't get a leading "Home" at all (see useVisibleCrumbs).
export default function Breadcrumb({ items = [] }) {
  const home = useHomePath()
  const crumbs = useVisibleCrumbs(items)
  return (
    <nav aria-label="breadcrumb">
      <ol className="breadcrumb mb-0">
        {crumbs.map((it, i) => {
          const last = i === crumbs.length - 1
          return (
            <li key={i} className={`breadcrumb-item ${last ? 'active' : ''}`}>
              {last || !it.to ? it.label : <Link to={toHome(it.to, home)}>{it.label}</Link>}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
