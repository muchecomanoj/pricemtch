import { useState } from 'react'
import { FiDownload } from 'react-icons/fi'
import { Line, Bar, Doughnut } from 'react-chartjs-2'
import {
  Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement,
  BarElement, ArcElement, Title, Tooltip, Legend, Filler,
} from 'chart.js'
import Card from '../common/Card'
import { Skeleton } from '../common/Skeleton'
import { useTheme } from '../../context/ThemeContext'
import { useNotification } from '../../context/NotificationContext'
import { downloadCsv, blobErrorMessage } from '../../services/downloadService'

// Register once for the whole app.
ChartJS.register(
  CategoryScale, LinearScale, PointElement, LineElement, BarElement,
  ArcElement, Title, Tooltip, Legend, Filler
)

const PALETTE = ['#3fa9f5', '#16a34a', '#f59e0b', '#dc2626', '#0ea5e9', '#8b5cf6']

// Chart.js paints to a canvas, so it cannot inherit CSS custom properties.
// Resolve the live theme tokens at render time instead — otherwise the axes
// and gridlines keep their light-mode defaults and vanish on the dark canvas.
function themeOptions(type) {
  const s = getComputedStyle(document.documentElement)
  const ink = s.getPropertyValue('--text-secondary').trim() || '#64748b'
  const grid = s.getPropertyValue('--border-soft').trim() || '#e2e8f0'
  return {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { position: 'bottom', labels: { boxWidth: 12, color: ink } } },
    // Doughnuts have no axes; giving them scales makes chart.js warn.
    ...(type === 'doughnut' ? {} : {
      scales: {
        x: { ticks: { color: ink }, grid: { color: grid }, border: { color: grid } },
        y: { ticks: { color: ink }, grid: { color: grid }, border: { color: grid } },
      },
    }),
  }
}

// Merge a caller's axis config over ours without dropping the theme colours.
const mergeAxis = (base, over) => (over
  ? { ...base, ...over, ticks: { ...base?.ticks, ...over.ticks }, grid: { ...base?.grid, ...over.grid } }
  : base)

// True when the series carry no points at all — a new company, or a month
// range with nothing in it. Chart.js would draw empty axes and no explanation.
function hasNoData(data) {
  if (!data) return true
  const series = data.datasets
    ? data.datasets.map((d) => d.data || [])
    : Object.entries(data).filter(([k]) => k !== 'labels').map(([, v]) => (Array.isArray(v) ? v : []))
  return series.every((arr) => arr.every((v) => v == null))
}

// type: 'line' | 'bar' | 'doughnut'
// `options` is shallow-merged over the defaults, so a caller can add tooltip
// callbacks or axis config without restating the base setup.
// `footer` renders inside the card, under the chart — keeping notes within the
// card so a card with a note still matches the height of one without.
export default function ChartCard({
  title, subtitle, type = 'line', data, loading, height = 260, actions, options,
  className = '', footer, emptyMessage = 'No data yet.',
  exportUrl, exportParams, exportName,
}) {
  // Subscribing to the theme is what re-runs themeOptions() on toggle —
  // without it the axes keep the colours of whichever mode loaded first.
  useTheme()
  const { notify } = useNotification()
  const [busy, setBusy] = useState(false)

  const empty = hasNoData(data)
  const onExport = async () => {
    setBusy(true)
    try {
      await downloadCsv(exportUrl, exportParams, exportName)
    } catch (err) {
      notify.error(await blobErrorMessage(err, 'Could not download the file.'))
    } finally {
      setBusy(false)
    }
  }

  const chartData = buildData(type, data)
  const Comp = type === 'bar' ? Bar : type === 'doughnut' ? Doughnut : Line
  const baseOptions = themeOptions(type)
  const chartOptions = options
    ? {
      ...baseOptions,
      ...options,
      plugins: { ...baseOptions.plugins, ...(options.plugins || {}) },
      scales: baseOptions.scales && {
        x: mergeAxis(baseOptions.scales.x, options.scales?.x),
        y: mergeAxis(baseOptions.scales.y, options.scales?.y),
        ...Object.fromEntries(
          Object.entries(options.scales || {}).filter(([k]) => k !== 'x' && k !== 'y')
        ),
      },
    }
    : baseOptions

  // No URL, no button. The icon used to render on every chart with nothing
  // attached to it, which is worse than having no icon at all.
  const headerActions = actions ?? (exportUrl
    ? (
      <button type="button" className="btn btn-ghost btn-sm"
        title={empty ? 'Nothing to export yet' : 'Download as CSV'}
        disabled={busy || loading || empty} onClick={onExport}>
        {busy
          ? <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true" />
          : <FiDownload size={15} />}
        <span className="visually-hidden">Download {title} as CSV</span>
      </button>
    )
    : null)

  return (
    <Card title={title} subtitle={subtitle} actions={headerActions} hover className={className}>
      <div style={{ height }}>
        {loading || !data ? (
          <Skeleton height={height} />
        ) : hasNoData(data) ? (
          <div className="h-100 d-flex align-items-center justify-content-center text-muted small">
            {emptyMessage}
          </div>
        ) : (
          <Comp data={chartData} options={chartOptions} />
        )}
      </div>
      {footer && <div className="form-text mt-2 mb-0">{footer}</div>}
    </Card>
  )
}

// Accepts either a preset shape from mock/data or a ready chart.js dataset.
function buildData(type, data) {
  if (!data) return { labels: [], datasets: [] }
  if (data.datasets) return data // already chart.js-shaped

  const { labels = [], values, ...series } = data

  if (type === 'doughnut') {
    return { labels, datasets: [{ data: values, backgroundColor: PALETTE }] }
  }

  if (values) {
    return {
      labels,
      datasets: [{
        label: 'Value', data: values,
        backgroundColor: type === 'bar' ? PALETTE : 'rgba(63,169,245,0.15)',
        borderColor: '#3fa9f5', borderWidth: 2, fill: type === 'line', tension: 0.35,
      }],
    }
  }

  // Multi-series line/bar (e.g. { ours: [], market: [] })
  // "ours" / "market" / "gross" are data keys; the legend is read by people.
  const datasets = Object.entries(series).map(([label, arr], idx) => ({
    label: label.charAt(0).toUpperCase() + label.slice(1), data: arr,
    borderColor: PALETTE[idx % PALETTE.length],
    backgroundColor: PALETTE[idx % PALETTE.length] + '33',
    borderWidth: 2, tension: 0.35, fill: false,
  }))
  return { labels, datasets }
}
