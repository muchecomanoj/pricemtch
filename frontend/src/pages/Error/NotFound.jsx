import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <div className="login-shell text-white text-center p-4">
      <div>
        <h1 className="display-1 fw-bold">404</h1>
        <p className="lead">The page you are looking for does not exist.</p>
        <Link to="/dashboard" className="btn btn-light mt-2">Back to Dashboard</Link>
      </div>
    </div>
  )
}
