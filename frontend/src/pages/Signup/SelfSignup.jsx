import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearchParams, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import { FiCreditCard, FiCheck } from 'react-icons/fi'
import StepBar from '../../components/onboarding/StepBar'
import SuccessScreen from '../../components/onboarding/SuccessScreen'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import { signupService } from '../../services/signupService'
import { SIGNUP_STEPS } from '../../mock/onboarding'
import { APP_NAME } from '../../constants'
import { publicService } from '../../services/publicService'
import { usePaymentConfirm } from '../../hooks/usePaymentConfirm'
import { normalizePlans, isFreePlan } from '../../utils/plans'
import ChoosePlan from './steps/ChoosePlan'
import Profile from './steps/Profile'
import Verify from './steps/Verify'
import CreatePassword from './steps/CreatePassword'
import Payment from './steps/Payment'
import Done from './steps/Done'

// Public self-signup: Plan → Profile → Verify → Password → Payment → Done.
// A free plan skips Payment: Password goes straight to Done.
const SCREENS = ['plan', 'profile', 'verify', 'password', 'payment', 'done']
const FREE_STEPS = SIGNUP_STEPS.filter((s) => s.key !== 'payment')
// Paid plans are charged from day one now, so the header promises no trial.
const SUBTITLE = 'Set up your workspace'

