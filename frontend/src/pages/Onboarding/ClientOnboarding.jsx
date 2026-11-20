import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import OnboardingStepper from '../../components/onboarding/OnboardingStepper'
import Loader from '../../components/common/Loader'
import { onboardingService } from '../../services/onboardingService'
import { CLIENT_STEPS } from '../../mock/onboarding'
import { publicService } from '../../services/publicService'
import { usePaymentConfirm } from '../../hooks/usePaymentConfirm'
import { normalizePlans, isFreePlan } from '../../utils/plans'
import OnboardAside from './steps/OnboardAside'
import Welcome from './steps/Welcome'
import VerifyEmail from './steps/VerifyEmail'
import CreatePassword from './steps/CreatePassword'
import CompleteProfile from './steps/CompleteProfile'
import ReviewPlan from './steps/ReviewPlan'
import ChooseBilling from './steps/ChooseBilling'
import Payment from './steps/Payment'
import Activated from './steps/Activated'
import { ExpiredLink, PaidReturn } from './steps/LinkStates'

// The activation payload names the assigned plan/cycle differently depending on
// backend version, so accept any of these rather than silently falling back.
// The live contract uses assignedPlan / assignedBillingCycle.
const readAssignedPlan = (inv) =>
  inv?.assignedPlan || inv?.subscriptionPlan || inv?.planCode || inv?.plan || ''
const readAssignedCycle = (inv) =>
  inv?.assignedBillingCycle || inv?.billingCycle || inv?.subscriptionBillingCycle || ''

// Wizard screens -> which CLIENT_STEPS milestone is "current".
// (The admin's create/plan/invite steps are deliberately not shown here — the
// client didn't perform them.)
const SCREENS = [
  { key: 'welcome', step: 'verified' },
  { key: 'otp', step: 'verified' },
  { key: 'password', step: 'password' },
  { key: 'profile', step: 'profile' },
  { key: 'review', step: 'review' },
  { key: 'billing', step: 'billing' },
  { key: 'payment', step: 'payment' },
  { key: 'done', step: 'active' },
]
// A free plan has no billing cycle and nothing to pay: Review Plan activates
// the workspace directly, so those two steps aren't shown — the same as
// self sign-up on Free.
const FREE_STEPS = CLIENT_STEPS.filter((s) => !['billing', 'payment'].includes(s.key))

