import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { FiCheck } from 'react-icons/fi'
import Card from '../../components/common/Card'
import { publicService } from '../../services/publicService'
import { DemoForm, ContactForm, NewsletterForm } from '../../components/public/LeadForms'
import { APP_NAME } from '../../constants'
import { formatCurrency } from '../../utils/format'

// Public (no-auth) marketing page: pricing, features, FAQs, testimonials, lead forms.
export default function PublicPricing() {
  const plans = useQuery({ queryKey: ['pub', 'plans'], queryFn: publicService.plans })
  const features = useQuery({ queryKey: ['pub', 'features'], queryFn: publicService.features })
  const faqs = useQuery({ queryKey: ['pub', 'faqs'], queryFn: publicService.faqs })
  const testimonials = useQuery({ queryKey: ['pub', 'testimonials'], queryFn: publicService.testimonials })
  // Each is a landing section { eyebrow?, title?, items }, not a bare list.
  const featureItems = features.data?.items || []
  const faqItems = faqs.data?.items || []
  const testimonialItems = testimonials.data?.items || []

  return (
    <div style={{ background: 'var(--app-bg)', minHeight: '100vh' }}>
      {/* Top bar */}
      <header className="d-flex align-items-center justify-content-between px-4 py-3 border-bottom" style={{ background: 'var(--card-bg)' }}>
        <div className="d-flex align-items-center gap-2">
          <div className="d-grid rounded-3 text-white fw-bold" style={{ width: 36, height: 36, placeItems: 'center', background: 'var(--grad-primary)' }}>PI</div>
          <span className="fw-bold" style={{ fontFamily: 'Plus Jakarta Sans' }}>{APP_NAME}</span>
        </div>
        <Link to="/login" className="btn btn-primary btn-sm">Sign In</Link>
      </header>

      <div className="container py-5" style={{ maxWidth: 1100 }}>
        <div className="text-center mb-5">
          <h1 className="fw-bold" style={{ fontSize: '2.4rem' }}>Simple, transparent pricing</h1>
          <p className="text-muted">Choose the plan that fits your business.</p>
        </div>

        {/* Plans */}
        <div className="row g-3 mb-5">
          {(plans.data || []).map((p, i) => (
            <div className="col-md-4" key={p.code || i}>
              <Card hover className={`h-100 ${i === 1 ? 'border-primary' : ''}`}>
                <div className="text-center">
                  <h5 className="fw-bold">{p.name}</h5>
                  <div className="display-6 fw-bold my-2">{formatCurrency(p.price, p.currency)}</div>
                  <div className="text-muted small mb-3">up to {p.maxUsers} users</div>
                  <div className="text-start small text-muted mb-3">
                    {String(p.features || '').split(',').map((f, k) => (
                      <div key={k} className="d-flex gap-2 align-items-center py-1"><FiCheck className="text-success" />{f.trim()}</div>
                    ))}
                  </div>
                  <Link to={`/signup?plan=${p.code}`} className={`btn w-100 ${i === 1 ? 'btn-primary' : 'btn-light'}`}>Get started</Link>
                </div>
              </Card>
            </div>
          ))}
        </div>

        {/* Features */}
        {featureItems.length > 0 && (
          <div className="row g-3 mb-5">
            {featureItems.map((f, i) => (
              <div className="col-md-4" key={i}>
                <Card><h6 className="fw-semibold">{f.title || f.name}</h6><p className="text-muted small mb-0">{f.description}</p></Card>
              </div>
            ))}
          </div>
        )}

        {/* Testimonials */}
        {testimonialItems.length > 0 && (
          <div className="row g-3 mb-5">
            {testimonialItems.map((t, i) => (
              <div className="col-md-6" key={i}>
                <Card><p className="mb-2">“{t.quote || t.message}”</p><div className="fw-semibold small">— {t.name}</div></Card>
              </div>
            ))}
          </div>
        )}

        {/* FAQs */}
        {faqItems.length > 0 && (
          <Card title={faqs.data?.title || 'Frequently asked questions'} className="mb-5">
            {faqItems.map((f, i) => (
              <div key={i} className="py-2 border-bottom">
                <div className="fw-semibold">{f.question}</div>
                <div className="text-muted small">{f.answer}</div>
              </div>
            ))}
          </Card>
        )}

        {/* Lead forms */}
        <div className="row g-3">
          <div className="col-md-6"><Card title="Request a demo"><DemoForm /></Card></div>
          <div className="col-md-6">
            <Card title="Contact us" className="mb-3"><ContactForm /></Card>
            <Card title="Newsletter"><NewsletterForm /></Card>
          </div>
        </div>
      </div>
    </div>
  )
}
