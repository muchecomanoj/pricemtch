import { useEffect, useState } from 'react'
import { paymentService } from '../services/paymentService'

// Confirm a Stripe checkout session against the PUBLIC endpoint (no auth), used
// by the self-registration and onboarding success screens so activation is
// instant instead of waiting on the ~60s backend reconciler.
//
// Returns { status, message }:
//   status: 'idle' | 'confirming' | 'success' | 'notdone' | 'error'
// When `active` is false or there is no sessionId, it stays 'idle' — the caller
// still shows its own success screen (the reconciler completes it regardless).
export function usePaymentConfirm({ active, sessionId }) {
  const [status, setStatus] = useState('idle')
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!active || !sessionId) return
    let alive = true
    setStatus('confirming')
    ;(async () => {
      try {
        const res = await paymentService.confirm(sessionId)
        if (!alive) return
        if (res?.success) { setStatus('success'); setMessage(res.message || '') }
        else { setStatus('notdone'); setMessage(res?.message || 'Payment is not completed yet.') }
      } catch (e) {
        if (!alive) return
        setStatus('error'); setMessage(e.message || '')
      }
    })()
    return () => { alive = false }
  }, [active, sessionId])

  return { status, message }
}
