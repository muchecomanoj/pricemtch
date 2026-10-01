import { createContext, useContext, useEffect, useState, useCallback } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { authService } from '../services/authService'
import { CAPABILITIES } from '../constants'

const AuthContext = createContext(null)

// A blocked company may not use the app at all. Raised as an error so login,
// two-factor and session restore all refuse it the same way, with the
// backend's own sentence.
function assertNotBlocked(u) {
  if (u && !u.superAdmin && u.account?.access === 'BLOCKED') {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    throw new Error(u.account.message || 'Your organization cannot use the app right now.')
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const queryClient = useQueryClient()

  // Restore session on load.
  useEffect(() => {
    const token = localStorage.getItem('token')
    if (!token) { setLoading(false); return }
    authService
      .me()
      .then((u) => { assertNotBlocked(u); setUser(u) })
      .catch((e) => {
        localStorage.removeItem('token')
        // Blocked since the last visit: say so on the login page. That is the
        // error assertNotBlocked throws; a failed request (an expired session,
        // say) has a response and its raw "status code 401" is no notice.
        if (e?.message && !e?.response) {
          try { sessionStorage.setItem('loginNotice', e.message) } catch { /* storage blocked */ }
        }
      })
      .finally(() => setLoading(false))
  }, [])

  // A request refused mid-session with SUBSCRIPTION_EXPIRED (see api.js): the
  // plan lapsed while the user was working. Switch to read-only in place, so
  // the banner appears and the write buttons go, without a reload.
  useEffect(() => {
    const onReadOnly = (e) => setUser((u) => (u ? {
      ...u,
      account: { ...(u.account || {}), access: 'READ_ONLY', message: e.detail?.message || u.account?.message },
    } : u))
    window.addEventListener('account:read-only', onReadOnly)
    return () => window.removeEventListener('account:read-only', onReadOnly)
  }, [])

  // Persist tokens + set the user. Shared by password login and 2FA completion.
  //
  // The query cache is cleared first: it is keyed by resource, not by user, so
  // signing in as someone else in the same tab would otherwise serve the
  // previous session's cached rows — another tenant's products, for instance —
  // until each query refetched in the background.
  const applySession = useCallback(({ token, refreshToken, user: u }) => {
    assertNotBlocked(u)
    queryClient.clear()
    localStorage.setItem('token', token)
    if (refreshToken) localStorage.setItem('refreshToken', refreshToken)
    setUser(u)
    return u
  }, [queryClient])

  const login = useCallback(async (credentials) => {
    const res = await authService.login(credentials)
    // 2FA challenge — caller routes to the OTP page; don't authenticate yet.
    if (res?.twoFactorRequired) return res
    return applySession(res)
  }, [applySession])

  const logout = useCallback(async () => {
    await authService.logout().catch(() => {})
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    setUser(null)
    // Drop every cached response so nothing from this session survives into
    // the next one on a shared machine.
    queryClient.clear()
  }, [queryClient])

  // Re-read the session user after a profile edit, so the navbar name and
  // avatar reflect the change without a reload.
  const refreshUser = useCallback(async () => {
    const u = await authService.me()
    setUser(u)
    return u
  }, [])

  // Allow access if any of the user's roles is in the allowed list.
  const hasRole = useCallback(
    (allowed) => {
      if (!allowed || allowed.length === 0) return true
      if (!user) return false
      const mine = user.roles?.length ? user.roles : [user.role]
      return mine.some((r) => allowed.includes(r))
    },
    [user]
  )

  // The company's access level. The platform owner has no account and is never
  // restricted, so a missing account means FULL.
  const account = user && !user.superAdmin ? (user.account || null) : null
  const access = account?.access || 'FULL'
  const readOnly = access === 'READ_ONLY'

  // Action-level permission check against the CAPABILITIES matrix: the ROLE
  // only. An expired plan does not take buttons away — they stay visible and
  // lock themselves (Button's `write` prop, useWriteLock below), so a read-only
  // company can see what renewing gives back.
  const can = useCallback((capability) => hasRole(CAPABILITIES[capability]), [hasRole])

  return (
    <AuthContext.Provider value={{
      user, loading, login, applySession, logout, refreshUser, hasRole, can,
      account, access, readOnly, isAuthenticated: !!user,
    }}>
      {children}
    </AuthContext.Provider>
  )
}

export const useAuth = () => useContext(AuthContext)

// Props that lock a plain <button> while read-only — for the icon buttons that
// do not go through <Button write>. Spread LAST, so the lock wins over any
// title or disabled set before it:
//   <button className="icon-btn" title="Delete" onClick={…} {...writeLock}>
export function useWriteLock() {
  const auth = useContext(AuthContext)
  return auth?.readOnly ? { disabled: true, title: 'Renew to use this', 'aria-disabled': true } : {}
}
