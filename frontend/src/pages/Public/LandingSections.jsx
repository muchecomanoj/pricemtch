import { Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { FiArrowRight, FiPlay, FiCheck, FiMenu, FiX } from 'react-icons/fi'
import { APP_NAME } from '../../constants'
import { NewsletterForm } from '../../components/public/LeadForms'
import {
  NAV_LINKS, FEATURE_GRADS, STEP_STYLE, iconFor, list, parseStat, heroTicks, initialsOf, rise,
} from './landingContent'
import { SectionHead, SplitCopy, HeroTitle, FooterCol, StatCounter } from './LandingParts'
import { HeroDashboard, ProductsTable, AIRecommendation } from './LandingMockups'

// The landing page's sections, top to bottom. Each is given only its own
// slice of the content; whether a section shows at all is Landing's call.

export function LandingNav({ menuOpen, setMenuOpen, trialTo }) {
  const closeMenu = () => setMenuOpen(false)
  return (
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
  )
}

export function Hero({ hero, trialTo, freePlan }) {
  return (
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
  )
}

export function Features({ features }) {
  return (
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
  )
}

export function Workflow({ how }) {
  return (
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
  )
}

export function Workspace({ workspace }) {
  return (
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
  )
}

export function Stats({ stats }) {
  return (
    <div className="lp-stats">
      <div className="lp-container lp-stats-grid">
        {list(stats).map((s, i) => <StatCounter key={`${i}-${s.label}`} {...parseStat(s.value)} label={s.label} />)}
      </div>
    </div>
  )
}

export function AiSection({ ai }) {
  return (
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
  )
}

export function Testimonial({ t }) {
  return (
    <section className="lp-section lp-quote-wrap">
      <div className="lp-container lp-quote">
        <div className="lp-quote-mark">“</div>
        <blockquote>{t.quote}</blockquote>
        <div className="lp-quote-author">
          <span className="lp-avatar">{t.initials || initialsOf(t.name)}</span>
          <div><div className="lp-qa-name">{t.name}</div>{t.role && <div className="lp-qa-role">{t.role}</div>}</div>
        </div>
      </div>
    </section>
  )
}

export function Pricing({ tiers }) {
  return (
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
  )
}

export function Faq({ faqs }) {
  return (
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
  )
}

export function CtaBand({ cta, trialTo, onDemo }) {
  return (
    <section className="lp-section">
      <div className="lp-container">
        <div className="lp-cta">
          <h2>{cta.title}</h2>
          {cta.subtitle && <p>{cta.subtitle}</p>}
          <div className="lp-cta-actions">
            <Link to={trialTo} className="lp-btn lp-btn-lg lp-btn-white">{cta.primaryCta || 'Start free trial'}</Link>
            {/* Used to link to /pricing, which is this page — so it did nothing. */}
            {cta.secondaryCta && (
              <button type="button" className="lp-btn lp-btn-lg lp-btn-glass" onClick={() => onDemo()}>
                {cta.secondaryCta}
              </button>
            )}
          </div>
        </div>
      </div>
    </section>
  )
}

export function LandingFooter({ onContact }) {
  return (
    <footer className="lp-footer" id="footer">
      <div className="lp-container lp-footer-grid">
        <div className="lp-footer-brand">
          <a className="lp-brand" href="#top"><span className="lp-logo">PI</span><span className="lp-brand-name">{APP_NAME}</span></a>
          <p>The AI product &amp; competitor price intelligence platform for modern retail teams.</p>
          <div className="lp-newsletter">
            <div className="lp-newsletter-label">Get pricing insights by email</div>
            <NewsletterForm landing />
          </div>
        </div>
        <FooterCol title="Product" links={['Platform', 'Pricing', 'Product tour', 'Customers']} />
        {/* Contact opens the form; the rest are still placeholders with no page behind them. */}
        <FooterCol title="Company" links={['About', 'Careers', 'Blog', ['Contact', onContact]]} />
        <FooterCol title="Legal" links={['Privacy', 'Terms', 'Security', 'SOC 2']} />
      </div>
      <div className="lp-container lp-footer-base">
        <span>© {new Date().getFullYear()} {APP_NAME}. All rights reserved.</span>
      </div>
    </footer>
  )
}
