import { useState } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { motion, AnimatePresence } from 'framer-motion'
import SubscriptionBanner from '../components/layout/SubscriptionBanner'
import Sidebar from '../components/layout/Sidebar'
import Navbar from '../components/layout/Navbar'
import Footer from '../components/layout/Footer'
import { useAuth } from '../context/AuthContext'
import { useNotification } from '../context/NotificationContext'
import ErrorBoundary from '../components/common/ErrorBoundary'

export default function MainLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(false)   // mobile drawer
  const [collapsed, setCollapsed] = useState(false)       // desktop rail
  const { logout } = useAuth()
  const { notify } = useNotification()
  const navigate = useNavigate()
  const location = useLocation()

  const handleLogout = async () => {
    await logout()
    notify.info('Signed out')
    // Replace, with no state: signing out is not an interruption to come back
    // from, so the next login starts at the user's home screen.
    navigate('/login', { replace: true, state: null })
  }

  return (
    <div className="app-shell">
      <Sidebar
        open={sidebarOpen}
        collapsed={collapsed}
        onNavigate={() => setSidebarOpen(false)}
        onToggleCollapse={() => setCollapsed((c) => !c)}
      />
      {sidebarOpen && (
        <div className="modal-backdrop fade show d-lg-none" onClick={() => setSidebarOpen(false)} />
      )}

      <div className={`app-main ${collapsed ? 'collapsed' : ''}`}>
        <Navbar onToggleSidebar={() => setSidebarOpen((o) => !o)} onLogout={handleLogout} />
        <main className="app-content">
          {/* Outside the page transition: it belongs to the account, not to
              whichever page is showing, so it should not fade on navigation. */}
          <SubscriptionBanner />
          {/* Page transition — purely visual, does not affect routing */}
          <AnimatePresence mode="wait">
            <motion.div
              key={location.pathname}
              initial={{ opacity: 0, y: 12 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -8 }}
              transition={{ duration: 0.24, ease: 'easeOut' }}
            >
              {/* Keyed on the path so navigating away from a broken page
                  clears the error rather than carrying it to the next one. */}
              <ErrorBoundary resetKey={location.pathname}>
                <Outlet />
              </ErrorBoundary>
            </motion.div>
          </AnimatePresence>
        </main>
        <Footer />
      </div>
    </div>
  )
}
