import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FiMenu, FiMoon, FiSun, FiUser, FiUsers, FiLogOut, FiChevronDown } from 'react-icons/fi'
import { useTheme } from '../../context/ThemeContext'
import { useAuth } from '../../context/AuthContext'
import { ROLE_LABELS, PLATFORM_ROLES } from '../../constants'
import Avatar from '../common/Avatar'
import NotificationBell from './NotificationBell'

export default function Navbar({ onToggleSidebar, onLogout }) {
  const { theme, toggleTheme } = useTheme()
  const { user, hasRole } = useAuth()
  const [open, setOpen] = useState(false)
  const wrapRef = useRef(null)
  const navigate = useNavigate()

  // Dismiss on outside click or Escape — same behaviour as the bell.
  useEffect(() => {
    if (!open) return undefined
    const onPointerDown = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false)
    }
    const onKeyDown = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const go = (to) => { setOpen(false); navigate(to) }

  // Platform owner only. Tenant admins can still reach /users directly, but
  // it is not offered here. Both representations are accepted: the backend
  // marks the platform owner with a superAdmin flag, and the role string is
  // checked too in case only that is present.
  const isSuperAdmin = !!user?.superAdmin || hasRole(PLATFORM_ROLES)

  return (
    <header className="app-navbar d-flex align-items-center gap-3 px-3 px-lg-4">
      <button className="icon-btn d-lg-none" onClick={onToggleSidebar} aria-label="Toggle menu">
        <FiMenu />
      </button>

      {/* Global search, Quick add and Messages removed — none was wired to
          anything, and each page has its own search where it belongs. */}
      <div className="ms-auto d-flex align-items-center gap-2">
        <NotificationBell />
        <button className="icon-btn" onClick={toggleTheme} aria-label="Toggle theme">
          {theme === 'dark' ? <FiSun /> : <FiMoon />}
        </button>

        <div className="vr mx-1 d-none d-sm-block" style={{ opacity: 0.2 }} />

        {/* Account menu — Profile, user management and Logout all live here
            now, so signing out is reachable from every screen without
            hunting for the sidebar. */}
        <div className="position-relative" ref={wrapRef}>
          <button
            type="button"
            className={`profile-chip ${open ? 'is-open' : ''}`}
            aria-haspopup="menu"
            aria-expanded={open}
            aria-label="Account menu"
            onClick={() => setOpen((o) => !o)}
          >
            <Avatar user={user} size={36} />
            <span className="d-none d-md-block text-start lh-1">
              <span className="d-block small fw-semibold">{user?.name}</span>
              <span className="d-block text-secondary" style={{ fontSize: 11 }}>
                {ROLE_LABELS[user?.role] || user?.role}
              </span>
            </span>
            <FiChevronDown
              className="d-none d-md-block text-secondary flex-shrink-0" size={15}
              style={{ transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 0.2s' }}
            />
          </button>

          {open && (
            <div className="profile-menu" role="menu">
              <div className="profile-menu__head">Welcome {user?.name}</div>

              <button type="button" role="menuitem" className="profile-menu__item" onClick={() => go('/settings')}>
                <FiUser size={16} /> Profile
              </button>

              {/* Goes to the platform Clients screen — that is where the
                  owner administers tenants and their users, not /users,
                  which is a tenant admin's own user list. */}
              {isSuperAdmin && (
                <button type="button" role="menuitem" className="profile-menu__item" onClick={() => go('/platform/clients')}>
                  <FiUsers size={16} /> Manage User
                </button>
              )}

              <div className="profile-menu__sep" />

              <button
                type="button" role="menuitem" className="profile-menu__item is-danger"
                onClick={() => { setOpen(false); onLogout?.() }}
              >
                <FiLogOut size={16} /> Logout
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
