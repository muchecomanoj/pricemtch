import { Link } from 'react-router-dom'

// items: [{ label, to }] — last item is rendered as the active (non-link) crumb.
export default function Breadcrumb({ items = [] }) {
  return (
    <nav aria-label="breadcrumb">
      <ol className="breadcrumb mb-0">
        {items.map((it, i) => {
          const last = i === items.length - 1
          return (
            <li key={i} className={`breadcrumb-item ${last ? 'active' : ''}`}>
              {last || !it.to ? it.label : <Link to={it.to}>{it.label}</Link>}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
