import api from './api'
import { ENDPOINTS } from './apiEndpoints'

// Live aggregates for the logged-in company. api's response interceptor
// unwraps the ApiResponse envelope, so res.data is already the payload.
//   GET /dashboard/summary     → array of KPI tiles, StatsCard's own shape
//   GET /dashboard/charts      → the six chart series
//   GET /dashboard/activities  → { searches, alerts, recommendations }
export const dashboardService = {
  async getSummary() { return (await api.get(ENDPOINTS.dashboard.summary)).data },
  async getCharts() { return (await api.get(ENDPOINTS.dashboard.charts)).data },
  async getActivities() { return (await api.get(ENDPOINTS.dashboard.activities)).data },
}
