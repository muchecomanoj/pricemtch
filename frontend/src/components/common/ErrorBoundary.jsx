import { Component } from 'react'
import { FiAlertTriangle, FiRefreshCw, FiCopy } from 'react-icons/fi'

// The app had no error boundary, so any throw during render unmounted the whole
// tree and left a white page — no message, no stack, nothing to report. Every
// crash looked identical and none of them could be diagnosed from the screen.
//
// React only offers this as a class; there is no hook equivalent for
// componentDidCatch. It catches render, lifecycle and constructor errors —
// NOT errors inside event handlers or async callbacks, which reach
// window.onerror instead and are handled by the toast layer.
export default class ErrorBoundary extends Component {
  constructor(props) {
    super(props)
    this.state = { error: null, info: null }
  }

  static getDerivedStateFromError(error) {
    return { error }
  }

  componentDidCatch(error, info) {
    this.setState({ info })
    // Kept: the console trace is what a developer opens first, and swallowing
    // it here would trade one silent failure for another.
    console.error('Unhandled render error:', error, info?.componentStack)
  }

  componentDidUpdate(prev) {
    // A different page should get a clean slate — otherwise one bad screen
    // holds the error state over every subsequent navigation.
    if (this.state.error && prev.resetKey !== this.props.resetKey) {
      this.setState({ error: null, info: null })
    }
  }

  copy = () => {
    const { error, info } = this.state
    const text = [error?.message, error?.stack, info?.componentStack].filter(Boolean).join('\n\n')
    navigator.clipboard?.writeText(text)
  }

  render() {
    const { error, info } = this.state
    if (!error) return this.props.children

    return (
      <div className="p-4">
        <div className="card mx-auto" style={{ maxWidth: 760 }}>
          <div className="card-body">
            <div className="d-flex align-items-center gap-2 mb-2">
              <FiAlertTriangle className="text-danger" size={22} />
              <h5 className="mb-0">This screen stopped working</h5>
            </div>
            <p className="text-muted small">
              Something on this page threw an error while rendering. Your data is not affected —
              anything already saved is safe. The details below are what a developer needs.
            </p>

            <div className="alert alert-danger py-2 small font-monospace mb-3"
              style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {error.message || String(error)}
            </div>

            <details className="mb-3">
              <summary className="small fw-semibold" style={{ cursor: 'pointer' }}>
                Where it happened
              </summary>
              <pre className="small text-muted mt-2 mb-0"
                style={{ whiteSpace: 'pre-wrap', maxHeight: 260, overflow: 'auto' }}>
                {info?.componentStack || error.stack || 'No stack available.'}
              </pre>
            </details>

            <div className="d-flex flex-wrap gap-2">
              <button className="btn btn-sm btn-primary d-inline-flex align-items-center gap-1"
                onClick={() => window.location.reload()}>
                <FiRefreshCw size={14} /> Reload the page
              </button>
              <button className="btn btn-sm btn-light d-inline-flex align-items-center gap-1"
                onClick={this.copy}>
                <FiCopy size={14} /> Copy the details
              </button>
            </div>
          </div>
        </div>
      </div>
    )
  }
}
