import { FiChevronLeft, FiChevronRight } from 'react-icons/fi'

export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null

  // Windowed page list: always show first, last, and neighbours of current.
  const visible = Array.from({ length: totalPages }, (_, i) => i + 1)
    .filter((p) => p === 1 || p === totalPages || Math.abs(p - page) <= 1)

  // Build render items, inserting an ellipsis marker where numbers are skipped.
  const items = []
  visible.forEach((p, i) => {
    if (i > 0 && p - visible[i - 1] > 1) items.push({ ellipsis: true, key: `e${p}` })
    items.push({ page: p, key: p })
  })

  return (
    <nav aria-label="Pagination">
      <ul className="pagination-clean">
        <li>
          <button type="button" className="page-btn" disabled={page === 1}
            onClick={() => onChange(page - 1)} aria-label="Previous page">
            <FiChevronLeft size={15} />
            <span className="d-none d-sm-inline">Prev</span>
          </button>
        </li>

        {items.map((it) => (
          <li key={it.key}>
            {it.ellipsis ? (
              <span className="page-ellipsis" aria-hidden="true">…</span>
            ) : (
              <button type="button"
                className={`page-btn ${it.page === page ? 'is-active' : ''}`}
                aria-current={it.page === page ? 'page' : undefined}
                aria-label={`Page ${it.page}`}
                onClick={() => onChange(it.page)}>
                {it.page}
              </button>
            )}
          </li>
        ))}

        <li>
          <button type="button" className="page-btn" disabled={page === totalPages}
            onClick={() => onChange(page + 1)} aria-label="Next page">
            <span className="d-none d-sm-inline">Next</span>
            <FiChevronRight size={15} />
          </button>
        </li>
      </ul>
    </nav>
  )
}
