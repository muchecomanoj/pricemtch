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
import { normalizePlans, isFreePlan } from '../../utils/plans'
import { formatCurrency } from '../../utils/format'
import Modal from '../../components/common/Modal'
import { DemoForm } from '../../components/public/LeadForms'

// ── Marketing landing page (public, no auth) ────────────────────────────────
// Every "start" CTA drops the visitor into the self-signup flow with the plan
// preselected; pricing comes live from the backend catalog.
//
// The text comes from GET /public/landing, editable by the platform admin.
// Two rules: a section missing from the response is simply not rendered (a
// hidden or broken section costs one box, not the page), and if the call
// fails or is still loading, FALLBACK_CONTENT is used so the page is never
// half-empty. What is NOT content: the pricing cards and the trial length,
// which both come from the plan catalog so they can't drift from what is
// actually sold.

const NAV_LINKS = [
  ['Platform', '#platform'], ['Product', '#workspace'], ['Pricing', '#pricing'], ['Resources', '#footer'],
]

const TRUST = ['NORTHWIND', 'Vertex&Co', 'MERIDIAN', 'Corta', 'HELIOMART']

// Same shape as GET /public/landing, and the copy it was seeded with.
const FALLBACK_CONTENT = {
  HERO: {
    badge: 'AI price monitoring · live across every marketplace',
    title: 'Price with intelligence, not guesswork.',
    subtitle: 'Track competitors, monitor market movements, match your catalog to millions of listings, and let AI recommend the price that protects margin and wins the buy box.',
    primaryCta: 'Start free trial',
    secondaryCta: 'See how it works',
    assurances: ['No credit card required', 'SOC 2 ready'],
  },
  STATS: {
    items: [
      { value: '12M+', label: 'Listings tracked daily' },
      { value: '3.2%', label: 'Average margin lift' },
      { value: '45 min', label: 'Saved per analyst / day' },
      { value: '99.9%', label: 'Platform uptime' },
    ],
  },
  FEATURES: {
    eyebrow: 'THE PLATFORM',
    title: 'Everything you need to price competitively',
    subtitle: 'From raw marketplace data to a boardroom-ready recommendation — one connected workflow, no spreadsheets.',
    items: [
      { icon: 'box', title: 'Universal matching engine',
        description: 'Match your catalog to millions of listings by ASIN, UPC, EAN or image similarity — with confidence scores you can audit.' },
      { icon: 'search', title: 'Real-time competitor tracking',
        description: 'Watch every seller across Amazon, eBay, Walmart and the open web — price, stock, shipping and condition, updated continuously.' },
      { icon: 'trend', title: 'AI pricing recommendations',
        description: 'Confidence-scored price moves that protect margin and win the buy box — with the reasoning shown, never a black box.' },
      { icon: 'pie', title: 'Profitability intelligence',
        description: 'Model COGS, fees, shipping, ads and returns to see the true net margin behind every SKU and every price change.' },
      { icon: 'bell', title: 'Smart alerts',
        description: 'Know the moment a rival drops price, goes out of stock, or a new seller appears — routed to the right person, instantly.' },
      { icon: 'report', title: 'Boardroom-ready reports',
        description: 'Export governed, data-status-tagged reports finance can trust — every number labelled actual, calculated or estimated.' },
    ],
  },
  HOW_IT_WORKS: {
    eyebrow: 'HOW IT WORKS',
    title: 'From catalog to confident pricing in three steps',
    subtitle: 'One connected workflow — no spreadsheets, no guesswork, no black boxes.',
    items: [
      { step: '01', title: 'Match',
        description: 'Import your catalog by CSV or API and auto-match every product against millions of marketplace listings.' },
      { step: '02', title: 'Monitor',
        description: 'Continuously track competitor prices, stock, shipping and new sellers across every marketplace you sell on.' },
      { step: '03', title: 'Optimize',
        description: 'Act on confidence-scored AI recommendations that defend the buy box and protect margin — with the reasoning shown.' },
    ],
  },
  // The two split sections. Only the words are content; the product table and
  // the recommendation card beside them are illustrations and stay in code.
  ONE_WORKSPACE: {
    eyebrow: 'ONE WORKSPACE',
    title: 'Your whole pricing operation, in one place',
    subtitle: 'Catalog, competitors, costs and recommendations share one source of truth — so analysts, managers and finance finally work from the same numbers.',
    bullets: [
      'Role-based access for admins, managers, analysts & finance',
      'Import your catalog by CSV or API in minutes',
      'Every number carries an auditable data-status tag',
    ],
    cta: 'Explore the platform',
  },
  AI_RECOMMENDATIONS: {
    eyebrow: 'AI RECOMMENDATIONS',
    title: 'AI that explains the price, not just recommends it',
    subtitle: 'Every recommendation shows the reasoning, the confidence score and the projected margin impact — so your team can trust it and act, instead of second-guessing a black box.',
    bullets: [
      'Every move shows its reasoning and confidence score',
      'Projected margin impact before you commit',
      'Stays inside the competitive range you set',
    ],
    cta: 'See AI recommendations',
  },
  TESTIMONIALS: {
    items: [{
      quote: 'Price Intelligence paid for itself in six weeks. We stopped guessing on competitor moves and our pricing team finally trusts a single set of numbers.',
      name: 'Anita Krishnan', role: 'Head of Pricing, Northwind Retail', initials: 'AK',
    }],
  },
  // No FAQ copy of our own: without the API there is nothing to answer with.
  CTA: {
    title: 'Stop guessing. Start pricing with intelligence.',
    subtitle: 'Set up your catalog in minutes. See competitor moves and your first AI recommendation today.',
    primaryCta: 'Start free trial',
    secondaryCta: 'Book a demo',
  },
}

