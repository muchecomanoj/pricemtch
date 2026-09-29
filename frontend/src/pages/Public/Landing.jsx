import { useState, useRef, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { motion, useInView } from 'framer-motion'
import {
  FiArrowRight, FiPlay, FiCheck, FiBox, FiSearch, FiTrendingUp,
  FiPieChart, FiBell, FiFileText, FiMenu, FiX, FiActivity, FiZap, FiTarget,
} from 'react-icons/fi'
import { APP_NAME } from '../../constants'
import { publicService } from '../../services/publicService'
import { normalizePlans } from '../../utils/plans'
import { formatCurrency } from '../../utils/format'

// ── Marketing landing page (public, no auth) ────────────────────────────────
// Every "start" CTA drops the visitor into the self-signup flow with the plan
// preselected; pricing comes live from the backend catalog.

const NAV_LINKS = [
  ['Platform', '#platform'], ['Product', '#workspace'], ['Pricing', '#pricing'], ['Resources', '#footer'],
]

const TRUST = ['NORTHWIND', 'Vertex&Co', 'MERIDIAN', 'Corta', 'HELIOMART']

const FEATURES = [
  { icon: FiBox, grad: 'primary', title: 'Universal matching engine',
    body: 'Match your catalog to millions of listings by ASIN, UPC, EAN or image similarity — with confidence scores you can audit.' },
  { icon: FiSearch, grad: 'info', title: 'Real-time competitor tracking',
    body: 'Watch every seller across Amazon, eBay, Walmart and the open web — price, stock, shipping and condition, updated continuously.' },
  { icon: FiTrendingUp, grad: 'primary', title: 'AI pricing recommendations',
    body: 'Confidence-scored price moves that protect margin and win the buy box — with the reasoning shown, never a black box.' },
  { icon: FiPieChart, grad: 'success', title: 'Profitability intelligence',
    body: 'Model COGS, fees, shipping, ads and returns to see the true net margin behind every SKU and every price change.' },
  { icon: FiBell, grad: 'purple', title: 'Smart alerts',
    body: 'Know the moment a rival drops price, goes out of stock, or a new seller appears — routed to the right person, instantly.' },
  { icon: FiFileText, grad: 'info', title: 'Boardroom-ready reports',
    body: 'Export governed, data-status-tagged reports finance can trust — every number labelled actual, calculated or estimated.' },
]

const STEPS = [
  { n: '01', icon: FiSearch, grad: 'primary', title: 'Match',
    body: 'Import your catalog by CSV or API and auto-match every product against millions of marketplace listings.' },
  { n: '02', icon: FiActivity, grad: 'info', title: 'Monitor',
    body: 'Continuously track competitor prices, stock, shipping and new sellers across every marketplace you sell on.' },
  { n: '03', icon: FiTarget, grad: 'success', title: 'Optimize',
    body: 'Act on confidence-scored AI recommendations that defend the buy box and protect margin — with the reasoning shown.' },
]

const WORKSPACE_POINTS = [
  'Role-based access for admins, managers, analysts & finance',
  'Import your catalog by CSV or API in minutes',
  'Every number carries an auditable data-status tag',
]

const AI_POINTS = [
  'Every move shows its reasoning and confidence score',
  'Projected margin impact before you commit',
  'Stays inside the competitive range you set',
]

const STATS = [
  { value: 12, suffix: 'M+', decimals: 0, label: 'Listings tracked daily' },
  { value: 3.2, suffix: '%', decimals: 1, label: 'Average margin lift' },
  { value: 45, suffix: ' min', decimals: 0, label: 'Saved per analyst / day' },
  { value: 99.9, suffix: '%', decimals: 1, label: 'Platform uptime' },
]

// Pricing grid — the exact same catalog the signup flow shows.
function pricingTiers(plans) {
  return plans.map((p) => {
    const isFree = p.code === 'FREE' || p.price.monthly === 0
    return {
      code: p.code, name: p.name, tagline: p.tagline,
      price: isFree ? 'Free' : formatCurrency(p.price.monthly, p.currency),
      unit: isFree ? '' : '/mo',
      cta: isFree ? 'Start free trial' : 'Start free',
      to: `/signup?plan=${p.code}`,
      highlight: p.recommended,
      points: [p.users ? `Up to ${p.users.toLocaleString()} seats` : null, ...p.features].filter(Boolean),
    }
  })
}

const rise = {
  hidden: { opacity: 0, y: 26 },
  show: (i = 0) => ({ opacity: 1, y: 0, transition: { duration: 0.5, delay: i * 0.06, ease: 'easeOut' } }),
}

export default function Landing() {
  const [menuOpen, setMenuOpen] = useState(false)
  const plansQuery = useQuery({
    queryKey: ['public', 'plans'],
    queryFn: publicService.plans,
    staleTime: 5 * 60 * 1000,
  })
  const tiers = pricingTiers(normalizePlans(plansQuery.data))
  const closeMenu = () => setMenuOpen(false)

  return (
    <div className="lp">
      {/* ── Nav ───────────────────────────────────────────── */}
      <header className="lp-nav">
        <div className="lp-container lp-nav-inner">
          <a className="lp-brand" href="#top">
            <span className="lp-logo">PI</span>
            <span className="lp-brand-name">{APP_NAME}</span>
          </a>
          <nav className="lp-nav-links">
            {NAV_LINKS.map(([label, href]) => <a key={label} href={href}>{label}</a>)}
          </nav>
          <div className="lp-nav-cta">
            <Link to="/login" className="lp-btn lp-btn-ghost">Sign in</Link>
            <Link to="/signup" className="lp-btn lp-btn-primary">Start free trial</Link>
          </div>
          <button className="lp-nav-toggle" aria-label="Toggle menu" aria-expanded={menuOpen}
            onClick={() => setMenuOpen((o) => !o)}>{menuOpen ? <FiX /> : <FiMenu />}</button>
        </div>
        {menuOpen && (
          <div className="lp-mobile-drawer">
            {NAV_LINKS.map(([label, href]) => <a key={label} href={href} onClick={closeMenu}>{label}</a>)}
            <Link to="/login" className="lp-btn lp-btn-outline w-100" onClick={closeMenu}>Sign in</Link>
            <Link to="/signup" className="lp-btn lp-btn-primary w-100" onClick={closeMenu}>Start free trial</Link>
          </div>
        )}
      </header>

      {/* ── Hero ──────────────────────────────────────────── */}
      <section className="lp-hero" id="top">
        <div className="lp-hero-glow" />
        <div className="lp-container lp-hero-grid">
          <motion.div initial="hidden" animate="show" variants={rise} className="lp-hero-copy">
            <span className="lp-eyebrow-pill"><span className="lp-dot" /> AI price monitoring · live across every marketplace</span>
            <h1 className="lp-h1">Price with<br />intelligence,<br /><span className="lp-grad-text">not guesswork.</span></h1>
            <p className="lp-lead">
              Track competitors, monitor market movements, match your catalog to millions of listings, and let AI
              recommend the price that protects margin and wins the buy box.
            </p>
            <div className="lp-hero-actions">
              <Link to="/signup" className="lp-btn lp-btn-primary lp-btn-lg">Start free trial <FiArrowRight /></Link>
              <a href="#workflow" className="lp-btn lp-btn-outline lp-btn-lg"><FiPlay /> See how it works</a>
            </div>
            <div className="lp-hero-ticks">
              <span><FiCheck /> No credit card required</span>
              <span><FiCheck /> 14-day full access</span>
              <span><FiCheck /> SOC 2 ready</span>
            </div>
          </motion.div>
          <motion.div initial="hidden" animate="show" variants={rise} custom={1.4} className="lp-hero-visual">
            <HeroDashboard />
          </motion.div>
        </div>
      </section>

      {/* ── Trust strip ───────────────────────────────────── */}
      <div className="lp-trust">
        <div className="lp-container lp-trust-inner">
          <span className="lp-trust-label">TRUSTED BY PRICING TEAMS AT</span>
          <div className="lp-trust-logos">{TRUST.map((t) => <span key={t} className="lp-trust-logo">{t}</span>)}</div>
        </div>
      </div>

      {/* ── Features ──────────────────────────────────────── */}
      <section className="lp-section" id="platform">
        <div className="lp-container">
          <SectionHead eyebrow="THE PLATFORM" title="Everything you need to price competitively"
            sub="From raw marketplace data to a boardroom-ready recommendation — one connected workflow, no spreadsheets." />
          <div className="lp-feature-grid">
            {FEATURES.map((f, i) => (
              <motion.div key={f.title} className="lp-feature-card"
                initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.3 }} variants={rise} custom={i % 3}>
                <div className={`lp-feat-icon grad-${f.grad}`}><f.icon /></div>
                <h3>{f.title}</h3>
                <p>{f.body}</p>
                <a className="lp-feat-more" href="#workspace">Learn more <FiArrowRight size={13} /></a>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Workflow (3 steps) ────────────────────────────── */}
      <section className="lp-section lp-workflow" id="workflow">
        <div className="lp-container">
          <SectionHead eyebrow="HOW IT WORKS" center title="From catalog to confident pricing in three steps"
            sub="One connected workflow — no spreadsheets, no guesswork, no black boxes." />
          <div className="lp-steps">
            {STEPS.map((s, i) => (
              <motion.div className="lp-step" key={s.n}
                initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.4 }} variants={rise} custom={i}>
                <div className="lp-step-num">{s.n}</div>
                <div className={`lp-step-icon grad-${s.grad}`}><s.icon /></div>
                <h3>{s.title}</h3>
                <p>{s.body}</p>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* ── Product showcase (workspace) ──────────────────── */}
      <section className="lp-section lp-workspace" id="workspace">
        <div className="lp-container lp-split">
          <motion.div initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.4 }} variants={rise}>
            <span className="lp-eyebrow">ONE WORKSPACE</span>
            <h2 className="lp-h2">Your whole pricing<br />operation, in one place</h2>
            <p className="lp-section-sub" style={{ margin: '0 0 1.6rem' }}>
              Catalog, competitors, costs and recommendations share one source of truth — so
              analysts, managers and finance finally work from the same numbers.
            </p>
            <ul className="lp-checklist">{WORKSPACE_POINTS.map((p) => <li key={p}><FiCheck /> {p}</li>)}</ul>
            <Link to="/signup" className="lp-btn lp-btn-dark lp-btn-lg">Explore the platform <FiArrowRight /></Link>
          </motion.div>
          <motion.div initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.4 }} variants={rise} custom={1}>
            <ProductsTable />
          </motion.div>
        </div>
      </section>

      {/* ── Stats band (animated counters) ────────────────── */}
      <div className="lp-stats">
        <div className="lp-container lp-stats-grid">
          {STATS.map((s) => <StatCounter key={s.label} {...s} />)}
        </div>
      </div>

      {/* ── AI Intelligence ───────────────────────────────── */}
      <section className="lp-section lp-ai" id="ai">
        <div className="lp-container lp-split">
          <motion.div initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.4 }} variants={rise} custom={1}>
            <AIRecommendation />
          </motion.div>
          <motion.div initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.4 }} variants={rise}>
            <span className="lp-eyebrow">AI RECOMMENDATIONS</span>
            <h2 className="lp-h2">AI that explains the price,<br />not just recommends it</h2>
            <p className="lp-section-sub" style={{ margin: '0 0 1.6rem' }}>
              Every recommendation shows the reasoning, the confidence score and the projected margin
              impact — so your team can trust it and act, instead of second-guessing a black box.
            </p>
            <ul className="lp-checklist">{AI_POINTS.map((p) => <li key={p}><FiCheck /> {p}</li>)}</ul>
            <a href="#pricing" className="lp-btn lp-btn-dark lp-btn-lg">See AI recommendations <FiArrowRight /></a>
          </motion.div>
        </div>
      </section>

      {/* ── Testimonial ───────────────────────────────────── */}
      <section className="lp-section lp-quote-wrap">
        <div className="lp-container lp-quote">
          <div className="lp-quote-mark">“</div>
          <blockquote>
            Price Intelligence paid for itself in six weeks. We stopped guessing on competitor
            moves and our pricing team finally trusts a single set of numbers.
          </blockquote>
          <div className="lp-quote-author">
            <span className="lp-avatar">AK</span>
            <div><div className="lp-qa-name">Anita Krishnan</div><div className="lp-qa-role">Head of Pricing, Northwind Retail</div></div>
          </div>
        </div>
      </section>

      {/* ── Pricing ───────────────────────────────────────── */}
      <section className="lp-section" id="pricing">
        <div className="lp-container">
          <SectionHead eyebrow="PRICING" center title="Simple, transparent pricing"
            sub="Choose the plan that fits your catalog. Every tier includes competitor tracking and the matching engine." />
          <div className="lp-price-grid">
            {tiers.map((p) => (
              <div key={p.code} className={`lp-price-card ${p.highlight ? 'is-popular' : ''}`}>
                {p.highlight && <span className="lp-popular-badge">MOST POPULAR</span>}
                <div className="lp-price-name">{p.name}</div>
                <div className="lp-price-tagline">{p.tagline}</div>
                <div className="lp-price-amount">{p.price}<span>{p.unit}</span></div>
                <Link to={p.to} className={`lp-btn lp-btn-lg w-100 ${p.highlight ? 'lp-btn-primary' : 'lp-btn-soft'}`}>{p.cta}</Link>
                <ul className="lp-price-points">{p.points.map((pt) => <li key={pt}><FiCheck /> {pt}</li>)}</ul>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── CTA band ──────────────────────────────────────── */}
      <section className="lp-section">
        <div className="lp-container">
          <div className="lp-cta">
            <h2>Stop guessing. Start pricing with intelligence.</h2>
            <p>Set up your catalog in minutes. See competitor moves and your first AI recommendation today.</p>
            <div className="lp-cta-actions">
              <Link to="/signup" className="lp-btn lp-btn-lg lp-btn-white">Start free trial</Link>
              <Link to="/pricing" className="lp-btn lp-btn-lg lp-btn-glass">Book a demo</Link>
            </div>
          </div>
        </div>
      </section>

      {/* ── Footer ────────────────────────────────────────── */}
      <footer className="lp-footer" id="footer">
        <div className="lp-container lp-footer-grid">
          <div className="lp-footer-brand">
            <a className="lp-brand" href="#top"><span className="lp-logo">PI</span><span className="lp-brand-name">{APP_NAME}</span></a>
            <p>The AI product &amp; competitor price intelligence platform for modern retail teams.</p>
          </div>
          <FooterCol title="Product" links={['Platform', 'Pricing', 'Product tour', 'Customers']} />
          <FooterCol title="Company" links={['About', 'Careers', 'Blog', 'Contact']} />
          <FooterCol title="Legal" links={['Privacy', 'Terms', 'Security', 'SOC 2']} />
        </div>
        <div className="lp-container lp-footer-base">
          <span>© {new Date().getFullYear()} {APP_NAME}. All rights reserved.</span>
        </div>
      </footer>
    </div>
  )
}

