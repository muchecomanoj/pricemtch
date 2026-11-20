import { useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import Card from '../common/Card'
import Pagination from '../common/Pagination'
import DataTable from '../tables/DataTable'
import { formatCurrency } from '../../utils/format'
import { useStoredState } from '../../hooks/useStoredState'
import { errorMessage } from '../../services/apiError'

const PAGE_SIZE = 20

// All by default, PENDING included: those rows are abandoned checkouts, and
// someone with a stuck payment has to be able to find it.
const STATUSES = [['', 'All statuses'], ['PAID', 'Paid'], ['PENDING', 'Pending'], ['FAILED', 'Failed'], ['CANCELLED', 'Cancelled']]

// Exactly the values the backend's `type` parameter accepts (its OpenAPI enum);
// anything else is refused with a 400. INITIAL is the first subscription payment.
const TYPES = [
  ['', 'All types'], ['INITIAL', 'New subscription'], ['RENEWAL', 'Renewal'],
  ['UPGRADE', 'Upgrade'], ['DOWNGRADE', 'Downgrade'],
]

// Built from the parts, not new Date('2026-10-01'): that parses as UTC, and
// west of Greenwich it prints as September.
function monthLabel(month) {
  const [year, m] = month.split('-').map(Number)
  return new Date(year, m - 1, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
}

// No money figure when paidAmount is null: nothing was paid, or the set mixes
// currencies and there is no single true total to show.
function PaymentSummary({ summary }) {
  if (!summary?.totalCount) return null
  const { paidAmount, currency, paidCount, pendingCount } = summary
  const payments = `${paidCount} payment${paidCount === 1 ? '' : 's'}`
  return (
    <div className="small mb-3">
      {paidAmount != null && currency
        ? <><strong>Paid {formatCurrency(paidAmount, currency)}</strong> across {payments}</>
        : <>{paidCount} paid</>}
      {pendingCount > 0 && <> · {pendingCount} pending</>}
    </div>
  )
}

// A payment list with its month and status filters, summary line and paging.
// GET /client/payments and GET /admin/clients/{id}/payments answer alike.
//
// `fetchPage` is called with { page, size, month, status, type }. `months`
// comes from the response and is not narrowed by the filters, so a month never
// vanishes from the picker because a status or type was chosen.
export default function PaymentHistoryCard({ title, subtitle, actions, queryKey, fetchPage, columns, emptyMessage }) {
  const [month, setMonth] = useState('')
  // Status and type are standing choices, remembered per user and per list;
  // the month is not — last visit's month is rarely the one wanted next.
  const [status, setStatus] = useStoredState(`${queryKey[0]}.status`, '')
  const [type, setType] = useStoredState(`${queryKey[0]}.type`, '')
  const [page, setPage] = useState(1)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [...queryKey, { month, status, type, page }],
    queryFn: () => fetchPage({ page, size: PAGE_SIZE, month, status, type }),
    placeholderData: keepPreviousData,
  })

  const months = data?.months || []
  const statusLabel = STATUSES.find(([v]) => v === status)?.[1]
  const typeLabel = TYPES.find(([v]) => v === type)?.[1]
  const where = month ? ` in ${monthLabel(month)}` : ''
  // "No paid renewal payments in October 2026."
  const empty = month || status || type
    ? `No ${status ? `${statusLabel.toLowerCase()} ` : ''}${type ? `${typeLabel.toLowerCase()} ` : ''}payments${where}.`
    : emptyMessage

  return (
    <Card title={title} subtitle={subtitle} actions={actions}>
      {/* Hidden only when there is nothing to filter: no payment at any time. */}
      {(months.length > 0 || month || status || type) && (
        <div className="d-flex flex-wrap gap-2 mb-3">
          <select className="form-select form-select-sm w-auto" aria-label="Month" value={month}
            onChange={(e) => { setMonth(e.target.value); setPage(1) }}>
            <option value="">All time</option>
            {months.map((m) => <option key={m} value={m}>{monthLabel(m)}</option>)}
          </select>
          <select className="form-select form-select-sm w-auto" aria-label="Status" value={status}
            onChange={(e) => { setStatus(e.target.value); setPage(1) }}>
            {STATUSES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
          <select className="form-select form-select-sm w-auto" aria-label="Type" value={type}
            onChange={(e) => { setType(e.target.value); setPage(1) }}>
            {TYPES.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
          </select>
        </div>
      )}

      {isError ? (
        <div className="text-danger small">{errorMessage(error, 'Payments could not be loaded.')}</div>
      ) : (
        <>
          <PaymentSummary summary={data?.summary} />
          <DataTable columns={columns} rows={data?.payments?.content || []} loading={isLoading}
            emptyMessage={empty} />
          {(data?.payments?.totalPages ?? 1) > 1 && (
            <div className="d-flex justify-content-end mt-3 pt-3 border-top-soft">
              <Pagination page={page} totalPages={data.payments.totalPages} onChange={setPage} />
            </div>
          )}
        </>
      )}
    </Card>
  )
}
