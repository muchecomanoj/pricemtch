import { Link } from 'react-router-dom'

export default function Forbidden() {
  return (
    <div className="login-shell text-white text-center p-4">
      <div>
        <h1 className="display-1 fw-bold">403</h1>
        <p className="lead">You do not have permission to view this page.</p>
        <Link to="/dashboard" className="btn btn-light mt-2">Back to Dashboard</Link>
      </div>
    </div>
  )
}