// ── Building blocks ─────────────────────────────────────────────────────────
function SectionHead({ eyebrow, title, sub, center }) {
  return (
    <div className={`lp-section-head ${center ? 'is-center' : ''}`}>
      <span className="lp-eyebrow">{eyebrow}</span>
      <h2 className="lp-h2">{title}</h2>
      <p className="lp-section-sub">{sub}</p>
    </div>
  )
}

function FooterCol({ title, links }) {
  return (
    <div className="lp-footer-col">
      <div className="lp-footer-col-title">{title}</div>
      {links.map((l) => <a key={l} href="#top">{l}</a>)}
    </div>
  )
}

// Animated count-up that runs once when scrolled into view. Respects reduced motion.
function StatCounter({ value, suffix = '', decimals = 0, label }) {
  const ref = useRef(null)
  const inView = useInView(ref, { once: true, amount: 0.5 })
  const [val, setVal] = useState(0)
  useEffect(() => {
    if (!inView) return
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
  }, [inView, value, decimals])
  const shown = val.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
  return (
    <div className="lp-stat" ref={ref}>
      <div className="lp-stat-n">{shown}{suffix}</div>
      <div className="lp-stat-l">{label}</div>
    </div>
  )
}

// ── Hero mockup: dashboard preview card ──────────────────────────────────────
function HeroDashboard() {
  return (
    <div className="lp-mock lp-mock-dash">
      <div className="lp-mock-bar">
        <span className="lp-tl r" /><span className="lp-tl y" /><span className="lp-tl g" />
        <span className="lp-mock-title">Dashboard · Maharana Retail</span>
      </div>
      <div className="lp-mock-body">
        <div className="lp-mini-row">
          <div className="lp-mini-stat"><span>Total Products</span><strong>1,284</strong><em className="up">▲ 4.2%</em></div>
          <div className="lp-mini-stat"><span>Avg Margin</span><strong>27.4%</strong><em className="up">▲ 0.9%</em></div>
        </div>
        <div className="lp-mini-chart">
          <div className="lp-mini-chart-head"><span>Price Trend — Ours vs Market</span><span className="lp-muted">7 mo</span></div>
          <TrendChart />
        </div>
        <div className="lp-mini-alert">
          <span className="lp-mini-alert-icon"><FiTrendingUp /></span>
          <div>
            <div className="lp-mini-alert-title">AI move: Echo Dot (5th Gen)</div>
            <div className="lp-muted sm">$44.99 → $42.49 · 91% confidence</div>
          </div>
          <span className="lp-chip green">Low risk</span>
        </div>
      </div>
      <div className="lp-float-alert">
        <div className="lp-muted sm">Competitor alert</div>
        <div className="lp-fw">SellerX dropped 6%</div>
      </div>
    </div>
  )
}

