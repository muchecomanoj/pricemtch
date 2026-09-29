import { Link } from 'react-router-dom'

export default function ErrorPage() {
  return (
    <div className="login-shell text-white text-center p-4">
      <div>
        <h1 className="display-3 fw-bold">Something went wrong</h1>
        <p className="lead">An unexpected error occurred. Please try again.</p>
        <Link to="/dashboard" className="btn btn-light mt-2">Back to Dashboard</Link>
      </div>
    </div>
  )
}
