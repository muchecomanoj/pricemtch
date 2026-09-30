import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { FiMail, FiAlertCircle } from 'react-icons/fi'
import Card from '../../components/common/Card'
import Button from '../../components/common/Button'
import Loader from '../../components/common/Loader'
import { publicService } from '../../services/publicService'
import { APP_NAME } from '../../constants'

// /unsubscribe?token=… — the link in every newsletter's footer.
//
// The backend answers success for any token, real or made up, so that nobody
// can test tokens to find the real ones. This page therefore has exactly one
// outcome to show. The only other case is the request not getting through.
export default function Unsubscribe() {
  const [params] = useSearchParams()
  const token = params.get('token') || ''
  const [state, setState] = useState(token ? 'working' : 'no-token')   // working | done | failed | no-token
  const sent = useRef(false)

  const run = async () => {
    setState('working')
    try {
      await publicService.unsubscribe(token)
      setState('done')
    } catch {
      setState('failed')
    }
  }

  useEffect(() => {
    // Once, even though StrictMode mounts effects twice in development.
    if (!token || sent.current) return
    sent.current = true
    run()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  return (
    <div className="d-flex align-items-center justify-content-center p-3" style={{ background: 'var(--app-bg)', minHeight: '100vh' }}>
      <div style={{ maxWidth: 440, width: '100%' }}>
        <Card className="text-center p-3">
          {state === 'working' && <Loader label="Removing you from the list…" />}

          {state === 'done' && (
            <>
              <div className="icon-box icon-grad-primary mx-auto mb-3"><FiMail size={22} /></div>
              <h4 className="fw-bold mb-2">You have been removed</h4>
              <p className="text-muted mb-4">You won’t receive the {APP_NAME} newsletter any more.</p>
              <Link to="/home" className="btn btn-light">Back to the website</Link>
            </>
          )}

          {state === 'failed' && (
            <>
              <div className="icon-box icon-grad-warning mx-auto mb-3"><FiAlertCircle size={22} /></div>
              <h4 className="fw-bold mb-2">That didn’t go through</h4>
              <p className="text-muted mb-4">We couldn’t reach the server. Please try again in a moment.</p>
              <Button onClick={run}>Try again</Button>
            </>
          )}

          {state === 'no-token' && (
            <>
              <div className="icon-box icon-grad-warning mx-auto mb-3"><FiAlertCircle size={22} /></div>
              <h4 className="fw-bold mb-2">This link is incomplete</h4>
              <p className="text-muted mb-4">Use the unsubscribe link at the bottom of any of our newsletters.</p>
              <Link to="/home" className="btn btn-light">Back to the website</Link>
            </>
          )}
        </Card>
      </div>
    </div>
  )
}
