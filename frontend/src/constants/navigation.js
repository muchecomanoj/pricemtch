import {
  FiGrid, FiBox, FiSearch, FiSettings, FiBell, FiFileText,
  FiTrendingUp, FiDollarSign, FiList, FiShield,
  FiDatabase, FiTag, FiAward, FiRadio, FiActivity,
  FiBriefcase, FiLayers, FiHome, FiCreditCard,
} from 'react-icons/fi'
import { ADMIN_ROLES, SCREEN_ACCESS } from './index'

// Sidebar menu. Role access is centralized in SCREEN_ACCESS (constants/index.js)
// so the sidebar and the route guards can never drift apart.
export const NAV_ITEMS = [
  { label: 'Dashboard', path: '/dashboard', icon: FiGrid },
  { label: 'Products', path: '/products', icon: FiBox },
  { label: 'Search Product', path: '/search', icon: FiSearch },
  { label: 'Competitor Listings', path: '/competitors', icon: FiList },
  // Hidden from the sidebar, not removed: /tracked-items still routes, and the
  // eye icon on a price change still opens it. To restore, uncomment this row
  // and add FiEye back to the react-icons import above.
  // { label: 'Tracked Items', path: '/tracked-items', icon: FiEye },
  // Hidden from the sidebar, not removed: /match-review still routes, and the
  // unmatched-listing links still reach it.
  // { label: 'Match Review', path: '/match-review', icon: FiCheckSquare },
  { label: 'Monitors', path: '/monitors', icon: FiRadio },
  { label: 'Price Intelligence', path: '/price-intelligence', icon: FiTrendingUp },
  { label: 'Price Changes', path: '/price-changes', icon: FiActivity },
  { label: 'Cost Management', path: '/costs', icon: FiDollarSign },
  // Hidden from the sidebar, not removed: both still route, and a product's
  // Costs & Profit / Recommendations tabs still reach them.
  // { label: 'Profitability', path: '/profitability', icon: FiPieChart },
  // { label: 'Recommendations', path: '/recommendations', icon: FiTrendingUp },
  { label: 'Alerts', path: '/alerts', icon: FiBell },
  { label: 'Reports', path: '/reports', icon: FiFileText },
  // Hidden from the sidebar — the routes still exist and remain reachable by URL.
  // { label: 'AI Analyst', path: '/ai-analyst', icon: FiCpu },
  // { label: 'AI Activity', path: '/ai-activity', icon: FiCpu },
  // { label: 'Users', path: '/users', icon: FiUsers },
  { label: 'Settings', path: '/settings', icon: FiSettings },
  // Platform admin (SYSTEM_ADMIN)
  { label: 'Clients', path: '/platform/clients', icon: FiBriefcase },
  { label: 'Plans', path: '/platform/plans', icon: FiLayers },
  // Tenant self-service
  { label: 'Company', path: '/company', icon: FiHome },
  { label: 'My Subscription', path: '/my-subscription', icon: FiCreditCard },
].map((item) => ({ ...item, roles: SCREEN_ACCESS[item.path] ?? undefined }))

// Master Data — expandable parent menu (admin-only) with its own sub-items.
// `live: true` means the page is usable; anything else shows a "soon" badge.
//
// Scoped to what the FRD actually treats as tenant-owned reference data:
//   Roles       — §11 user/role/permission (RBAC)
//   Categories  — FR-PROD-003 canonical category; drives the product form
//   Brands      — FR-PROD-003 canonical brand; drives the product form
//   Cost Types  — FR-COST-001's fixed field set, reference only
// Currencies and Marketplaces were removed: §11 puts `currency` on the tenant
// (edited on Company Profile) and `channel_account` credentials belong to
// Settings → Marketplaces, per §3 "Configure channels, credentials".
export const MASTER_DATA = {
  label: 'Master Data',
  icon: FiDatabase,
  roles: ADMIN_ROLES,
  children: [
    { label: 'Roles', path: '/roles', icon: FiShield, live: true },
    { label: 'Categories', path: '/master-data/categories', icon: FiTag, live: true },
    { label: 'Brands', path: '/master-data/brands', icon: FiAward, live: true },
    { label: 'Cost Types', path: '/master-data/cost-types', icon: FiDollarSign, live: true },
  ],
}
