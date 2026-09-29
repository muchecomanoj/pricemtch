// Loading skeletons. `rows` renders stacked bars; use `width`/`height` for one block.
export function Skeleton({ width = '100%', height = 16, className = '' }) {
  return <div className={`skeleton ${className}`} style={{ width, height }} />
}

export function SkeletonRows({ rows = 5, height = 16, gap = 10 }) {
  return (
    <div className="d-flex flex-column" style={{ gap }}>
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} height={height} width={`${90 - (i % 3) * 15}%`} />
      ))}
    </div>
  )
}

export function SkeletonCard() {
  return (
    <div className="card p-3">
      <Skeleton width="40%" height={14} className="mb-3" />
      <Skeleton width="70%" height={28} />
    </div>
  )
}
