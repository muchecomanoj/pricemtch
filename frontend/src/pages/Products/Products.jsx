import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query'
import {
  FiPlus, FiEdit2, FiTrash2, FiEye, FiUpload, FiDownload, FiX, FiPackage, FiBell,
} from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Pagination from '../../components/common/Pagination'
import StatusBadge from '../../components/common/StatusBadge'
import Button from '../../components/common/Button'
import Modal from '../../components/common/Modal'
import { SkeletonRows } from '../../components/common/Skeleton'
import EmptyState from '../../components/common/EmptyState'
import ProductForm from '../../components/forms/ProductForm'
import ComboInput from '../../components/forms/ComboInput'
import SearchBar from '../../components/common/SearchBar'
import ImportWizard from '../../components/forms/ImportWizard'
import AlertRuleModal from '../../components/alerts/AlertRuleModal'
import AddProductWizard from '../../components/products/AddProductWizard'
import { productService, downloadBlob, blobErrorMessage } from '../../services/productService'
import { useNotification } from '../../context/NotificationContext'
import { shortItemId } from '../../utils/marketplaces'
import { useAuth, useWriteLock } from '../../context/AuthContext'

// Clean fallback for values the backend doesn't provide — never render "undefined".
const dash = (v) => (v === null || v === undefined || v === '' ? '—' : v)

// The identifiers users actually search by. Price/margin are deliberately not
// columns here: FR-PROD-003 excludes them from the product master, and both are
// per-channel values that belong on the Price Intelligence dashboard (FRD §13.3).
function Identifiers({ product }) {
  // eBay item number alongside the ASIN: an eBay-only product has no ASIN, and
  // leaving it out made those rows read as having no identifier at all.
  const ids = [
    ['ASIN', product.asin],
    ['eBay', product.ebayItemId ? shortItemId(product.ebayItemId) : null],
    ['GTIN', product.gtin],
  ].filter(([, v]) => v)
  if (!ids.length) return <span className="text-muted">—</span>
  return (
    <div className="d-flex flex-wrap gap-1">
      {ids.map(([label, value]) => (
        <span key={label} className="badge text-bg-light border fw-normal" style={{ fontSize: 11 }}>
          {label} <span className="font-monospace">{value}</span>
        </span>
      ))}
    </div>
  )
}

// Deterministic gradient thumbnail placeholder when a product has no image.
const THUMB_GRADS = [
  'linear-gradient(135deg,#6366f1,#8b5cf6)', 'linear-gradient(135deg,#0ea5e9,#3fa9f5)',
  'linear-gradient(135deg,#10b981,#0d9488)', 'linear-gradient(135deg,#f59e0b,#d97706)',
  'linear-gradient(135deg,#ec4899,#db2777)',
]
const isRealImg = (url) => url && !/placehold/i.test(url)

function Thumb({ product }) {
  const grad = THUMB_GRADS[[...(product.title || '')].reduce((a, c) => a + c.charCodeAt(0), 0) % THUMB_GRADS.length]
  if (isRealImg(product.image)) {
    return <img src={product.image} alt="" width={44} height={44} className="rounded-3" style={{ objectFit: 'cover' }} />
  }
  return (
    <div className="d-grid rounded-3 text-white flex-shrink-0" style={{ width: 44, height: 44, placeItems: 'center', background: grad }}>
      <FiPackage size={18} />
    </div>
  )
}

const PAGE_SIZE = 10
// Fallback only — /products/filter-options supplies the real list. Kept so the
// status filter still works if that call fails, rather than rendering empty.
const STATUSES = ['ACTIVE', 'INACTIVE', 'DRAFT', 'ARCHIVED']

