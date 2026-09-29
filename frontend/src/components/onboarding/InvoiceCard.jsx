import { FiDownload, FiFileText } from 'react-icons/fi'
import Card from '../common/Card'
import { formatCurrency } from '../../utils/format'

// Post-payment receipt summary with a download action.
export default function InvoiceCard({ invoice, onDownload }) {
  if (!invoice) return null
  const rows = [
    ['Transaction ID', invoice.transactionId],
    ['Plan', invoice.planName],
    ['Billing cycle', invoice.cycleLabel],
    ['Payment method', invoice.method],
    ['Paid on', invoice.paidOn],
    ['Renews on', invoice.renewsOn],
  ]

  return (
    <Card>
      <div className="d-flex align-items-center gap-3 mb-3">
        <div className="icon-box icon-grad-success"><FiFileText /></div>
        <div className="flex-grow-1">
          <div className="fw-semibold">Invoice {invoice.number}</div>
          <div className="text-muted small">Subscription payment receipt</div>
        </div>
        <div className="h5 mb-0 fw-bold">{formatCurrency(invoice.total, invoice.currency)}</div>
      </div>
      <div className="row g-2 small">
        {rows.map(([k, v]) => (
          <div className="col-12 col-sm-6 d-flex justify-content-between border-bottom py-1" key={k}>
            <span className="text-muted">{k}</span>
            <span className="fw-semibold text-end">{v}</span>
          </div>
        ))}
      </div>
      <button className="btn btn-light btn-sm mt-3 d-inline-flex align-items-center gap-2" onClick={onDownload}>
        <FiDownload /> Download invoice
      </button>
    </Card>
  )
}