export default function SelfSignup() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const location = useLocation()
  const [screen, setScreen] = useState(0)
  const [data, setData] = useState({
    planCode: params.get('plan') || 'PRO',
    cycle: 'YEARLY',
    profile: {},
    email: '',
    token: '',      // from POST /register, carried through the rest of the flow
    otpCode: '',    // carried into set-password
    result: null,   // final SelfRegisterResponse
  })

  const goToLogin = (loginUrl) => {
    const url = loginUrl || '/login'
    if (/^https?:\/\//i.test(url)) window.location.assign(url)
    else navigate(url)
  }

  // Live plan catalog — falls back to the bundled list if the API is unreachable,
  // so signup never dead-ends on a marketing endpoint being down.
  const plansQuery = useQuery({
    queryKey: ['public', 'plans'],
    queryFn: publicService.plans,
    staleTime: 5 * 60 * 1000,
  })
  const plans = normalizePlans(plansQuery.data)

  // Return leg from the hosted (Stripe) checkout. The backend sets its own
  // redirect URLs, so the browser lands on /register/success (or /register/cancel)
  // rather than the URLs the client sent. The in-memory token/state is gone, so
  // instead of dropping the user back on the plan step we read the path and show
  // the right screen; the payment itself is recorded server-side.
  const path = location.pathname
  const paidReturn = /\/register\/success/.test(path)
    || params.get('paid') === '1' || params.get('redirect_status') === 'succeeded'
  const canceledReturn = /\/register\/cancel/.test(path) || params.get('canceled') === '1'

  // Confirm the checkout session against the public endpoint so activation is
  // instant (the backend reconciler would also complete it within ~60s).
  const { status: confirmStatus } = usePaymentConfirm({
    active: paidReturn, sessionId: params.get('session_id'),
  })

  const key = SCREENS[screen]
  const plan = plans.find((p) => p.code === data.planCode)
    || plans.find((p) => p.recommended)
    || plans[0]
  const free = isFreePlan(plan)
  const patch = (p) => setData((d) => ({ ...d, ...p }))
  const next = () => setScreen((s) => Math.min(s + 1, SCREENS.length - 1))
  const back = () => setScreen((s) => Math.max(s - 1, 0))

  // On a free plan the checkout call is still what creates the account — the
  // backend answers mode FREE with nothing to pay — so it runs straight after
  // the password rather than being presented as a payment step.
  const afterPassword = async () => {
    if (!free) return next()
    const result = await signupService.checkout(data.token)
    patch({ result })
    setScreen(SCREENS.indexOf('done'))
  }

  // Steps shown in the top bar (Done is the success screen, not a step).
  // Free has 4 steps, so on Free, Done's index 4 marks them all complete.
  const steps = free ? FREE_STEPS : SIGNUP_STEPS
  const stepIndex = { plan: 0, profile: 1, verify: 2, password: 3, payment: 4, done: 4 }[key]

  if (paidReturn) {
    return (
      <div className="ob-shell flex-column">
        <StepBar steps={SIGNUP_STEPS} currentIndex={SIGNUP_STEPS.length}
          title={`Create your ${APP_NAME} account`} subtitle={SUBTITLE}
          onExit={() => navigate('/login')} />
        <div className="ob-main">
          <div className="ob-body ob-center">
            <div className="ob-narrow">
              <SuccessScreen title="Payment successful 🎉"
                message={confirmStatus === 'success'
                  ? 'Your subscription is now active. You can sign in to your account.'
                  : 'Your subscription is being activated. You can sign in to your account now.'}>
                <Button icon={FiCheck} onClick={() => navigate('/login')}>Go to sign in</Button>
              </SuccessScreen>
            </div>
          </div>
        </div>
      </div>
    )
  }

  if (canceledReturn) {
    return (
      <div className="ob-shell flex-column">
        <StepBar steps={SIGNUP_STEPS} currentIndex={4}
          title={`Create your ${APP_NAME} account`} subtitle={SUBTITLE}
          onExit={() => navigate('/login')} />
        <div className="ob-main">
          <div className="ob-body ob-center">
            <Card className="text-center p-4 ob-narrow">
              <div className="icon-box icon-grad-warning mx-auto mb-3"><FiCreditCard size={22} /></div>
              <h4 className="fw-bold mb-2">Checkout canceled</h4>
              <p className="text-muted mb-4">Your payment was not completed and you have not been charged. You can try again from the sign-up page.</p>
              <div className="d-flex gap-2 justify-content-center">
                <Button variant="light" onClick={() => navigate('/login')}>Back to sign in</Button>
                <Button onClick={() => navigate('/signup')}>Start over</Button>
              </div>
            </Card>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="ob-shell flex-column">
      <StepBar steps={steps} currentIndex={stepIndex}
        title={`Create your ${APP_NAME} account`} subtitle={SUBTITLE}
        onExit={() => navigate('/login')} />

      <div className="ob-main">
        <div className={`ob-body ${['verify', 'password', 'done'].includes(key) ? 'ob-center' : ''}`}>
          <AnimatePresence>
            <motion.div key={key} className="ob-content"
              initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.25 }}>

              {key === 'plan' && (
                <ChoosePlan plans={plans} loading={plansQuery.isLoading} selected={plan}
                  cycle={data.cycle}
                  onPlan={(c) => patch({ planCode: c })} onCycle={(c) => patch({ cycle: c })} onNext={next} />
              )}
              {key === 'profile' && (
                <Profile plan={plan} cycle={data.cycle} initial={data.profile}
                  onRegistered={({ token, email, profile }) => { patch({ token, email, profile }); next() }}
                  onBack={back} />
              )}
              {key === 'verify' && (
                <Verify email={data.email} token={data.token}
                  onVerified={(code) => { patch({ otpCode: code }); next() }} onBack={back} />
              )}
              {key === 'password' && (
                <CreatePassword token={data.token} code={data.otpCode} free={free}
                  onNext={afterPassword} onBack={back} />
              )}
              {key === 'payment' && (
                <Payment plan={plan} cycle={data.cycle} token={data.token}
                  onPaid={(result) => { patch({ result }); next() }} onBack={back} />
              )}
              {key === 'done' && (
                <Done email={data.email} plan={plan} free={free} result={data.result}
                  onGo={() => goToLogin(data.result?.loginUrl)} />
              )}
            </motion.div>
          </AnimatePresence>
        </div>
      </div>
    </div>
  )
}
