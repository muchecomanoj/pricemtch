export default function Field({ label, value }) {
  return (
    <div className="col-6 col-md-3">
      <div className="text-muted small">{label}</div>
      <div className="fw-semibold">{value || '—'}</div>
    </div>
  )
}
