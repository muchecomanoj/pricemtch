import { useForm } from 'react-hook-form'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { FiCheck } from 'react-icons/fi'
import Button from '../../components/common/Button'
import Card from '../../components/common/Card'
import { publicService } from '../../services/publicService'
import { useNotification } from '../../context/NotificationContext'
import { APP_NAME } from '../../constants'
import { formatCurrency } from '../../utils/format'

// Public (no-auth) marketing page: pricing, features, FAQs, testimonials, lead forms.
export default function PublicPricing() {
  const plans = useQuery({ queryKey: ['pub', 'plans'], queryFn: publicService.plans })
  const features = useQuery({ queryKey: ['pub', 'features'], queryFn: publicService.features })
  const faqs = useQuery({ queryKey: ['pub', 'faqs'], queryFn: publicService.faqs })
  const testimonials = useQuery({ queryKey: ['pub', 'testimonials'], queryFn: publicService.testimonials })

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
        {(features.data || []).length > 0 && (
          <div className="row g-3 mb-5">
            {(features.data || []).map((f, i) => (
              <div className="col-md-4" key={i}>
                <Card><h6 className="fw-semibold">{f.title || f.name}</h6><p className="text-muted small mb-0">{f.description}</p></Card>
              </div>
            ))}
          </div>
        )}

        {/* Testimonials */}
        {(testimonials.data || []).length > 0 && (
          <div className="row g-3 mb-5">
            {(testimonials.data || []).map((t, i) => (
              <div className="col-md-6" key={i}>
                <Card><p className="mb-2">“{t.quote || t.message}”</p><div className="fw-semibold small">— {t.name}</div></Card>
              </div>
            ))}
          </div>
        )}

        {/* FAQs */}
        {(faqs.data || []).length > 0 && (
          <Card title="Frequently asked questions" className="mb-5">
            {(faqs.data || []).map((f, i) => (
              <div key={i} className="py-2 border-bottom">
                <div className="fw-semibold">{f.question}</div>
                <div className="text-muted small">{f.answer}</div>
              </div>
            ))}
          </Card>
        )}

        {/* Lead forms */}
        <div className="row g-3">
          <div className="col-md-6"><DemoForm /></div>
          <div className="col-md-6"><ContactForm /><NewsletterForm /></div>
        </div>
      </div>
    </div>
  )
}

function DemoForm() {
  const { notify } = useNotification()
  const { register, handleSubmit, reset, formState: { isSubmitting } } = useForm()
  const onSubmit = async (v) => {
    try { await publicService.requestDemo(v); notify.success('Demo request sent'); reset() }
    catch (e) { notify.error(e.message || 'Failed to send') }
  }
  return (
    <Card title="Request a demo">
      <form onSubmit={handleSubmit(onSubmit)} className="row g-2">
        <div className="col-md-6"><input className="form-control" placeholder="Name" {...register('name', { required: true })} /></div>
        <div className="col-md-6"><input type="email" className="form-control" placeholder="Email" {...register('email', { required: true })} /></div>
        <div className="col-md-6"><input className="form-control" placeholder="Phone" {...register('phone')} /></div>
        <div className="col-md-6"><input className="form-control" placeholder="Company" {...register('companyName')} /></div>
        <div className="col-12"><textarea className="form-control" rows={2} placeholder="Message" {...register('message')} /></div>
        <div className="col-12 d-flex justify-content-end"><Button type="submit" size="sm" loading={isSubmitting}>Request Demo</Button></div>
      </form>
    </Card>
  )
}

function ContactForm() {
  const { notify } = useNotification()
  const { register, handleSubmit, reset, formState: { isSubmitting } } = useForm()
  const onSubmit = async (v) => {
    try { await publicService.contact(v); notify.success('Message sent'); reset() }
    catch (e) { notify.error(e.message || 'Failed to send') }
  }
  return (
    <Card title="Contact us" className="mb-3">
      <form onSubmit={handleSubmit(onSubmit)} className="row g-2">
        <div className="col-md-6"><input className="form-control" placeholder="Name" {...register('name', { required: true })} /></div>
        <div className="col-md-6"><input type="email" className="form-control" placeholder="Email" {...register('email', { required: true })} /></div>
        <div className="col-12"><input className="form-control" placeholder="Subject" {...register('subject')} /></div>
        <div className="col-12"><textarea className="form-control" rows={2} placeholder="Message" {...register('message')} /></div>
        <div className="col-12 d-flex justify-content-end"><Button type="submit" size="sm" loading={isSubmitting}>Send</Button></div>
      </form>
    </Card>
  )
}

function NewsletterForm() {
  const { notify } = useNotification()
  const { register, handleSubmit, reset, formState: { isSubmitting } } = useForm()
  const onSubmit = async (v) => {
    try { await publicService.newsletter(v.email); notify.success('Subscribed'); reset() }
    catch (e) { notify.error(e.message || 'Failed') }
  }
  return (
    <Card title="Newsletter">
      <form onSubmit={handleSubmit(onSubmit)} className="input-group">
        <input type="email" className="form-control" placeholder="you@company.com" {...register('email', { required: true })} />
        <Button type="submit" loading={isSubmitting}>Subscribe</Button>
      </form>
    </Card>
  )
}
