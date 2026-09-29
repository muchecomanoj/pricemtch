import { createContext, useContext } from 'react'
import { useQuery } from '@tanstack/react-query'
import { dashboardService } from '../services/dashboardService'

const DashboardContext = createContext(null)

export function DashboardProvider({ children }) {
  // The figures are recomputed on every request and the six-month chart is the
  // heavy one, so a minute of staleness is plenty. Returning to the tab
  // refreshes them, which is when a stale dashboard would be noticed.
  const opts = { staleTime: 60_000, refetchOnWindowFocus: true }
  const summary = useQuery({ queryKey: ['dashboard', 'summary'], queryFn: dashboardService.getSummary, ...opts })
  const charts = useQuery({ queryKey: ['dashboard', 'charts'], queryFn: dashboardService.getCharts, ...opts })
  const activities = useQuery({ queryKey: ['dashboard', 'activities'], queryFn: dashboardService.getActivities, ...opts })

  return (
    <DashboardContext.Provider value={{ summary, charts, activities }}>
      {children}
    </DashboardContext.Provider>
  )
}

export const useDashboard = () => useContext(DashboardContext)
