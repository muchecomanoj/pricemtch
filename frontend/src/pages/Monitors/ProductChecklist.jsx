import { useEffect, useMemo, useState } from 'react'
import { useQuery, keepPreviousData } from '@tanstack/react-query'
import { FiSearch } from 'react-icons/fi'
import { productService } from '../../services/productService'

const PAGE = 50

// Pick several products at once, for monitoring them on one schedule.
//
// Searched on the server rather than filtered from a first page: a catalogue
// runs past any page size, and a product that never loads can never be ticked.
// Ticks survive a new search, so one product can be found, ticked, and the
// next searched for. Products already monitored are shown but can't be ticked
// — the backend refuses a second monitor for the same product.
export default function ProductChecklist({ selected, onChange, monitoredIds }) {
  const [text, setText] = useState('')
  const [search, setSearch] = useState('')

  // Searched when typing pauses, not on every keystroke.
  useEffect(() => {
    const t = setTimeout(() => setSearch(text.trim()), 300)
    return () => clearTimeout(t)
  }, [text])

  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['products', 'monitorChecklist', search],
    queryFn: () => productService.list({ page: 1, size: PAGE, search }),
    placeholderData: keepPreviousData,
  })
  const products = useMemo(() => data?.content ?? [], [data])
  const total = data?.totalElements ?? products.length

  const chosen = useMemo(() => new Set(selected), [selected])
  const pickable = products.filter((p) => !monitoredIds?.has(p.id))
  const allShownPicked = pickable.length > 0 && pickable.every((p) => chosen.has(p.id))

  const toggle = (id) => onChange(chosen.has(id) ? selected.filter((x) => x !== id) : [...selected, id])
  const toggleShown = () => {
    if (allShownPicked) {
      const shown = new Set(pickable.map((p) => p.id))
      onChange(selected.filter((id) => !shown.has(id)))
    } else {
      onChange([...new Set([...selected, ...pickable.map((p) => p.id)])])
    }
  }

  return (
    <div>
      <div className="d-flex flex-wrap align-items-center justify-content-between gap-2 mb-2">
        <label className="form-label mb-0" htmlFor="monitor-product-search">Products</label>
        <span className="small text-muted">
          {selected.length} selected
          {selected.length > 0 && (
            <button type="button" className="btn btn-link btn-sm p-0 ms-2 align-baseline"
              onClick={() => onChange([])}>Clear</button>
          )}
        </span>
      </div>

      <div className="input-group input-group-sm mb-2">
        <span className="input-group-text"><FiSearch /></span>
        <input id="monitor-product-search" className="form-control" placeholder="Search by title, SKU or brand"
          value={text} onChange={(e) => setText(e.target.value)} />
      </div>

      <div className="border rounded-3" style={{ borderColor: 'var(--border-soft)', maxHeight: 260, overflowY: 'auto' }}>
        {isLoading ? (
          <div className="text-muted small p-3">Loading products…</div>
        ) : products.length === 0 ? (
          <div className="text-muted small p-3">{search ? 'No products match that search.' : 'Your catalogue has no products yet.'}</div>
        ) : (
          <>
            {pickable.length > 1 && (
              <label className="d-flex align-items-center gap-2 px-3 py-2 border-bottom small fw-semibold"
                style={{ borderColor: 'var(--border-soft)', cursor: 'pointer' }}>
                <input type="checkbox" className="form-check-input mt-0" checked={allShownPicked} onChange={toggleShown} />
                Select all {search ? 'matching' : 'shown'} ({pickable.length})
              </label>
            )}
            {products.map((p) => {
              const monitored = monitoredIds?.has(p.id)
              return (
                <label key={p.id}
                  className={`d-flex align-items-center gap-2 px-3 py-2 small ${monitored ? 'text-muted' : ''}`}
                  style={{ cursor: monitored ? 'default' : 'pointer' }}
                  title={monitored ? 'Already monitored — edit its monitor instead' : undefined}>
                  <input type="checkbox" className="form-check-input mt-0 flex-shrink-0"
                    checked={monitored || chosen.has(p.id)} disabled={monitored}
                    onChange={() => toggle(p.id)} />
                  <span className="text-truncate flex-grow-1" style={{ minWidth: 0 }}>{p.title}</span>
                  {p.sku && <span className="font-monospace text-muted flex-shrink-0">{p.sku}</span>}
                  {monitored && <span className="badge text-bg-light flex-shrink-0">Monitored</span>}
                </label>
              )
            })}
          </>
        )}
      </div>
      <div className="form-text">
        {isFetching && !isLoading ? 'Searching…'
          : total > products.length
            ? `Showing ${products.length} of ${total} — search to find the rest. Ticks are kept when you search again.`
            : 'Ticks are kept when you search again.'}
      </div>
    </div>
  )
}
