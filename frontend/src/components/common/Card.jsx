// Reusable surface. Backward-compatible props (title, actions, children,
// className, bodyClassName) plus optional subtitle + hover lift.
export default function Card({
  title, subtitle, actions, children, className = '', bodyClassName = '', hover = false,
}) {
  return (
    <div className={`card ${hover ? 'card-hover' : ''} ${className}`}>
      {(title || actions) && (
        // Wraps on a phone: the actions drop under the title instead of
        // squeezing it into a column one word wide.
        <div className="card-header d-flex flex-wrap justify-content-between align-items-center gap-2">
          <div style={{ minWidth: 0 }}>
            {title && <h6 className="mb-0 fw-semibold">{title}</h6>}
            {subtitle && <div className="text-muted small mt-1">{subtitle}</div>}
          </div>
          {actions && <div className="d-flex flex-wrap align-items-center gap-2">{actions}</div>}
        </div>
      )}
      <div className={`card-body ${bodyClassName}`}>{children}</div>
    </div>
  )
}
