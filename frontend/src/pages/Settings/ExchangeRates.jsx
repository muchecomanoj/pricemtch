import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FiPlus, FiTrash2, FiRepeat, FiInfo } from 'react-icons/fi'
import { useAuth } from '../../context/AuthContext'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Modal from '../../components/common/Modal'
import DataTable from '../../components/tables/DataTable'
import EmptyState from '../../components/common/EmptyState'
import { fxRateService, rateExample } from '../../services/fxRateService'
import { useNotification } from '../../context/NotificationContext'
import { formatDate } from '../../utils/format'

// FR-PROFIT-001's stored FX rate.
//
// Every price already carried a currency code and nothing used it: a Canadian
// listing at C$95 and a US one at $70 were sorted together and the median wore
// whichever currency the first row happened to have. That number looked
// authoritative, meant nothing, and fed the recommendation engine — so it was
// setting real prices.

const CURRENCIES = ['USD', 'CAD', 'GBP', 'EUR', 'AUD', 'JPY', 'INR', 'MXN', 'BRL', 'SGD']

// Today, as YYYY-MM-DD, for the date input's default.
const today = () => new Date().toISOString().slice(0, 10)

const EMPTY = { baseCurrency: 'CAD', quoteCurrency: 'USD', rate: '', asOf: today(), source: 'MANUAL' }

