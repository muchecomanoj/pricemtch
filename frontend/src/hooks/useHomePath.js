import { useAuth } from '../context/AuthContext'
import { homePathForUser, PLATFORM_ROLES } from '../constants'

// Where "Home" goes for the signed-in user. Pages write their breadcrumbs as
// Home → /dashboard, but the platform owner has no company and so no
// dashboard — for them home is Clients. Signed out, /dashboard is kept: the
// route guard sends it to the login page.
export function useHomePath() {
  const { user } = useAuth()
  return user ? homePathForUser(user) : '/dashboard'
}

// Swap the generic /dashboard link for this user's real home.
export const toHome = (to, home) => (to === '/dashboard' ? home : to)

// The breadcrumb as this user should see it. The platform owner's screens are
// all one level deep and their home is just Clients — already in the sidebar —
// so a leading "Home" crumb only duplicated it. Company users keep theirs: it
// is the way back to their dashboard.
export function useVisibleCrumbs(items = []) {
  const { user, hasRole } = useAuth()
  const isPlatform = !!user?.superAdmin || hasRole(PLATFORM_ROLES)
  return isPlatform ? items.filter((c, i) => !(i === 0 && c.label === 'Home')) : items
}
