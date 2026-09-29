// Controlled Bootstrap modal (rendered inline; no bootstrap JS needed).
//
// `subheader` is a fixed strip under the title, outside the scrolling body —
// for the one or two facts that must stay visible while the body scrolls. A
// sticky element inside the body cannot do this job: rows slide under it and
// reappear in the padding above it.
export default function Modal({
  show, title, onClose, children, footer, size = '', scrollable = false, subheader,
}) {
  if (!show) return null
  return (
    <>
      <div className="modal fade show d-block" tabIndex={-1} role="dialog">
        <div className={`modal-dialog modal-dialog-centered ${scrollable ? 'modal-dialog-scrollable' : ''} ${size ? `modal-${size}` : ''}`}>
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">{title}</h5>
              <button type="button" className="btn-close" onClick={onClose} />
            </div>
            {subheader && (
              <div className="px-3 py-2 border-bottom flex-shrink-0"
                style={{ background: 'var(--app-bg)' }}>
                {subheader}
              </div>
            )}
            <div className="modal-body">{children}</div>
            {footer && <div className="modal-footer">{footer}</div>}
          </div>
        </div>
      </div>
      <div className="modal-backdrop fade show" onClick={onClose} />
    </>
  )
}
