import { Link } from 'react-router-dom'
import { useHomePath } from '../../hooks/useHomePath'

export default function ErrorPage() {
  // The platform owner has no dashboard; home is wherever their menu starts.
  const home = useHomePath()
  return (
    <div className="login-shell text-white text-center p-4">
      <div>
        <h1 className="display-3 fw-bold">Something went wrong</h1>
        <p className="lead">An unexpected error occurred. Please try again.</p>
        <Link to={home} className="btn btn-light mt-2">{home === '/dashboard' ? 'Back to Dashboard' : 'Back to home'}</Link>
      </div>
    </div>
  )
}