export default function ClientOnboarding() {
  const [params] = useSearchParams()
  const location = useLocation()
  const token = params.get('token') || ''
  const navigate = useNavigate()

  // Return leg from the hosted (Stripe) checkout. The provider redirects the
  // browser to /onboarding/success (or our successUrl …&paid=1); the invitation
  // token is now consumed — so re-running the wizard would just hit the "invalid
  // link" page. Show the success state and confirm the payment (public, no auth)
  // so activation is instant instead of waiting on the ~60s reconciler.
  const paidReturn = /\/onboarding\/success/.test(location.pathname)
    || params.get('paid') === '1' || params.get('redirect_status') === 'succeeded'

  usePaymentConfirm({ active: paidReturn, sessionId: params.get('session_id') })

  const [screen, setScreen] = useState(0)
  const [invite, setInvite] = useState(null)
  const [invalid, setInvalid] = useState(false)
  // Collected across steps. `otpCode` is carried into set-password (backend re-checks it).
  // planCode starts empty: defaulting to a specific plan here would show the
  // client a plan their administrator never assigned.
  const [data, setData] = useState({ planCode: '', cycle: 'YEARLY', otpCode: '', profile: {}, activation: null })

  useEffect(() => {
    if (paidReturn) return   // don't re-validate a token the checkout already consumed
    onboardingService.validate(token)
      .then((inv) => {
        if (inv?.valid === false) { setInvalid(true); return }
        setInvite(inv)
        setData((d) => ({
          ...d,
          planCode: readAssignedPlan(inv) || d.planCode,
          cycle: readAssignedCycle(inv) || d.cycle,
        }))
      })
      .catch(() => setInvalid(true))
  }, [token, paidReturn])

  // The backend may return loginUrl as an absolute URL (http://…/login) or a
  // relative path. React Router's navigate() only handles in-app paths, so an
  // absolute URL must go through window.location; a relative one through router.
  const goToLogin = (loginUrl) => {
    const url = loginUrl || '/login'
    if (/^https?:\/\//i.test(url)) window.location.assign(url)
    else navigate(url)
  }

  // Live plan catalog, so names/prices match what the admin assigned.
  const plansQuery = useQuery({
    queryKey: ['public', 'plans'],
    queryFn: publicService.plans,
    staleTime: 5 * 60 * 1000,
  })
  const plans = normalizePlans(plansQuery.data)

  const next = () => setScreen((s) => Math.min(s + 1, SCREENS.length - 1))
  const back = () => setScreen((s) => Math.max(s - 1, 0))
  const patch = (p) => setData((d) => ({ ...d, ...p }))

  // Resolve strictly against the assigned code — never fall back to another
  // plan, or the client is shown (and charged for) something else entirely.
  const plan = plans.find((p) => p.code === data.planCode) || null
  const current = SCREENS[screen]
  const free = !!plan && isFreePlan(plan)
  const steps = free ? FREE_STEPS : CLIENT_STEPS
  const goTo = (key) => setScreen(SCREENS.findIndex((s) => s.key === key))
  // Short, card-sized steps — centred rather than pinned to the top.
  const isNarrow = ['welcome', 'otp', 'password'].includes(current.key)

  if (paidReturn) return <PaidReturn onGo={() => navigate('/login')} />
  if (invalid) return <ExpiredLink onBack={() => navigate('/login')} />
  if (!invite) return <div className="ob-shell"><div className="ob-main"><Loader label="Verifying your invitation…" /></div></div>

  return (
    <div className="ob-shell">
      <OnboardingStepper
        steps={steps}
        currentIndex={Math.max(0, steps.findIndex((s) => s.key === current.step))}
        completed={current.key === 'done'}
        title="Set up your workspace"
        subtitle={invite.companyName}
      />

      <div className="ob-main">
        {/* Short steps get vertically centred; tall forms stay top-aligned. */}
        <div className={`ob-body ${isNarrow ? 'ob-center' : ''}`}>
          <AnimatePresence mode="wait">
            <motion.div
              key={current.key}
              className={isNarrow ? '' : 'ob-content'}
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -12 }}
              transition={{ duration: 0.25, ease: 'easeOut' }}
            >
              {isNarrow ? (
                <div className="ob-split">
                  <OnboardAside stepKey={current.key} invite={invite} plan={plan} />
                  <div className="ob-split-card">
                    {current.key === 'welcome' && <Welcome invite={invite} plan={plan} onNext={next} />}
                    {current.key === 'otp' && (
                      <VerifyEmail invite={invite} token={token} onBack={back}
                        onVerified={(code) => { patch({ otpCode: code }); next() }} />
                    )}
                    {current.key === 'password' && (
                      <CreatePassword token={token} code={data.otpCode} onNext={next} onBack={back} />
                    )}
                  </div>
                </div>
              ) : (<>
              {current.key === 'profile' && (
                <CompleteProfile invite={invite} token={token} initial={data.profile}
                  onSave={(p) => { patch({ profile: p }); next() }} onBack={back} />
              )}
              {current.key === 'review' && (
                <ReviewPlan plan={plan} plans={plans} cycle={data.cycle} token={token}
                  onChangePlan={(c) => patch({ planCode: c })}
                  onChangeCycle={(c) => patch({ cycle: c })}
                  onNext={next} onBack={back}
                  onActivated={(activation) => { patch({ activation }); goTo('done') }}
                  onNeedsPayment={() => goTo('billing')} />
              )}
              {current.key === 'billing' && (
                <ChooseBilling plan={plan} value={data.cycle} token={token}
                  onChange={(c) => patch({ cycle: c })}
                  onNext={() => next()} onBack={back} />
              )}
              {current.key === 'payment' && (
                <Payment plan={plan} cycle={data.cycle} token={token}
                  onPaid={(activation) => { patch({ activation }); next() }} onBack={back} />
              )}
              {current.key === 'done' && (
                <Activated activation={data.activation} plan={plan} cycle={data.cycle} free={free}
                  email={invite.email} onGo={() => goToLogin(data.activation?.loginUrl)} />
              )}
              </>)}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </div>
  )
}
