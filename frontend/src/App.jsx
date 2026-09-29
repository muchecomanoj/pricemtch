import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ToastContainer } from 'react-toastify'
import { AuthProvider } from './context/AuthContext'
import { ThemeProvider } from './context/ThemeContext'
import { NotificationProvider } from './context/NotificationContext'
import AppRoutes from './routes/AppRoutes'
import ErrorBoundary from './components/common/ErrorBoundary'

const queryClient = new QueryClient({
  defaultOptions: { queries: { refetchOnWindowFocus: false, retry: 1 } },
})

export default function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <NotificationProvider>
            <AuthProvider>
              <BrowserRouter>
                <AppRoutes />
                <ToastContainer position="top-right" autoClose={3000} newestOnTop theme="colored" />
              </BrowserRouter>
            </AuthProvider>
          </NotificationProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  )
}