export default function Products() {
  // Plain write buttons lock themselves while the plan is read-only.
  const writeLock = useWriteLock()
  const qc = useQueryClient()
  const { notify, confirm } = useNotification()
  const { can } = useAuth()
  const canManage = can('MANAGE_PRODUCTS')
  const canImport = can('IMPORT_PRODUCTS')
  // Alerts are priced-decision territory, so they carry their own permission —
  // editing the catalogue and setting what it watches are different jobs.
  const canAlert = can('MANAGE_PRICING')
  // Product, SKU, Identifiers, Category, Status, Actions (+ checkbox when manageable).
  const colCount = canManage ? 7 : 6

  const [searchInput, setSearchInput] = useState('')  // what's typed
  const [search, setSearch] = useState('')            // debounced, actually queried
  const [fCategory, setFCategory] = useState('')
  const [fBrand, setFBrand] = useState('')
  const [fStatus, setFStatus] = useState('')
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState(() => new Set())
  const [editing, setEditing] = useState(null)  // null=closed, {}=create, {..}=edit
  const [importing, setImporting] = useState(false)
  const [alerting, setAlerting] = useState(false)
  // Adding starts with a marketplace search; the manual form is the fallback.
  const [adding, setAdding] = useState(false)

  // Debounce the search box so typing doesn't fire a request per keystroke.
  useEffect(() => {
    const t = setTimeout(() => { setSearch(searchInput); setPage(1) }, 400)
    return () => clearTimeout(t)
  }, [searchInput])

  const filters = { search, category: fCategory, brand: fBrand, status: fStatus }
  const hasFilters = Object.values(filters).some(Boolean)

  // Search, filtering and paging all run on the backend — GET /products takes
  // search/status/brand/category plus page/size. keepPreviousData holds the
  // current rows while the next page loads instead of flashing a skeleton.
  const { data, isLoading } = useQuery({
    queryKey: ['products', { page, ...filters }],
    queryFn: () => productService.list({ page, size: PAGE_SIZE, ...filters }),
    placeholderData: keepPreviousData,
  })

  const rows = data?.content ?? []
  const total = data?.totalElements ?? 0
  const totalPages = data?.totalPages ?? 1

  // All three dropdowns come from one call. It returns every distinct value in
  // the tenant's catalog, so nothing is missed however large it grows.
  const { data: options } = useQuery({
    queryKey: ['products', 'filterOptions'],
    queryFn: productService.filterOptions,
  })
  const brands = options?.brands ?? []
  const categories = options?.categories ?? []
  const statuses = options?.statuses?.length ? options.statuses : STATUSES

  const clearFilters = () => {
    setSearchInput(''); setSearch(''); setFCategory(''); setFBrand(''); setFStatus(''); setPage(1)
  }

  // Exports whatever the filters currently describe — the whole matching set,
  // not just the page on screen. `page` is deliberately not sent.
  const [exporting, setExporting] = useState(false)
  const handleExport = async () => {
    setExporting(true)
    try {
      const { blob, filename } = await productService.exportCsv(filters)
      downloadBlob(blob, filename)
      notify.success(hasFilters ? `Exported ${total} products` : 'Export downloaded')
    } catch (err) {
      notify.error(await blobErrorMessage(err, 'Could not export products. Please try again.'))
    } finally {
      setExporting(false)
    }
  }

  // Selection (scoped to the current page).
  const pageIds = rows.map((r) => r.id)
  const allPageSelected = pageIds.length > 0 && pageIds.every((id) => selected.has(id))
  const toggleAll = () => setSelected((s) => {
    const next = new Set(s)
    if (allPageSelected) pageIds.forEach((id) => next.delete(id))
    else pageIds.forEach((id) => next.add(id))
    return next
  })
  const toggleOne = (id) => setSelected((s) => {
    const next = new Set(s); next.has(id) ? next.delete(id) : next.add(id); return next
  })

  const removeMut = useMutation({
    mutationFn: (id) => productService.remove(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['products'] }),
  })
  const saveMut = useMutation({
    mutationFn: (values) => (values.id ? productService.update(values.id, values) : productService.create(values)),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['products'] }); notify.success('Product saved'); setEditing(null) },
    // Without this the rejection is swallowed: the spinner stops, the modal
    // stays open and the user cannot tell a failed save from a hung one.
    // api.js already copies the backend's message onto error.message.
    onError: (err) => notify.error(err?.message || 'Could not save the product.'),
  })

  // mutateAsync rejects on failure, so every call site needs a catch — otherwise
  // the delete silently does nothing and the row stays on screen unexplained.
  const handleDelete = async (row) => {
    if (await confirm({ text: `Delete "${row.title}"? This can't be undone.` })) {
      try {
        await removeMut.mutateAsync(row.id)
        setSelected((s) => { const n = new Set(s); n.delete(row.id); return n })
        notify.success('Product deleted')
      } catch (err) {
        notify.error(err?.message || 'Could not delete the product.')
      }
    }
  }
  const bulkDelete = async () => {
    if (!selected.size) return
    if (await confirm({ text: `Delete ${selected.size} selected product${selected.size > 1 ? 's' : ''}?` })) {
      // allSettled so one failure doesn't abandon the rest mid-way.
      const outcomes = await Promise.allSettled([...selected].map((id) => removeMut.mutateAsync(id)))
      const failed = outcomes.filter((o) => o.status === 'rejected').length
      setSelected(new Set())
      if (failed) notify.error(`${failed} of ${outcomes.length} product${outcomes.length > 1 ? 's' : ''} could not be deleted.`)
      else notify.success('Products deleted')
    }
  }

  return (
    <>
      <PageHeader
        title="Products"
        subtitle="Manage and monitor your product catalog."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'Products' }]}
        actions={
          <>
            {canImport && <Button write variant="light" icon={FiUpload} onClick={() => setImporting(true)}>Import CSV</Button>}
            {/* Says how many rows it will produce, so nobody exports 12 filtered
                products expecting the whole catalogue — or the reverse. */}
            <Button variant="light" icon={FiDownload} loading={exporting} onClick={handleExport}>
              {hasFilters ? `Export ${total}` : 'Export'}
            </Button>
            {canManage && <Button write icon={FiPlus} onClick={() => setAdding(true)}>Add Product</Button>}
          </>
        }
      />

      <Card bodyClassName="p-0">
        {/* Toolbar */}
        <div className="p-3 border-bottom">
          <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-3">
            <h6 className="fw-bold mb-0">
              {hasFilters
                ? `${total} matching product${total === 1 ? '' : 's'}`
                : `All products (${total})`}
            </h6>
            {selected.size > 0 && (
              <div className="d-flex align-items-center gap-2 px-2 py-1 rounded-3"
                style={{ background: 'var(--hover-soft)', border: '1px solid var(--border-soft)' }}>
                <span className="small fw-semibold">{selected.size} selected</span>
                {canAlert && (
                  <button className="btn btn-sm d-inline-flex align-items-center gap-1 fw-semibold"
                    onClick={() => setAlerting(true)} {...writeLock}
                    style={{ background: 'var(--hover-soft)', border: '1px solid var(--border-soft)' }}>
                    <FiBell size={13} /> Create alert
                  </button>
                )}
                {canManage && (
                  <button className="btn btn-sm d-inline-flex align-items-center gap-1 fw-semibold" onClick={bulkDelete} {...writeLock}
                    style={{ background: 'rgba(239,68,68,0.1)', color: 'var(--danger)', border: '1px solid rgba(239,68,68,0.25)' }}>
                    <FiTrash2 size={13} /> Delete
                  </button>
                )}
                <button className="btn btn-sm btn-link text-decoration-none p-0 px-1 text-muted" onClick={() => setSelected(new Set())}>Clear</button>
              </div>
            )}
          </div>

          <div className="d-flex flex-wrap gap-2">
            <SearchBar grow value={searchInput} onChange={setSearchInput}
              placeholder="Search products by name, SKU, brand…" />
            {/* strict: a filter must hold a value that actually exists, so
                typing only searches the list rather than becoming the filter. */}
            <div style={{ width: 180 }}>
              <ComboInput value={fCategory} onChange={(v) => { setFCategory(v); setPage(1) }}
                options={categories} placeholder="All categories"
                emptyLabel="All categories" strict clearable />
            </div>
            <div style={{ width: 170 }}>
              <ComboInput value={fBrand} onChange={(v) => { setFBrand(v); setPage(1) }}
                options={brands} placeholder="All brands"
                emptyLabel="All brands" strict clearable />
            </div>
            <select className="form-select" style={{ maxWidth: 150 }} value={fStatus} onChange={(e) => { setFStatus(e.target.value); setPage(1) }}>
              <option value="">All statuses</option>
              {statuses.map((s) => <option key={s}>{s}</option>)}
            </select>
            {hasFilters && (
              <Button variant="light" icon={FiX} onClick={clearFilters}>Clear filters</Button>
            )}
          </div>
        </div>

        {/* Table */}
        <div className="table-responsive">
          <table className="table table-hover align-middle mb-0">
            <thead>
              <tr className="text-muted small text-uppercase">
                {canManage && (
                  <th style={{ width: 40 }}>
                    <input type="checkbox" className="form-check-input" checked={allPageSelected} onChange={toggleAll} />
                  </th>
                )}
                {/* text-truncate needs a bound to truncate against; without a
                    width here the column stretches to the longest title and
                    pushes Status and Actions off the viewport. */}
                <th style={{ width: '38%', minWidth: 260 }}>Product</th>
                <th className="text-nowrap">SKU</th>
                <th>Identifiers</th>
                <th style={{ maxWidth: 160 }}>Category</th>
                <th className="text-nowrap">Status</th>
                <th className="text-end text-nowrap">Actions</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr><td colSpan={colCount}><SkeletonRows rows={6} /></td></tr>
              ) : rows.length === 0 ? (
                <tr><td colSpan={colCount}>
                  <EmptyState message={hasFilters ? 'No products match your filters.' : 'No products yet. Add your first product to get started.'} />
                </td></tr>
              ) : rows.map((r) => (
                <tr key={r.id}>
                  {canManage && (
                    <td><input type="checkbox" className="form-check-input" checked={selected.has(r.id)} onChange={() => toggleOne(r.id)} /></td>
                  )}
                  <td style={{ maxWidth: 0 }}>
                    <Link to={`/products/${r.id}`} className="d-flex align-items-center gap-3 text-reset text-decoration-none">
                      <Thumb product={r} />
                      <div style={{ minWidth: 0 }}>
                        {/* Full name on hover, since the visible text is clipped. */}
                        <div className="fw-semibold text-truncate" title={r.title || ''}>{dash(r.title)}</div>
                        <div className="text-muted small text-truncate">{dash(r.brand)}</div>
                      </div>
                    </Link>
                  </td>
                  <td><span className="small font-monospace text-muted text-nowrap d-block">{dash(r.sku)}</span></td>
                  <td><Identifiers product={r} /></td>
                  <td style={{ maxWidth: 160 }}><span className="d-block text-truncate" title={r.category || ''}>{dash(r.category)}</span></td>
                  <td>{r.status ? <StatusBadge status={r.status} /> : '—'}</td>
                  <td className="text-end">
                    <div className="d-inline-flex gap-1">
                      <Link to={`/products/${r.id}`} className="icon-btn" title="View details"><FiEye /></Link>
                      {canManage && <button className="icon-btn" title="Edit" onClick={() => setEditing(r)} {...writeLock}><FiEdit2 /></button>}
                      {canManage && <button className="icon-btn text-danger" title="Delete" onClick={() => handleDelete(r)} {...writeLock}><FiTrash2 /></button>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Footer: showing count + pagination (matches the Clients list) */}
        {!isLoading && total > 0 && (
          <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 p-3 border-top">
            <span className="text-muted small">
              Showing <strong>{rows.length ? (page - 1) * PAGE_SIZE + 1 : 0}</strong>–
              <strong>{(page - 1) * PAGE_SIZE + rows.length}</strong> of <strong>{total}</strong>
            </span>
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </div>
        )}
      </Card>

      {/* xl (1140px) rather than lg (800px) — the form carries four sections and
          three-across rows, which cramp badly at 800. */}
      <Modal show={editing !== null} title={editing?.id ? 'Edit Product' : 'Add Product'} onClose={() => setEditing(null)} size="xl" scrollable>
        {editing !== null && (
          <ProductForm defaultValues={editing} submitting={saveMut.isPending}
            onSubmit={(v) => saveMut.mutate({ ...editing, ...v })} onCancel={() => setEditing(null)} />
        )}
      </Modal>

      <ImportWizard show={importing} onClose={() => setImporting(false)}
        onComplete={() => qc.invalidateQueries({ queryKey: ['products'] })} />

      {/* Selection survives the modal closing, so the same set can be given a
          second rule without picking it all again. */}
      <AlertRuleModal show={alerting} onClose={() => setAlerting(false)} productIds={[...selected]} />

      {/* Search first, type second: an identifier copied from a listing is
          right, and one typed from memory frequently is not. */}
      <AddProductWizard show={adding} onClose={() => setAdding(false)}
        onManual={() => setEditing({})} />
    </>
  )
}
