export default function Loader({ label = 'Loading…', center = true }) {
  return (
    <div className={`d-flex align-items-center gap-2 ${center ? 'justify-content-center py-5' : ''}`}>
      <span className="spinner-border text-primary" role="status" />
      <span className="text-muted">{label}</span>
    </div>
  )
}
