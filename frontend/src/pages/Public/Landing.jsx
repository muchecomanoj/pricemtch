import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { publicService } from '../../services/publicService'
import { normalizePlans, isFreePlan } from '../../utils/plans'
import Modal from '../../components/common/Modal'
import { DemoForm, ContactForm } from '../../components/public/LeadForms'
import { FALLBACK_CONTENT, TRUST, list, pricingTiers } from './landingContent'
import {
  LandingNav, Hero, Features, Workflow, Workspace, Stats, AiSection, Testimonial, Pricing, Faq, CtaBand, LandingFooter,
} from './LandingSections'

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
//
// The pieces: landingContent.js (copy and helpers), LandingSections.jsx (the
// sections), LandingParts.jsx (shared bits), LandingMockups.jsx (illustrations).

export default function Landing() {
  const [menuOpen, setMenuOpen] = useState(false)
  const [demoOpen, setDemoOpen] = useState(false)
  const [contactOpen, setContactOpen] = useState(false)
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
  // "Start free trial" must open sign-up on the free plan: bare /signup
  // preselects Professional, which is paid from day one.
  const freePlan = plans.find(isFreePlan)
  const trialTo = freePlan ? `/signup?plan=${freePlan.code}` : '/signup'

  return (
    <div className="lp">
      {/* ── Nav ───────────────────────────────────────────── */}
      <LandingNav menuOpen={menuOpen} setMenuOpen={setMenuOpen} trialTo={trialTo} />

      {/* ── Hero ──────────────────────────────────────────── */}
      {/* The id stays on a section either way: the brand and footer links jump to #top. */}
      {hero ? <Hero hero={hero} trialTo={trialTo} freePlan={freePlan} /> : <span id="top" />}

      {/* ── Trust strip ───────────────────────────────────── */}
      <div className="lp-trust">
        <div className="lp-container lp-trust-inner">
          <span className="lp-trust-label">TRUSTED BY PRICING TEAMS AT</span>
          <div className="lp-trust-logos">{TRUST.map((t) => <span key={t} className="lp-trust-logo">{t}</span>)}</div>
        </div>
      </div>

      {/* ── Features ──────────────────────────────────────── */}
      {features && <Features features={features} />}

      {/* ── Workflow (3 steps) ────────────────────────────── */}
      {how && <Workflow how={how} />}

      {/* ── Product showcase (workspace) ──────────────────── */}
      {workspace && <Workspace workspace={workspace} />}

      {/* ── Stats band (animated counters) ────────────────── */}
      {stats && <Stats stats={stats} />}

      {/* ── AI Intelligence ───────────────────────────────── */}
      {ai && <AiSection ai={ai} />}

      {/* ── Testimonials ──────────────────────────────────── */}
      {list(testimonials).map((t, i) => <Testimonial key={`${i}-${t.name}`} t={t} />)}

      {/* ── Pricing ───────────────────────────────────────── */}
      <Pricing tiers={tiers} />

      {/* ── FAQ ───────────────────────────────────────────── */}
      {list(faqs).length > 0 && <Faq faqs={faqs} />}

      {/* ── CTA band ──────────────────────────────────────── */}
      {cta && <CtaBand cta={cta} trialTo={trialTo} onDemo={() => setDemoOpen(true)} />}

      <Modal show={demoOpen} title="Book a demo" onClose={() => setDemoOpen(false)}>
        <p className="text-muted small">Tell us a little about you and we’ll be in touch to set up a walkthrough.</p>
        <DemoForm submitLabel="Book a demo" />
      </Modal>

      <Modal show={contactOpen} title="Contact us" onClose={() => setContactOpen(false)}>
        <p className="text-muted small">Questions about the platform, billing or anything else — we’ll reply by email.</p>
        <ContactForm />
      </Modal>

      {/* ── Footer ────────────────────────────────────────── */}
      <LandingFooter onContact={() => setContactOpen(true)} />
    </div>
  )
}
