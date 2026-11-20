import { useState, useRef, useEffect } from 'react'
import { useInView } from 'framer-motion'
import { FiCheck } from 'react-icons/fi'

// Small presentational pieces the landing page's sections share.

// A two-line heading. An explicit "\n" in the text decides the break;
// otherwise it goes at the space nearest the middle, which is where the
// hand-set <br /> sat in both split-section titles.
export function TwoLineTitle({ text = '' }) {
  let lines = text.split('\n')
  if (lines.length === 1 && text.length > 24) {
    const mid = text.length / 2
    let cut = -1
    for (let i = text.indexOf(' '); i >= 0; i = text.indexOf(' ', i + 1)) {
      if (cut < 0 || Math.abs(i - mid) < Math.abs(cut - mid)) cut = i
    }
    if (cut > 0) lines = [text.slice(0, cut), text.slice(cut + 1)]
  }
  return <h2 className="lp-h2">{lines.map((l, i) => <span key={i}>{i > 0 && <br />}{l}</span>)}</h2>
}

// The words half of a split section.
export function SplitCopy({ eyebrow, title, subtitle, bullets, children }) {
  return (
    <>
      {eyebrow && <span className="lp-eyebrow">{eyebrow}</span>}
      {title && <TwoLineTitle text={title} />}
      {subtitle && <p className="lp-section-sub" style={{ margin: '0 0 1.6rem' }}>{subtitle}</p>}
      {Array.isArray(bullets) && bullets.length > 0 && (
        <ul className="lp-checklist">{bullets.map((p, i) => <li key={`${i}-${p}`}><FiCheck /> {p}</li>)}</ul>
      )}
      {children}
    </>
  )
}

// "Price with intelligence, not guesswork." is set as three lines with the
// clause after the last comma in the gradient. Any title with a comma gets
// the same treatment; one without is shown plain.
export function HeroTitle({ title = '' }) {
  const cut = title.lastIndexOf(',')
  if (cut < 0) return <h1 className="lp-h1">{title}</h1>
  const lead = title.slice(0, cut + 1).trim().split(/\s+/)
  const tail = title.slice(cut + 1).trim()
  const last = lead.pop()
  return (
    <h1 className="lp-h1">
      {lead.length > 0 && <>{lead.join(' ')}<br /></>}{last}<br /><span className="lp-grad-text">{tail}</span>
    </h1>
  )
}

export function SectionHead({ eyebrow, title, sub, center }) {
  return (
    <div className={`lp-section-head ${center ? 'is-center' : ''}`}>
      {eyebrow && <span className="lp-eyebrow">{eyebrow}</span>}
      {title && <h2 className="lp-h2">{title}</h2>}
      {sub && <p className="lp-section-sub">{sub}</p>}
    </div>
  )
}

// A link is a label (a placeholder that returns to the top) or
// [label, onClick] for one that does something on this page.
export function FooterCol({ title, links }) {
  return (
    <div className="lp-footer-col">
      <div className="lp-footer-col-title">{title}</div>
      {links.map((l) => (Array.isArray(l)
        ? <button key={l[0]} type="button" className="lp-footer-link" onClick={l[1]}>{l[0]}</button>
        : <a key={l} href="#top">{l}</a>))}
    </div>
  )
}

// Animated count-up that runs once when scrolled into view. Respects reduced
// motion. `text` (a value that isn't a number) is shown as written.
export function StatCounter({ value, suffix = '', decimals = 0, label, text }) {
  const ref = useRef(null)
  const inView = useInView(ref, { once: true, amount: 0.5 })
  const [val, setVal] = useState(0)
  useEffect(() => {
    if (!inView || text != null) return
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) { setVal(value); return }
    let raf
    const start = performance.now()
    const dur = 1300
    const tick = (t) => {
      const p = Math.min(1, (t - start) / dur)
      setVal(+(value * (1 - Math.pow(1 - p, 3))).toFixed(decimals))
      if (p < 1) raf = requestAnimationFrame(tick)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [inView, value, decimals, text])
  const shown = val.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
  return (
    <div className="lp-stat" ref={ref}>
      <div className="lp-stat-n">{text ?? <>{shown}{suffix}</>}</div>
      <div className="lp-stat-l">{label}</div>
    </div>
  )
}
