// Small pieces shared by the invitation onboarding and self sign-up flows.
// Identical in both, so they live here once.

// Labelled text input in a Bootstrap grid column, wired to react-hook-form.
export const Input = ({ col = 6, label, reg, err, required, ...rest }) => (
  <div className={`col-12 col-md-${col}`}>
    <label className="form-label">{label}{required && <span className="text-danger ms-1">*</span>}</label>
    <input className={`form-control ${err ? 'is-invalid' : ''}`} {...reg} {...rest} />
    {err && <div className="invalid-feedback">{err.message}</div>}
  </div>
)

// One label / value line in an order summary.
export const Row = ({ label, value, success, muted }) => (
  <div className="d-flex justify-content-between py-1 small">
    <span className="text-muted">{label}</span>
    <span className={`fw-semibold ${success ? 'text-success' : ''} ${muted ? 'text-muted' : ''}`}>{value}</span>
  </div>
)
