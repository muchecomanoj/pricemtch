import { useMemo } from 'react'
import { useForm, Controller } from 'react-hook-form'
import { useMutation, useQuery } from '@tanstack/react-query'
import { FiFileText, FiDownload, FiInbox } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import EmptyState from '../../components/common/EmptyState'
import ComboInput from '../../components/forms/ComboInput'
import { reportService, downloadReport } from '../../services/reportService'
import { productService } from '../../services/productService'
import { useNotification } from '../../context/NotificationContext'
import { formatRelativeTime } from '../../utils/format'
import { mpLabel } from '../../utils/marketplaces'
import { useMarketplaceCodes } from '../../hooks/useMarketplaces'

// POST /reports/generate → preview rows + the full file in one response.
// The export is every competitor listing across all products, filtered by
// marketplace, category and date range.
const FORMATS = ['CSV', 'JSON']

export default function Reports() {
  const { codes: marketplaces } = useMarketplaceCodes()
  const { notify } = useNotification()
  const { register, handleSubmit, control } = useForm({
    defaultValues: { format: 'CSV', marketplace: 'All', category: 'All' },
  })

  // Categories come from the tenant's own products so the filter can only offer
  // strings that actually exist in the data.
  const productsQ = useQuery({
    queryKey: ['products', 'selector'],
    queryFn: () => productService.list({ page: 1, size: 100 }),
  })
  const categories = useMemo(() => (
    [...new Set((productsQ.data?.content ?? []).map((p) => p.category).filter(Boolean))].sort()
  ), [productsQ.data])

  const generate = useMutation({
    mutationFn: (values) => reportService.generate(values),
    onSuccess: (r) => {
      if (r.rowCount > 0) notify.success(`${r.rowCount} rows generated`)
      else notify.info('No data matches these filters')
    },
    onError: (err) => notify.error(err?.message || 'Could not generate the report.'),
  })

  const report = generate.data

  return (
    <>
      <PageHeader title="Reports"
        subtitle="Export competitor listings across your catalog."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Reports' }]} />

      <div className="row g-3">
        <div className="col-12 col-xl-4">
          <Card title="Generate report">
            <form onSubmit={handleSubmit((v) => generate.mutate(v))} className="row g-3">
              <div className="col-md-6">
                <label className="form-label" htmlFor="r-format">Format</label>
                <select id="r-format" className="form-select" {...register('format')}>
                  {FORMATS.map((f) => <option key={f}>{f}</option>)}
                </select>
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="r-market">Marketplace</label>
                {/* Live channel list: this used to include WEB, which no
                    longer exists, so the filter sent a value the API 400s on. */}
                <select id="r-market" className="form-select" {...register('marketplace')}>
                  <option value="All">All</option>
                  {marketplaces.map((m) => <option key={m} value={m}>{mpLabel(m)}</option>)}
                </select>
              </div>

              <div className="col-12">
                {/* Data-driven list, so searchable — format and marketplace
                    above are short fixed lists and stay native selects. */}
                <label className="form-label" htmlFor="r-category">Category</label>
                <Controller name="category" control={control}
                  render={({ field }) => (
                    <ComboInput id="r-category"
                      value={field.value === 'All' ? '' : (field.value || '')}
                      onChange={(v) => field.onChange(v || 'All')}
                      options={categories} placeholder="All categories"
                      emptyLabel="All categories"
                      disabled={productsQ.isLoading} strict clearable />
                  )} />
              </div>

              <div className="col-md-6">
                <label className="form-label" htmlFor="r-from">From</label>
                <input id="r-from" type="date" className="form-control" {...register('from')} />
              </div>
              <div className="col-md-6">
                <label className="form-label" htmlFor="r-to">To</label>
                <input id="r-to" type="date" className="form-control" {...register('to')} />
              </div>

              <div className="col-12 d-grid pt-2 border-top-soft">
                <Button type="submit" icon={FiFileText} loading={generate.isPending}
                  className="justify-content-center">Generate</Button>
              </div>
            </form>
          </Card>
        </div>

        <div className="col-12 col-xl-8">
          {!report ? (
            <Card title="Result">
              <EmptyState icon={FiFileText} title="No report yet"
                message="Configure the options and generate a report to see the result here." />
            </Card>
          ) : report.rowCount === 0 ? (
            <Card title="Result">
              <EmptyState icon={FiInbox} title="No data matches these filters"
                message="Try a wider date range, or set marketplace and category back to All." />
            </Card>
          ) : (
            <Card
              title="Result"
              subtitle={report.generatedAt ? `Generated ${formatRelativeTime(report.generatedAt)}` : null}
              actions={(
                <Button icon={FiDownload} onClick={() => downloadReport(report)}>
                  Download
                </Button>
              )}
            >
              <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
                <span className="mr-pill mr-pill--blue">{report.rowCount} rows</span>
                <span className="mr-pill mr-pill--slate">{report.format}</span>
                {report.filename && (
                  <span className="text-muted small font-monospace">{report.filename}</span>
                )}
              </div>

              <div className="table-responsive" style={{ maxHeight: 460 }}>
                <table className="table table-hover align-middle mb-0">
                  <thead>
                    <tr className="text-muted small text-uppercase">
                      {report.columns.map((c) => <th key={c}>{c}</th>)}
                    </tr>
                  </thead>
                  <tbody>
                    {/* Export rows are positional arrays with no id of their
                        own, so the index is the only stable key available. */}
                    {report.rows.map((row, i) => (
                      <tr key={i}>
                        {row.map((cell, j) => (
                          <td key={j} className="small text-nowrap">{cell ?? '—'}</td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {report.rowCount > report.rows.length && (
                <p className="form-text mt-2 mb-0">
                  Showing the first {report.rows.length} of {report.rowCount} rows —
                  the download contains all of them.
                </p>
              )}
            </Card>
          )}
        </div>
      </div>
    </>
  )
}
