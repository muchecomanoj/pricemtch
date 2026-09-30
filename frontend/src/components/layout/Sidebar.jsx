import { useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { FiChevronsLeft, FiChevronDown, FiChevronRight } from 'react-icons/fi'
import { NAV_ITEMS, MASTER_DATA } from '../../constants/navigation'
import { APP_NAME } from '../../constants'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../../context/AuthContext'
import { tenantService } from '../../services/tenantService'

// Presentational grouping of the existing flat nav (no routing change).
//
// Every path a group does NOT name is picked up by the catch-all below. This
// list used to be the whole story, so a page added to NAV_ITEMS but forgotten
// here vanished from the sidebar with no error anywhere — which is exactly what
// happened to Bulk Search, Tracked Items and Price Changes.
const GROUPS = [
  { label: 'Overview', paths: ['/dashboard'] },
  {
    label: 'Catalog',
    // /search/bulk is deliberately absent: it is reached from the Search page,
    // not from the sidebar. Leaving it here would put it in the catch-all
    // "More" group the moment it left NAV_ITEMS, which is the opposite of
    // removing it.
    paths: ['/products', '/search', '/competitors', '/tracked-items',
      '/match-review', '/monitors'],
  },
  {
    label: 'Intelligence',
    paths: ['/price-intelligence', '/price-changes', '/costs', '/profitability', '/recommendations'],
  },
  { label: 'Operations', paths: ['/alerts', '/reports', '/ai-analyst', '/ai-activity'] },
  { label: 'Account', paths: ['/company', '/my-subscription'] },
  { label: 'Platform', paths: ['/platform/clients', '/platform/plans', '/platform/submissions', '/platform/landing-content'] },
  { label: 'Admin', paths: ['/users', '/settings'] },
]

// Paths any group claims, so the catch-all can find the ones none does.
const GROUPED_PATHS = new Set(GROUPS.flatMap((g) => g.paths))

// Initials for the workspace mark — two words give two letters, one gives two
// characters of that word, so "SG" and "Ro" beat a single floating letter.
function initialsOf(name = '') {
  const words = name.trim().split(/\s+/).filter(Boolean)
  if (!words.length) return '—'
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase()
  return (words[0][0] + words[words.length - 1][0]).toUpperCase()
}

function WorkspaceChip({ workspace, to, onNavigate }) {
  const inner = (
    <>
      <span className="workspace-chip__mark">{initialsOf(workspace.name)}</span>
      <span className="workspace-meta flex-grow-1 overflow-hidden">
        <span className="d-block small fw-semibold text-white text-truncate">{workspace.name}</span>
        {workspace.plan && (
          <span className="d-block text-secondary text-truncate" style={{ fontSize: 11 }}>
            {workspace.plan}
          </span>
        )}
      </span>
    </>
  )
  // Only an admin can open the company profile, so for everyone else this is
  // an identity marker rather than a control that looks clickable and isn't.
  return to
    ? <Link to={to} className="workspace-chip" onClick={onNavigate} title="Company profile">{inner}</Link>
    : <div className="workspace-chip is-static">{inner}</div>
}

export default function Sidebar({ open, collapsed, onNavigate, onToggleCollapse }) {
  const { hasRole, user, can } = useAuth()
  const canManageCompany = can('MANAGE_COMPANY')

  // A platform owner has no tenant of their own, so /client/company would 404.
  const isPlatform = !!user?.superAdmin
  const { data: company } = useQuery({
    queryKey: ['company'],
    queryFn: tenantService.company,
    enabled: !isPlatform,
    staleTime: 5 * 60 * 1000,
    retry: false,   // a role without access shouldn't retry a 403 three times
  })

  const workspace = isPlatform
    ? { name: APP_NAME, plan: 'Platform administration' }
    : company?.companyName
      ? { name: company.companyName, plan: company.subscriptionPlan ? `${company.subscriptionPlan} plan` : null }
      : null
  const location = useLocation()
  const visible = NAV_ITEMS.filter((it) => hasRole(it.roles))

  // Master Data expandable submenu — auto-open when a child route is active.
  const mdVisible = hasRole(MASTER_DATA.roles)
  const mdChildActive = MASTER_DATA.children.some((c) => location.pathname.startsWith(c.path))
  const [mdOpen, setMdOpen] = useState(mdChildActive)

  return (
    <aside className={`app-sidebar ${open ? 'open' : ''} ${collapsed ? 'collapsed' : ''}`}>
      {/* Logo */}
      <div className="sidebar-head">
        <div className="d-flex align-items-center gap-2 overflow-hidden">
          <div className="d-grid rounded-3 text-white fw-bold flex-shrink-0"
            style={{ width: 38, height: 38, placeItems: 'center', background: 'var(--grad-primary)' }}>
            PI
          </div>
          <span className="sidebar-label fw-bold text-white text-truncate" style={{ fontFamily: 'Plus Jakarta Sans' }}>{APP_NAME}</span>
        </div>
        {/* Not Bootstrap's .text-secondary: that follows the app theme, and the
            sidebar is dark in both — in light mode it rendered dark on dark. */}
        <button type="button" className="sidebar-collapse-btn" onClick={onToggleCollapse}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}>
          <FiChevronsLeft size={16} style={{ transform: collapsed ? 'rotate(180deg)' : 'none', transition: 'transform 0.25s' }} />
        </button>
      </div>

      {/* Which workspace you are signed into. Was hardcoded to one tenant's
          name and plan, so every client saw the same company. */}
      {workspace && (
        <div className="px-3 pb-2">
          <WorkspaceChip workspace={workspace} to={canManageCompany ? '/company' : null} onNavigate={onNavigate} />
        </div>
      )}

      {/* Navigation groups */}
      <nav className="sidebar-scroll py-2">
        {[
          ...GROUPS,
          // Anything routed and permitted but not placed in a group above. A
          // page missing from the sidebar is indistinguishable from a page that
          // does not exist, so it appears here rather than nowhere.
          { label: 'More', paths: visible.filter((it) => !GROUPED_PATHS.has(it.path)).map((it) => it.path) },
        ].map((group) => {
          const items = visible.filter((it) => group.paths.includes(it.path))
          if (!items.length) return null
          return (
            <div key={group.label} className="mb-1">
              <div className="sidebar-group-label">{group.label}</div>
              {items.map((it) => (
                <NavLink key={it.path} to={it.path} className="nav-link" onClick={onNavigate} title={it.label}>
                  <it.icon size={18} />
                  <span className="sidebar-label flex-grow-1">{it.label}</span>
                  {it.badge && <span className="badge text-bg-danger sidebar-label">{it.badge}</span>}
                </NavLink>
              ))}

              {/* Master Data expandable submenu appears at the end of the Admin group */}
              {group.label === 'Admin' && mdVisible && (
                <>
                  <button
                    type="button"
                    className={`nav-link w-100 border-0 bg-transparent ${mdChildActive ? 'text-white' : ''}`}
                    onClick={() => setMdOpen((o) => !o)}
                    title={MASTER_DATA.label}
                    aria-expanded={mdOpen}
                  >
                    <MASTER_DATA.icon size={18} />
                    <span className="sidebar-label flex-grow-1 text-start">{MASTER_DATA.label}</span>
                    <span className="sidebar-label">
                      {mdOpen ? <FiChevronDown size={15} /> : <FiChevronRight size={15} />}
                    </span>
                  </button>

                  {mdOpen && (
                    <div className="submenu">
                      {MASTER_DATA.children.map((child) => (
                        <NavLink
                          key={child.path}
                          to={child.path}
                          className="nav-link submenu-link"
                          onClick={onNavigate}
                          title={child.label}
                        >
                          <child.icon size={15} />
                          <span className="sidebar-label flex-grow-1">{child.label}</span>
                          {!child.live && <span className="sidebar-label badge text-bg-secondary" style={{ fontSize: 9 }}>soon</span>}
                        </NavLink>
                      ))}
                    </div>
                  )}
                </>
              )}
            </div>
          )
        })}
      </nav>

      {/* The bottom profile card is gone: identity and sign-out both live in
          the navbar account menu now, and duplicating them here only cost
          vertical space the nav could use. */}
    </aside>
  )
}
