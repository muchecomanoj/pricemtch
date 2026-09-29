import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import Loader from '../components/common/Loader'

// Guards authenticated areas and (optionally) enforces role access.
export default function ProtectedRoute({ children, roles }) {
  const { isAuthenticated, loading, hasRole } = useAuth()
  const location = useLocation()

  if (loading) return <Loader label="Checking session…" />
  if (!isAuthenticated) return <Navigate to="/login" state={{ from: location }} replace />
  if (roles && !hasRole(roles)) return <Navigate to="/403" replace />

  return children
}
