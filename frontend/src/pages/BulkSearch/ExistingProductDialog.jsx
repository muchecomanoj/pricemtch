import { FiAlertTriangle, FiArrowRight } from 'react-icons/fi'
import { Link } from 'react-router-dom'
import Modal from '../../components/common/Modal'
import Button from '../../components/common/Button'

// The product is already in the catalogue.
//
// Not an error — the save succeeded in finding it. The only open question is
// whether to overwrite what is there, and that is a decision, so it is asked
// rather than assumed either way. Answering no does nothing at all.
export default function ExistingProductDialog({ result, saving, onConfirm, onClose }) {
  if (!result) return null
  const diffs = result.differences || []
  // A marketplace price is an observation of someone else's listing, not a
  // considered decision about ours. Overwriting a deliberate price with one is
  // the change most likely to be regretted, so it is marked rather than
  // presented as just another differing field.
  const advisory = diffs.filter((d) => d.advisory)

  return (
    <Modal show onClose={onClose} size="lg" title="This product is already in your catalogue"
      footer={(
        <>
          <Button variant="light" onClick={onClose}>No, leave it as it is</Button>
          <Button loading={saving} onClick={onConfirm}>Yes, update it</Button>
        </>
      )}>

      <p className="text-muted small">{result.message}</p>

      <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
        <span className="fw-semibold">{result.productTitle || `Product #${result.productId}`}</span>
        {result.productSku && <span className="font-monospace text-muted small">{result.productSku}</span>}
        {result.matchedOn && <span className="text-muted small">· matched on {result.matchedOn}</span>}
        {result.productId != null && (
          <Link className="btn btn-sm btn-light d-inline-flex align-items-center gap-1 ms-auto"
            to={`/products/${result.productId}`}>
            Open it <FiArrowRight />
          </Link>
        )}
      </div>

      {diffs.length === 0 ? (
        <div className="text-muted small">Nothing would change — the values already match.</div>
      ) : (
        <>
          <div className="table-responsive">
            <table className="table table-sm align-middle mb-0">
              <thead>
                <tr>
                  <th>Field</th>
                  <th>Now</th>
                  <th>Would become</th>
                </tr>
              </thead>
              <tbody>
                {diffs.map((d, i) => (
                  <tr key={i}>
                    <td className="fw-semibold">
                      {d.field}
                      {d.advisory && (
                        <FiAlertTriangle className="text-warning ms-1" size={13}
                          title="Comes from a marketplace listing — worth a second look" />
                      )}
                    </td>
                    <td className="text-muted">{d.current ?? '—'}</td>
                    <td className={d.advisory ? 'fw-semibold text-warning-emphasis' : 'fw-semibold'}>
                      {d.incoming ?? '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {advisory.length > 0 && (
            <div className="field-note field-note--warn mt-3" style={{ maxWidth: 'none' }}>
              <FiAlertTriangle size={15} />
              <span>
                {advisory.length === 1 ? 'One field is' : `${advisory.length} fields are`} taken from a
                marketplace listing rather than from your own records
                {' — '}
                <strong>{advisory.map((d) => d.field).join(', ')}</strong>. Check
                {advisory.length === 1 ? ' it' : ' them'} before overwriting what you set deliberately.
              </span>
            </div>
          )}
        </>
      )}
    </Modal>
  )
}
