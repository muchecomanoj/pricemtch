import { useState, useRef, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { FiSend, FiCpu, FiAlertCircle } from 'react-icons/fi'
import PageHeader from '../../components/common/PageHeader'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import { useAuth } from '../../context/AuthContext'
import { aiService } from '../../services/aiService'

// GET /ai/analyst/suggestions + POST /ai/analyst/ask
//
// Deterministic Q&A — the backend computes answers from stored costs, prices
// and competitor listings, so there is no streaming and nothing is generated.
// `answer` is display-ready text with \n line breaks; `items` is an optional
// structured breakdown of the same figures.
const MAX_QUESTION = 500
const GREETING = {
  role: 'analyst',
  answer: 'Hi! Ask me about pricing, competitors, or margins. I answer only from stored evidence.',
}

export default function AIAnalyst() {
  // Every question is an AI call, which a lapsed plan no longer covers.
  const { readOnly } = useAuth()
  const [messages, setMessages] = useState([GREETING])
  const [input, setInput] = useState('')
  const [typing, setTyping] = useState(false)
  const endRef = useRef(null)

  const suggestions = useQuery({
    queryKey: ['analystSuggestions'],
    queryFn: aiService.analystSuggestions,
  })

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }) }, [messages, typing])

  const send = async (text) => {
    const q = (text ?? input).trim().slice(0, MAX_QUESTION)
    if (!q || typing) return
    setMessages((m) => [...m, { role: 'user', answer: q }])
    setInput('')
    setTyping(true)
    try {
      const reply = await aiService.analystAsk(q)
      setMessages((m) => [...m, { role: 'analyst', ...reply }])
    } catch (err) {
      // Keep failures in the transcript — a toast would vanish and leave the
      // conversation looking like the question was never asked.
      setMessages((m) => [...m, {
        role: 'analyst',
        error: true,
        answer: err?.message || 'Something went wrong. Please try again.',
      }])
    } finally {
      setTyping(false)
    }
  }

  const chips = suggestions.data ?? []

  return (
    <>
      <PageHeader title="AI Analyst"
        subtitle="Answers computed from your stored costs, prices and competitor listings."
        breadcrumb={[{ label: 'Home', to: '/dashboard' }, { label: 'AI Analyst' }]} />

      <div className="row g-3">
        <div className="col-12 col-lg-3">
          <Card title="Suggested questions">
            {suggestions.isLoading ? (
              <div className="d-flex flex-column gap-2">
                {[0, 1, 2, 3].map((i) => <div key={i} className="skeleton" style={{ height: 34 }} />)}
              </div>
            ) : chips.length === 0 ? (
              <p className="text-muted small mb-0">No suggestions available.</p>
            ) : (
              <div className="d-flex flex-column gap-2">
                {chips.map((q) => (
                  <button key={q} type="button" className="btn btn-light text-start small"
                    disabled={typing || readOnly} onClick={() => send(q)}>{q}</button>
                ))}
              </div>
            )}
            <p className="form-text mt-3 mb-0">
              For a specific product, include its name or SKU in the question.
            </p>
          </Card>
        </div>

        <div className="col-12 col-lg-9">
          <Card bodyClassName="d-flex flex-column p-0" className="h-100">
            <div className="p-3 flex-grow-1" style={{ minHeight: 420, maxHeight: '60vh', overflowY: 'auto' }}>
              {messages.map((m, i) => (
                <Message key={i} m={m} />
              ))}
              {typing && (
                <div className="chat-bubble ai">
                  <span className="spinner-grow spinner-grow-sm me-1" /> thinking…
                </div>
              )}
              <div ref={endRef} />
            </div>

            <div className="border-top p-3">
              <div className="input-group">
                <input
                  className="form-control"
                  placeholder="Ask the AI analyst…"
                  maxLength={MAX_QUESTION}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => e.key === 'Enter' && send()}
                />
                <Button icon={FiSend} onClick={() => send()} disabled={typing || !input.trim() || readOnly}
                  title={readOnly ? 'Renew to use this' : undefined}>Send</Button>
              </div>
              {input.length > MAX_QUESTION - 60 && (
                <div className="form-text text-end">{input.length} / {MAX_QUESTION}</div>
              )}
            </div>
          </Card>
        </div>
      </div>
    </>
  )
}

function Message({ m }) {
  if (m.role === 'user') {
    return <div className="chat-bubble user">{m.answer}</div>
  }
  return (
    <div className={`chat-bubble ai ${m.error ? 'border border-danger' : ''}`}>
      <div className="d-flex align-items-start gap-2">
        {m.error
          ? <FiAlertCircle className="text-danger flex-shrink-0 mt-1" />
          : <FiCpu className="text-primary flex-shrink-0 mt-1" />}
        {/* pre-line preserves the \n bullets the backend sends without needing
            to parse the text into markup. */}
        <div className="flex-grow-1" style={{ whiteSpace: 'pre-line', minWidth: 0 }}>
          {m.answer}

          {m.items?.length > 0 && (
            <div className="table-responsive mt-2">
              <table className="table table-sm align-middle mb-0" style={{ whiteSpace: 'normal' }}>
                <tbody>
                  {m.items.map((it, i) => (
                    <tr key={i}>
                      <td className="small">
                        {it.productId
                          ? <Link to={`/products/${it.productId}`} className="fw-semibold text-decoration-none">{it.product}</Link>
                          : <span className="fw-semibold">{it.product}</span>}
                      </td>
                      <td className="small text-muted">{it.metric}</td>
                      <td className="small fw-semibold text-nowrap">{it.value}</td>
                      <td className="small text-muted">{it.note}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {m.source && (
            <div className="text-muted mt-2" style={{ fontSize: 11 }}>{m.source}</div>
          )}
        </div>
      </div>
    </div>
  )
}
