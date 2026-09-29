// Tiny dependency-free SVG sparkline for KPI cards.
export default function Sparkline({ data, color = '#3fa9f5', width = 96, height = 34 }) {
  const pts = data && data.length > 1 ? data : [4, 6, 5, 8, 7, 9, 8, 11]
  const min = Math.min(...pts)
  const max = Math.max(...pts)
  const span = max - min || 1
  const step = width / (pts.length - 1)

  const coords = pts.map((v, i) => [i * step, height - ((v - min) / span) * (height - 6) - 3])
  const line = coords.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ')
  const area = `${line} L${width},${height} L0,${height} Z`
  const id = `spark-${color.replace('#', '')}`

  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} className="d-block">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={color} stopOpacity="0.28" />
          <stop offset="100%" stopColor={color} stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d={area} fill={`url(#${id})`} />
      <path d={line} fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