function TrendChart() {
  return (
    <svg viewBox="0 0 360 120" className="lp-trend" preserveAspectRatio="none">
      <polyline className="lp-trend-market" points="0,86 60,80 120,74 180,60 240,52 300,40 360,30" />
      <polyline className="lp-trend-ours" points="0,96 60,92 120,88 180,78 240,72 300,58 360,50" />
    </svg>
  )
}

function ProductsTable() {
  const rows = [
    ['ED', 'Echo Dot (5th Gen)', '$29.99', '15.0%', 'Matched', 'green'],
    ['FT', 'Fire TV Stick 4K', '$30.99', '16.0%', 'Review', 'amber'],
    ['KP', 'Kindle Paperwhite', '$31.99', '17.0%', 'Unmatched', 'grey'],
    ['WH', 'Sony WH-1000XM4', '$32.99', '18.0%', 'Matched', 'green'],
  ]
  return (
    <div className="lp-mock">
      <div className="lp-mock-bar lp-mock-bar-plain"><span className="lp-mock-title">Products · 1,284 tracked</span></div>
      <table className="lp-ptable">
        <thead><tr><th>PRODUCT</th><th>PRICE</th><th>MARGIN</th><th>STATUS</th></tr></thead>
        <tbody>
          {rows.map(([ini, name, price, margin, status, tone]) => (
            <tr key={name}>
              <td><span className={`lp-ini t-${tone}`}>{ini}</span>{name}</td>
              <td>{price}</td>
              <td className="lp-muted">{margin}</td>
              <td><span className={`lp-chip ${tone}`}>{status}</span></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

// ── AI recommendation mockup ────────────────────────────────────────────────
function AIRecommendation() {
  return (
    <div className="lp-mock lp-ai-card">
      <div className="lp-mock-bar">
        <span className="lp-tl r" /><span className="lp-tl y" /><span className="lp-tl g" />
        <span className="lp-mock-title">AI Recommendation · Echo Dot (5th Gen)</span>
      </div>
      <div className="lp-mock-body">
        <div className="lp-ai-head">
          <div>
            <div className="lp-muted sm">Recommended price</div>
            <div className="lp-ai-price">$42.49</div>
          </div>
          <span className="lp-chip green">91% confidence</span>
        </div>
        <div className="lp-ai-rows">
          <div><span>Current price</span><strong>$44.99</strong></div>
          <div><span>Competitor average</span><strong>$43.80</strong></div>
          <div><span>Expected margin</span><strong className="up">+3.5%</strong></div>
        </div>
        <div className="lp-ai-reason">
          <span className="lp-mini-alert-icon"><FiZap /></span>
          Increase price by 3.5% while staying within the competitive range and improving projected margin.
        </div>
      </div>
    </div>
  )
}