export default function ExchangeRates() {
  // Viewable while the plan is expired; nothing here can be changed until it is renewed.
  const { readOnly } = useAuth()
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState(EMPTY)

  const { data: rates = [], isLoading } = useQuery({
    queryKey: ['fxRates'],
    queryFn: () => fxRateService.list(),
  })
  const { data: reporting } = useQuery({
    queryKey: ['fxReportingCurrency'],
    queryFn: () => fxRateService.reportingCurrency(),
  })

  const save = useMutation({
    mutationFn: () => fxRateService.save({
      baseCurrency: form.baseCurrency,
      quoteCurrency: form.quoteCurrency,
      rate: Number(form.rate),
      // A date, sent as the instant it was true from.
      asOf: new Date(`${form.asOf}T00:00:00Z`).toISOString(),
      source: form.source || 'MANUAL',
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['fxRates'] })
      setAdding(false)
      setForm(EMPTY)
      notify.success('Rate saved')
    },
    onError: (e) => notify.error(e?.message || 'The rate could not be saved.'),
  })

  const remove = useMutation({
    mutationFn: (id) => fxRateService.remove(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['fxRates'] })
      notify.success('Rate deleted')
    },
    onError: (e) => notify.error(e?.message || 'The rate could not be deleted.'),
  })

  const set = (patch) => setForm((f) => ({ ...f, ...patch }))
  const sameCurrency = form.baseCurrency === form.quoteCurrency
  const valid = form.rate !== '' && Number(form.rate) > 0 && !sameCurrency

  const columns = [
    {
      key: 'pair',
      header: 'Pair',
      className: 'text-nowrap',
      render: (r) => (
        <span className="fw-semibold d-inline-flex align-items-center gap-2">
          {r.baseCurrency} <FiRepeat size={12} className="text-muted" /> {r.quoteCurrency}
        </span>
      ),
    },
    {
      key: 'rate',
      header: 'Rate',
      className: 'text-nowrap',
      render: (r) => (
        <>
          <span className="fw-semibold" style={{ fontVariantNumeric: 'tabular-nums' }}>{r.rate}</span>
          <div className="text-muted" style={{ fontSize: 11 }}>
            {rateExample(r.baseCurrency, r.quoteCurrency, r.rate)}
          </div>
        </>
      ),
    },
    {
      key: 'asOf',
      // When the rate was TRUE, not when it was typed. A figure converted at a
      // rate somebody entered three months ago and one converted at today's
      // close deserve different amounts of trust, and only this column says
      // which you are looking at.
      header: 'As of',
      className: 'text-nowrap',
      render: (r) => (r.asOf ? formatDate(r.asOf) : <span className="text-muted">—</span>),
    },
    {
      key: 'source',
      header: 'Source',
      className: 'text-nowrap',
      render: (r) => <span className="mr-pill mr-pill--slate">{r.source || 'MANUAL'}</span>,
    },
    {
      key: 'actions',
      header: '',
      className: 'text-end',
      render: (r) => (
        <button className="icon-btn" disabled={readOnly} title={readOnly ? 'Renew to use this' : 'Delete rate'}
          onClick={async () => {
            const ok = await confirm({
              title: `Delete the ${r.baseCurrency} to ${r.quoteCurrency} rate?`,
              text: 'Listings in that currency will be left out of market statistics until a rate exists again.',
              confirmText: 'Delete',
            })
            if (ok) remove.mutate(r.id)
          }}>
          <FiTrash2 />
        </button>
      ),
    },
  ]

  return (
    <>
      <Card
        title={`Exchange rates (${rates.length})`}
        subtitle={reporting
          ? `Statistics are reported in ${reporting}.`
          : 'Statistics are reported in whichever currency most listings already use.'}
        actions={<Button icon={FiPlus} disabled={readOnly} title={readOnly ? 'Renew to use this' : undefined}
          onClick={() => { setForm(EMPTY); setAdding(true) }}>Add rate</Button>}
      >
        {!isLoading && rates.length === 0 ? (
          <EmptyState icon={FiRepeat} title="No rates configured"
            message="If every listing you track is already in one currency, you need none of these.
                     Add a rate only when competitor prices come back in a currency your statistics
                     are not reported in — until then nothing is being converted or left out." />
        ) : (
          <DataTable columns={columns} rows={rates} loading={isLoading} />
        )}
      </Card>

      <Modal show={adding} onClose={() => setAdding(false)} title="Add an exchange rate"
        footer={(
          <>
            <Button variant="light" onClick={() => setAdding(false)}>Cancel</Button>
            <Button loading={save.isPending} disabled={!valid} onClick={() => save.mutate()}>
              Save rate
            </Button>
          </>
        )}>

        <div className="row g-3">
          <div className="col-6">
            <label className="form-label" htmlFor="fx-base">From</label>
            <select id="fx-base" className="form-select" value={form.baseCurrency}
              onChange={(e) => set({ baseCurrency: e.target.value })}>
              {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div className="col-6">
            <label className="form-label" htmlFor="fx-quote">To</label>
            <select id="fx-quote" className="form-select" value={form.quoteCurrency}
              onChange={(e) => set({ quoteCurrency: e.target.value })}>
              {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
            {sameCurrency && (
              <div className="text-danger small mt-1">Pick two different currencies.</div>
            )}
          </div>

          <div className="col-12">
            <label className="form-label" htmlFor="fx-rate">Rate</label>
            <input id="fx-rate" type="number" min="0" step="0.0001" className="form-control"
              value={form.rate} onChange={(e) => set({ rate: e.target.value })} />
            {/* The direction stated as a sentence, because "rate" alone is
                ambiguous and entering it backwards is silent and expensive. */}
            <div className="form-text">
              {form.rate
                ? <strong>{rateExample(form.baseCurrency, form.quoteCurrency, form.rate)}</strong>
                : `How many ${form.quoteCurrency} one ${form.baseCurrency} buys.`}
            </div>
          </div>

          <div className="col-12">
            <label className="form-label" htmlFor="fx-asof">As of</label>
            <input id="fx-asof" type="date" className="form-control"
              value={form.asOf} onChange={(e) => set({ asOf: e.target.value })} />
            <div className="form-text">
              When the rate was true, not when you are entering it. Saving the same pair against the
              same date corrects that rate rather than adding a second one.
            </div>
          </div>

          <div className="col-12">
            <div className="field-note" style={{ maxWidth: 'none' }}>
              <FiInfo size={15} />
              <span>
                One direction is enough — {form.quoteCurrency} to {form.baseCurrency} is the same
                fact written backwards and does not need its own row.
              </span>
            </div>
          </div>
        </div>
      </Modal>
    </>
  )
}
