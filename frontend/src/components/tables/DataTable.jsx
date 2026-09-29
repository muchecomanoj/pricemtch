import { SkeletonRows } from '../common/Skeleton'
import EmptyState from '../common/EmptyState'

// columns: [{ key, header, render?(row), className?, width?, minWidth? }]
//
// `width`/`minWidth` are sized on the header only — the browser applies a
// column's width to the whole column, and without them a table with many
// columns steals space from the widest text cell and wraps it one word per
// line. The wrapper scrolls, so a min-width can exceed the container.
// Optional row selection: pass `selected` (a Set of ids) plus onToggleRow and
// onToggleAll and a checkbox column appears in front. Left out entirely when
// those are absent, so every existing table renders exactly as before.
export default function DataTable({
  columns, rows, loading, emptyMessage = 'No records found.',
  selected, onToggleRow, onToggleAll, rowId = (r) => r.id,
}) {
  const selectable = Boolean(selected && onToggleRow && onToggleAll)
  const allSelected = selectable && rows.length > 0 && rows.every((r) => selected.has(rowId(r)))
  const colSpan = columns.length + (selectable ? 1 : 0)

  return (
    <div className="table-responsive">
      <table className="table table-hover align-middle mb-0">
        <thead>
          <tr className="text-muted small text-uppercase">
            {selectable && (
              <th style={{ width: 40 }}>
                <input type="checkbox" className="form-check-input" aria-label="Select all rows"
                  checked={allSelected} onChange={onToggleAll} />
              </th>
            )}
            {columns.map((c) => (
              <th key={c.key} className={c.className}
                style={c.width || c.minWidth ? { width: c.width, minWidth: c.minWidth } : undefined}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr><td colSpan={colSpan}><SkeletonRows rows={6} /></td></tr>
          ) : rows.length === 0 ? (
            <tr><td colSpan={colSpan}><EmptyState message={emptyMessage} /></td></tr>
          ) : (
            rows.map((row, i) => (
              <tr key={row.id ?? i}>
                {selectable && (
                  <td>
                    <input type="checkbox" className="form-check-input" aria-label="Select row"
                      checked={selected.has(rowId(row))} onChange={() => onToggleRow(rowId(row))} />
                  </td>
                )}
                {columns.map((c) => (
                  <td key={c.key} className={c.className}>
                    {c.render ? c.render(row) : row[c.key]}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}