// The API names icons by slug; an unknown one gets a generic mark.
const ICONS = { box: FiBox, search: FiSearch, trend: FiTrendingUp, pie: FiPieChart, bell: FiBell, report: FiFileText }
const iconFor = (slug) => ICONS[slug] || FiZap
// Colour is presentation, not content: by position, cycling if items are added.
const FEATURE_GRADS = ['primary', 'info', 'primary', 'success', 'purple', 'info']
const STEP_STYLE = [
  { icon: FiSearch, grad: 'primary' }, { icon: FiActivity, grad: 'info' }, { icon: FiTarget, grad: 'success' },
]
const list = (section) => (Array.isArray(section?.items) ? section.items : [])

// A two-line heading. An explicit "\n" in the text decides the break;
// otherwise it goes at the space nearest the middle, which is where the
// hand-set <br /> sat in both split-section titles.
function TwoLineTitle({ text = '' }) {
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
function SplitCopy({ eyebrow, title, subtitle, bullets, children }) {
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

// "12M+" → counts up to 12 then shows "M+"; "45 min" → 45 then " min". A
// value that doesn't start with a number is shown as written, unanimated.
function parseStat(value) {
  const m = /^(\d+(?:\.\d+)?)(.*)$/.exec(String(value ?? '').trim())
  if (!m) return { text: String(value ?? '') }
  return { value: parseFloat(m[1]), decimals: (m[1].split('.')[1] || '').length, suffix: m[2] }
}

// "Price with intelligence, not guesswork." is set as three lines with the
// clause after the last comma in the gradient. Any title with a comma gets
// the same treatment; one without is shown plain.
function HeroTitle({ title = '' }) {
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

// Pricing grid — the exact same catalog the signup flow shows.
function pricingTiers(plans) {
  return plans.map((p) => {
    const isFree = p.code === 'FREE' || isFreePlan(p)
    return {
      code: p.code, name: p.name, tagline: p.tagline,
      price: isFree ? 'Free' : formatCurrency(p.price.monthly, p.currency),
      unit: isFree ? '' : '/mo',
      // Paid plans are billed from day one — only Free is a trial.
      cta: isFree ? 'Start free trial' : 'Get started',
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
  const [demoOpen, setDemoOpen] = useState(false)
  const plansQuery = useQuery({
    queryKey: ['public', 'plans'],
    queryFn: publicService.plans,
    staleTime: 5 * 60 * 1000,
  })
  const landingQuery = useQuery({
    queryKey: ['public', 'landing'],
    queryFn: publicService.landing,
    staleTime: 5 * 60 * 1000,
    retry: 1,   // a marketing page shouldn't sit on retries; the fallback is fine
  })
  const content = landingQuery.data ?? FALLBACK_CONTENT
  const { HERO: hero, STATS: stats, FEATURES: features, HOW_IT_WORKS: how,
    ONE_WORKSPACE: workspace, AI_RECOMMENDATIONS: ai,
    TESTIMONIALS: testimonials, FAQS: faqs, CTA: cta } = content
  const plans = normalizePlans(plansQuery.data)
  const tiers = pricingTiers(plans)
  const closeMenu = () => setMenuOpen(false)
  // "Start free trial" must open sign-up on the free plan: bare /signup
  // preselects Professional, which is paid from day one.
  const freePlan = plans.find(isFreePlan)
  const trialTo = freePlan ? `/signup?plan=${freePlan.code}` : '/signup'

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
            <Link to={trialTo} className="lp-btn lp-btn-primary">Start free trial</Link>
          </div>
          <button className="lp-nav-toggle" aria-label="Toggle menu" aria-expanded={menuOpen}
            onClick={() => setMenuOpen((o) => !o)}>{menuOpen ? <FiX /> : <FiMenu />}</button>
        </div>
        {menuOpen && (
          <div className="lp-mobile-drawer">
            {NAV_LINKS.map(([label, href]) => <a key={label} href={href} onClick={closeMenu}>{label}</a>)}
            <Link to="/login" className="lp-btn lp-btn-outline w-100" onClick={closeMenu}>Sign in</Link>
            <Link to={trialTo} className="lp-btn lp-btn-primary w-100" onClick={closeMenu}>Start free trial</Link>
          </div>
        )}
      </header>

      {/* ── Hero ──────────────────────────────────────────── */}
      {/* The id stays on a section either way: the brand and footer links jump to #top. */}
      {hero ? (
        <section className="lp-hero" id="top">
          <div className="lp-hero-glow" />
          <div className="lp-container lp-hero-grid">
            <motion.div initial="hidden" animate="show" variants={rise} className="lp-hero-copy">
              {hero.badge && <span className="lp-eyebrow-pill"><span className="lp-dot" /> {hero.badge}</span>}
              <HeroTitle title={hero.title} />
              {hero.subtitle && <p className="lp-lead">{hero.subtitle}</p>}
              <div className="lp-hero-actions">
                <Link to={trialTo} className="lp-btn lp-btn-primary lp-btn-lg">{hero.primaryCta || 'Start free trial'} <FiArrowRight /></Link>
                {hero.secondaryCta && <a href="#workflow" className="lp-btn lp-btn-outline lp-btn-lg"><FiPlay /> {hero.secondaryCta}</a>}
              </div>
              <div className="lp-hero-ticks">
                {heroTicks(hero.assurances, freePlan).map((t) => <span key={t}><FiCheck /> {t}</span>)}
              </div>
            </motion.div>
            <motion.div initial="hidden" animate="show" variants={rise} custom={1.4} className="lp-hero-visual">
              <HeroDashboard />
            </motion.div>
          </div>
        </section>
      ) : <span id="top" />}

      {/* ── Trust strip ───────────────────────────────────── */}
      <div className="lp-trust">
        <div className="lp-container lp-trust-inner">
          <span className="lp-trust-label">TRUSTED BY PRICING TEAMS AT</span>
          <div className="lp-trust-logos">{TRUST.map((t) => <span key={t} className="lp-trust-logo">{t}</span>)}</div>
        </div>
      </div>

      {/* ── Features ──────────────────────────────────────── */}
      {features && (
        <section className="lp-section" id="platform">
          <div className="lp-container">
            <SectionHead eyebrow={features.eyebrow} title={features.title} sub={features.subtitle} />
            <div className="lp-feature-grid">
              {list(features).map((f, i) => {
                const Icon = iconFor(f.icon)
                return (
                  <motion.div key={`${i}-${f.title}`} className="lp-feature-card"
                    initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.3 }} variants={rise} custom={i % 3}>
                    <div className={`lp-feat-icon grad-${FEATURE_GRADS[i % FEATURE_GRADS.length]}`}><Icon /></div>
                    <h3>{f.title}</h3>
                    <p>{f.description}</p>
                    <a className="lp-feat-more" href="#workspace">Learn more <FiArrowRight size={13} /></a>
                  </motion.div>
                )
              })}
            </div>
          </div>
        </section>
      )}

      {/* ── Workflow (3 steps) ────────────────────────────── */}
      {how && (
        <section className="lp-section lp-workflow" id="workflow">
          <div className="lp-container">
            <SectionHead eyebrow={how.eyebrow} center title={how.title} sub={how.subtitle} />
            <div className="lp-steps">
              {list(how).map((s, i) => {
                const style = STEP_STYLE[i % STEP_STYLE.length]
                return (
                  <motion.div className="lp-step" key={`${i}-${s.title}`}
                    initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.4 }} variants={rise} custom={i}>
                    <div className="lp-step-num">{s.step || String(i + 1).padStart(2, '0')}</div>
                    <div className={`lp-step-icon grad-${style.grad}`}><style.icon /></div>
                    <h3>{s.title}</h3>
                    <p>{s.description}</p>
                  </motion.div>
                )
              })}
            </div>
          </div>
        </section>
      )}

      {/* ── Product showcase (workspace) ──────────────────── */}
      {workspace && (
        <section className="lp-section lp-workspace" id="workspace">
          <div className="lp-container lp-split">
            <motion.div initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.4 }} variants={rise}>
              <SplitCopy {...workspace}>
                {workspace.cta && <Link to="/signup" className="lp-btn lp-btn-dark lp-btn-lg">{workspace.cta} <FiArrowRight /></Link>}
              </SplitCopy>
            </motion.div>
            <motion.div initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.4 }} variants={rise} custom={1}>
              <ProductsTable />
            </motion.div>
          </div>
        </section>
      )}

      {/* ── Stats band (animated counters) ────────────────── */}
      {stats && (
        <div className="lp-stats">
          <div className="lp-container lp-stats-grid">
            {list(stats).map((s, i) => <StatCounter key={`${i}-${s.label}`} {...parseStat(s.value)} label={s.label} />)}
          </div>
        </div>
      )}

      {/* ── AI Intelligence ───────────────────────────────── */}
      {ai && (
        <section className="lp-section lp-ai" id="ai">
          <div className="lp-container lp-split">
            <motion.div initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.4 }} variants={rise} custom={1}>
              <AIRecommendation />
            </motion.div>
            <motion.div initial="hidden" whileInView="show" viewport={{ once: true, amount: 0.4 }} variants={rise}>
              <SplitCopy {...ai}>
                {ai.cta && <a href="#pricing" className="lp-btn lp-btn-dark lp-btn-lg">{ai.cta} <FiArrowRight /></a>}
              </SplitCopy>
            </motion.div>
          </div>
        </section>
      )}

      {/* ── Testimonials ──────────────────────────────────── */}
      {list(testimonials).map((t, i) => (
        <section className="lp-section lp-quote-wrap" key={`${i}-${t.name}`}>
          <div className="lp-container lp-quote">
            <div className="lp-quote-mark">“</div>
            <blockquote>{t.quote}</blockquote>
            <div className="lp-quote-author">
              <span className="lp-avatar">{t.initials || initialsOf(t.name)}</span>
              <div><div className="lp-qa-name">{t.name}</div>{t.role && <div className="lp-qa-role">{t.role}</div>}</div>
            </div>
          </div>
        </section>
      ))}

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

      {/* ── FAQ ───────────────────────────────────────────── */}
      {list(faqs).length > 0 && (
        <section className="lp-section" id="faq">
          <div className="lp-container">
            <SectionHead eyebrow={faqs.eyebrow} center title={faqs.title} sub={faqs.subtitle} />
            <div className="lp-faq">
              {list(faqs).map((f, i) => (
                <details key={`${i}-${f.question}`} className="lp-faq-item">
                  <summary>{f.question}</summary>
                  <p>{f.answer}</p>
                </details>
              ))}
            </div>
          </div>
        </section>
      )}

      {/* ── CTA band ──────────────────────────────────────── */}
      {cta && (
        <section className="lp-section">
          <div className="lp-container">
            <div className="lp-cta">
              <h2>{cta.title}</h2>
              {cta.subtitle && <p>{cta.subtitle}</p>}
              <div className="lp-cta-actions">
                <Link to={trialTo} className="lp-btn lp-btn-lg lp-btn-white">{cta.primaryCta || 'Start free trial'}</Link>
                {/* Used to link to /pricing, which is this page — so it did nothing. */}
                {cta.secondaryCta && (
                  <button type="button" className="lp-btn lp-btn-lg lp-btn-glass" onClick={() => setDemoOpen(true)}>
                    {cta.secondaryCta}
                  </button>
                )}
              </div>
            </div>
          </div>
        </section>
      )}

      <Modal show={demoOpen} title="Book a demo" onClose={() => setDemoOpen(false)}>
        <p className="text-muted small">Tell us a little about you and we’ll be in touch to set up a walkthrough.</p>
        <DemoForm submitLabel="Book a demo" />
      </Modal>

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

// The reassurances under the hero buttons. The trial length is deliberately
// not among them: it is read from the Free plan, second in line, so it can't
// promise a trial the catalog no longer offers.
function heroTicks(assurances, freePlan) {
  const given = Array.isArray(assurances) ? assurances.filter(Boolean) : []
  const trial = freePlan?.trialDays > 0 ? `${freePlan.trialDays} days free` : freePlan ? 'Free plan available' : null
  return [...given.slice(0, 1), trial, ...given.slice(1)].filter(Boolean)
}

function initialsOf(name = '') {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('')
}

function SectionHead({ eyebrow, title, sub, center }) {
  return (
    <div className={`lp-section-head ${center ? 'is-center' : ''}`}>
      {eyebrow && <span className="lp-eyebrow">{eyebrow}</span>}
      {title && <h2 className="lp-h2">{title}</h2>}
      {sub && <p className="lp-section-sub">{sub}</p>}
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

// Animated count-up that runs once when scrolled into view. Respects reduced
// motion. `text` (a value that isn't a number) is shown as written.
function StatCounter({ value, suffix = '', decimals = 0, label, text }) {
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
