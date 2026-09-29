// Mock JSON for the onboarding experience. Replace with real API payloads later.

// Plan codes MUST match the backend enum: FREE | BASIC | PRO | ENTERPRISE.
export const PLANS = [
  {
    code: 'FREE', name: 'Free Trial', icon: 'rocket', color: 'info',
    tagline: 'Try it out, no card required',
    price: { monthly: 0, yearly: 0 }, currency: 'USD',
    users: 3, storage: '5 GB', aiCredits: '500 / mo',
    features: ['Product search', 'Basic reports', 'Community support'],
    modules: ['Products', 'Reports'], support: 'Community',
  },
  {
    code: 'BASIC', name: 'Basic', icon: 'zap', color: 'primary',
    tagline: 'For small teams getting started',
    price: { monthly: 49, yearly: 588 }, currency: 'USD', // 49 × 12, no discount
    users: 10, storage: '50 GB', aiCredits: '2,000 / mo',
    features: ['Product management', 'Competitor pricing', 'Reports', 'Email support'],
    modules: ['Products', 'Pricing', 'Reports'], support: 'Email',
  },
  {
    code: 'PRO', name: 'Professional', icon: 'briefcase', color: 'success',
    tagline: 'Most popular for growing businesses', recommended: true,
    price: { monthly: 149, yearly: 1788 }, currency: 'USD', // 149 × 12, no discount
    users: 50, storage: '250 GB', aiCredits: '20,000 / mo',
    features: ['Everything in Basic', 'AI matching', 'Marketplace integration', 'Advanced analytics'],
    modules: ['Products', 'Pricing', 'AI Analyst', 'Reports'], support: 'Priority email',
  },
  {
    code: 'ENTERPRISE', name: 'Enterprise', icon: 'shield', color: 'warning',
    tagline: 'Custom scale and controls',
    price: { monthly: 499, yearly: 5988 }, currency: 'USD', // 499 × 12, no discount
    users: 500, storage: 'Unlimited', aiCredits: 'Unlimited',
    features: ['Everything in Pro', 'Priority support', 'Custom limits', 'SSO / SAML', 'Audit logs'],
    modules: ['All modules + custom'], support: 'Dedicated CSM 24/7',
  },
]

// Billing cycles — backend accepts MONTHLY | YEARLY only. No discount is baked
// in here; any yearly saving comes from the plan's own yearly price (backend).
export const BILLING_CYCLES = [
  { code: 'MONTHLY', label: 'Monthly', months: 1, discount: 0 },
  { code: 'YEARLY', label: 'Yearly', months: 12, discount: 0, best: true },
]

export const PAYMENT_METHODS = [
  { code: 'CARD', label: 'Credit / Debit Card', icon: 'card', desc: 'Visa, Mastercard, Amex' },
  { code: 'UPI', label: 'UPI', icon: 'smartphone', desc: 'GPay, PhonePe, Paytm' },
  { code: 'NETBANKING', label: 'Net Banking', icon: 'bank', desc: 'All major banks' },
  { code: 'PAYPAL', label: 'PayPal', icon: 'paypal', desc: 'Pay with your PayPal balance' },
  { code: 'STRIPE', label: 'Stripe', icon: 'stripe', desc: 'Secure card processing' },
  { code: 'RAZORPAY', label: 'Razorpay', icon: 'razorpay', desc: 'Cards, UPI & wallets' },
]

export const INDUSTRIES = [
  'Retail & E-commerce', 'Consumer Electronics', 'Fashion & Apparel', 'Health & Beauty',
  'Home & Garden', 'Sports & Outdoors', 'Toys & Games', 'Grocery', 'Automotive', 'Other',
]

export const CURRENCIES = ['USD', 'EUR', 'GBP', 'INR', 'AUD', 'CAD', 'SGD', 'AED']
export const LANGUAGES = ['English', 'Hindi', 'Spanish', 'French', 'German', 'Arabic']
export const DATE_FORMATS = ['DD/MM/YYYY', 'MM/DD/YYYY', 'YYYY-MM-DD', 'DD MMM YYYY']

// Invitation resolved from the token — mirrors ActivationValidationResponse.
export const mockInvitation = {
  valid: true,
  email: 'admin@acmeretail.com',
  companyName: 'Acme Retail Pvt Ltd',
  companyCode: 'ACMERET-2481',
  contactPerson: 'Priya Sharma',
  expiresAt: '2026-07-24T23:59:00',
}

// The two personas own different halves of onboarding, so each wizard shows
// ONLY the steps that persona actually performs.

// Super admin — steps 1-3 (everything before the invitation leaves).
export const ADMIN_STEPS = [
  { key: 'created', label: 'Client Details', desc: 'Organization info' },
  { key: 'plan', label: 'Plan Assigned', desc: 'Subscription selected' },
  { key: 'invited', label: 'Invitation Sent', desc: 'Secure link delivered' },
]

// Public self-signup — a company signing itself up (no admin invitation).
export const SIGNUP_STEPS = [
  { key: 'plan', label: 'Choose Plan', desc: 'Pick a plan' },
  { key: 'profile', label: 'Your Details', desc: 'Organization info' },
  { key: 'verify', label: 'Verify Email', desc: 'Confirm your email' },
  { key: 'password', label: 'Create Password', desc: 'Secure your account' },
  { key: 'payment', label: 'Payment', desc: 'Complete checkout' },
]

// Client — steps 4-12, performed later from the invitation link.
export const CLIENT_STEPS = [
  { key: 'verified', label: 'Verify Email', desc: 'Confirm your identity' },
  { key: 'password', label: 'Create Password', desc: 'Secure your account' },
  { key: 'profile', label: 'Complete Profile', desc: 'Organization details' },
  { key: 'review', label: 'Review Plan', desc: 'Confirm your subscription' },
  { key: 'billing', label: 'Choose Billing', desc: 'Pick a cycle' },
  { key: 'payment', label: 'Payment', desc: 'Complete checkout' },
  { key: 'active', label: 'Activated', desc: 'Ready to go' },
]

// Shown to the admin as "what happens next" once the invitation is sent —
// informational only; the client performs these.
export const CLIENT_NEXT_STEPS = CLIENT_STEPS

// The backend's checkout amount carries NO tax (amount = monthly × 12 × 0.8 for
// yearly). Keep the preview tax-free so it reconciles with what's actually charged.
export const TAX_RATE = 0
