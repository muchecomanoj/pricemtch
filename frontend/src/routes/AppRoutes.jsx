import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from '../layouts/MainLayout'
import ProtectedRoute from './ProtectedRoute'
import Loader from '../components/common/Loader'
import { useAuth } from '../context/AuthContext'
import { ADMIN_ROLES, SCREEN_ACCESS, homePathForUser } from '../constants'

// The front door.
//
// Signed in: straight to that persona's home (platform owner -> Clients).
// Signed out: the public landing page, NOT the login screen — the bare domain
// is the address a prospective customer is given, and they have no account to
// log in with. It used to redirect everyone to homePathForUser(), which for a
// logged-out visitor resolves to /settings and lands them on /login.
function RootRedirect() {
  const { user, isAuthenticated, loading } = useAuth()
  if (loading) return <Loader />
  if (!isAuthenticated) return <Landing />
  return <Navigate to={homePathForUser(user)} replace />
}

// Lazy-loaded pages -> smaller initial bundle.
const Login = lazy(() => import('../pages/Login/Login'))
const TwoFactor = lazy(() => import('../pages/TwoFactor/TwoFactor'))
const ForgotPassword = lazy(() => import('../pages/ForgotPassword/ForgotPassword'))
const ClientOnboarding = lazy(() => import('../pages/Onboarding/ClientOnboarding'))
const SelfSignup = lazy(() => import('../pages/Signup/SelfSignup'))
const Dashboard = lazy(() => import('../pages/Dashboard/Dashboard'))
const Products = lazy(() => import('../pages/Products/Products'))
const ProductDetails = lazy(() => import('../pages/ProductDetails/ProductDetails'))
const SearchProduct = lazy(() => import('../pages/SearchProduct/SearchProduct'))
const CompetitorListings = lazy(() => import('../pages/CompetitorListings/CompetitorListings'))
const TrackedItems = lazy(() => import('../pages/TrackedItems/TrackedItems'))
const BulkSearch = lazy(() => import('../pages/BulkSearch/BulkSearch'))
const PriceChanges = lazy(() => import('../pages/PriceChanges/PriceChanges'))
const MatchReview = lazy(() => import('../pages/MatchReview/MatchReview'))
const Monitors = lazy(() => import('../pages/Monitors/Monitors'))
const PriceHistory = lazy(() => import('../pages/PriceHistory/PriceHistory'))
const CostManagement = lazy(() => import('../pages/CostManagement/CostManagement'))
const Profitability = lazy(() => import('../pages/Profitability/Profitability'))
const Recommendations = lazy(() => import('../pages/Recommendations/Recommendations'))
const Alerts = lazy(() => import('../pages/Alerts/Alerts'))
const Notifications = lazy(() => import('../pages/Notifications/Notifications'))
const Reports = lazy(() => import('../pages/Reports/Reports'))
const AIAnalyst = lazy(() => import('../pages/AIAnalyst/AIAnalyst'))
const Users = lazy(() => import('../pages/Users/Users'))
const Roles = lazy(() => import('../pages/Roles/Roles'))
const Categories = lazy(() => import('../pages/MasterData/Categories'))
const Brands = lazy(() => import('../pages/MasterData/Brands'))
const CostTypes = lazy(() => import('../pages/MasterData/CostTypes'))
const MarketplaceListingDetail = lazy(() => import('../pages/MarketplaceTools/MarketplaceListingDetail'))
const AIActivity = lazy(() => import('../pages/AIActivity/AIActivity'))
const Settings = lazy(() => import('../pages/Settings/Settings'))
const ClientsAdmin = lazy(() => import('../pages/Platform/ClientsAdmin'))
const ClientDetail = lazy(() => import('../pages/Platform/ClientDetail'))
const CreateClientWizard = lazy(() => import('../pages/Platform/CreateClientWizard'))
const PlansAdmin = lazy(() => import('../pages/Platform/PlansAdmin'))
const Company = lazy(() => import('../pages/Company/Company'))
const MySubscription = lazy(() => import('../pages/Company/MySubscription'))
const BillingReturn = lazy(() => import('../pages/Billing/BillingReturn'))
const PublicPricing = lazy(() => import('../pages/Public/PublicPricing'))
const Landing = lazy(() => import('../pages/Public/Landing'))
const NotFound = lazy(() => import('../pages/Error/NotFound'))
const Forbidden = lazy(() => import('../pages/Error/Forbidden'))
const ErrorPage = lazy(() => import('../pages/Error/ErrorPage'))

export default function AppRoutes() {
  return (
    <Suspense fallback={<Loader label="Loading page…" />}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/two-factor" element={<TwoFactor />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        {/* Alias — reset links in older emails may point here. */}
        <Route path="/reset-password" element={<ForgotPassword />} />
        <Route path="/signup" element={<SelfSignup />} />
        {/* Hosted-checkout (Stripe) return URLs the backend redirects to after
            self-registration payment. Same page renders the success/cancel state. */}
        <Route path="/register/success" element={<SelfSignup />} />
        <Route path="/register/cancel" element={<SelfSignup />} />
        {/* Plan upgrade/downgrade return from Stripe. PUBLIC — the client comes
            back from an external site and may have lost its login token, so this
            page must load without auth and confirm via the public endpoint. */}
        <Route path="/billing/success" element={<BillingReturn />} />
        <Route path="/billing/cancel" element={<BillingReturn />} />
        {/* Onboarding-invitation checkout return. Also public. */}
        <Route path="/onboarding/success" element={<ClientOnboarding />} />
        <Route path="/onboarding/cancel" element={<ClientOnboarding />} />
        {/* Client onboarding is genuinely standalone — the client isn't signed in,
            so there's no app shell to show. */}
        <Route path="/activate" element={<ClientOnboarding />} />
        <Route path="/home" element={<Landing />} />
        <Route path="/pricing" element={<Landing />} />
        <Route path="/pricing-legacy" element={<PublicPricing />} />

        {/* Authenticated area. Role access is centralized in SCREEN_ACCESS —
            each route is guarded by the same rules the sidebar uses. */}
        <Route element={<ProtectedRoute><MainLayout /></ProtectedRoute>}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/products" element={<ProtectedRoute roles={SCREEN_ACCESS['/products']}><Products /></ProtectedRoute>} />
          <Route path="/products/:id" element={<ProtectedRoute roles={SCREEN_ACCESS['/products']}><ProductDetails /></ProtectedRoute>} />
          <Route path="/search" element={<ProtectedRoute roles={SCREEN_ACCESS['/search']}><SearchProduct /></ProtectedRoute>} />
          {/* Job id in the URL so a run that takes half an hour can be closed
              and reopened, and the link shared. */}
          <Route path="/search/bulk" element={<ProtectedRoute roles={SCREEN_ACCESS['/search/bulk']}><BulkSearch /></ProtectedRoute>} />
          <Route path="/search/bulk/:jobId" element={<ProtectedRoute roles={SCREEN_ACCESS['/search/bulk']}><BulkSearch /></ProtectedRoute>} />
          <Route path="/competitors" element={<ProtectedRoute roles={SCREEN_ACCESS['/competitors']}><CompetitorListings /></ProtectedRoute>} />
          <Route path="/tracked-items" element={<ProtectedRoute roles={SCREEN_ACCESS['/tracked-items']}><TrackedItems /></ProtectedRoute>} />
          <Route path="/price-changes" element={<ProtectedRoute roles={SCREEN_ACCESS['/price-changes']}><PriceChanges /></ProtectedRoute>} />
          <Route path="/price-intelligence" element={<ProtectedRoute roles={SCREEN_ACCESS['/price-intelligence']}><PriceHistory /></ProtectedRoute>} />
          <Route path="/profitability" element={<ProtectedRoute roles={SCREEN_ACCESS['/profitability']}><Profitability /></ProtectedRoute>} />
          <Route path="/recommendations" element={<ProtectedRoute roles={SCREEN_ACCESS['/recommendations']}><Recommendations /></ProtectedRoute>} />
          <Route path="/alerts" element={<ProtectedRoute roles={SCREEN_ACCESS['/alerts']}><Alerts /></ProtectedRoute>} />
          <Route path="/reports" element={<Reports />} />
          {/* No role gate: notifications are addressed to the signed-in user,
              and the backend scopes them by the JWT. */}
          <Route path="/notifications" element={<Notifications />} />
          <Route path="/ai-analyst" element={<ProtectedRoute roles={SCREEN_ACCESS['/ai-analyst']}><AIAnalyst /></ProtectedRoute>} />
          {/* The marketplace search that lived here was a duplicate of
              /search — FR-SRCH-001 describes one search. Only the listing
              detail survives, reached from that search's channel results. */}
          <Route path="/marketplace-tools/:mp/:itemId" element={<ProtectedRoute roles={SCREEN_ACCESS['/marketplace-tools']}><MarketplaceListingDetail /></ProtectedRoute>} />
          <Route path="/ai-activity" element={<ProtectedRoute roles={SCREEN_ACCESS['/ai-activity']}><AIActivity /></ProtectedRoute>} />
          <Route path="/settings" element={<Settings />} />
          <Route path="/match-review" element={<ProtectedRoute roles={SCREEN_ACCESS['/match-review']}><MatchReview /></ProtectedRoute>} />
          <Route path="/monitors" element={<ProtectedRoute roles={SCREEN_ACCESS['/monitors']}><Monitors /></ProtectedRoute>} />
          <Route path="/costs" element={<ProtectedRoute roles={SCREEN_ACCESS['/costs']}><CostManagement /></ProtectedRoute>} />
          <Route path="/users" element={<ProtectedRoute roles={SCREEN_ACCESS['/users']}><Users /></ProtectedRoute>} />
          <Route path="/roles" element={<ProtectedRoute roles={SCREEN_ACCESS['/roles']}><Roles /></ProtectedRoute>} />
          {/* Platform admin (SYSTEM_ADMIN) */}
          <Route path="/platform/clients" element={<ProtectedRoute roles={SCREEN_ACCESS['/platform/clients']}><ClientsAdmin /></ProtectedRoute>} />
          {/* Static "new" must precede the dynamic ":id" so it isn't treated as an id. */}
          <Route path="/platform/clients/new" element={<ProtectedRoute roles={SCREEN_ACCESS['/platform/clients']}><CreateClientWizard /></ProtectedRoute>} />
          <Route path="/platform/clients/:id" element={<ProtectedRoute roles={SCREEN_ACCESS['/platform/clients']}><ClientDetail /></ProtectedRoute>} />
          <Route path="/platform/plans" element={<ProtectedRoute roles={SCREEN_ACCESS['/platform/plans']}><PlansAdmin /></ProtectedRoute>} />
          {/* Tenant self-service */}
          <Route path="/company" element={<ProtectedRoute roles={SCREEN_ACCESS['/company']}><Company /></ProtectedRoute>} />
          <Route path="/my-subscription" element={<ProtectedRoute roles={SCREEN_ACCESS['/my-subscription']}><MySubscription /></ProtectedRoute>} />
          {/* Master Data (admin-only). No CRUD endpoints exist yet, so these are
              backed by a localStorage store with the same shape as a REST
              service — see services/localMasterData.js. */}
          <Route path="/master-data/categories" element={
            <ProtectedRoute roles={ADMIN_ROLES}><Categories /></ProtectedRoute>
          } />
          <Route path="/master-data/brands" element={
            <ProtectedRoute roles={ADMIN_ROLES}><Brands /></ProtectedRoute>
          } />
          {/* Marketplaces and Currencies removed: channel credentials live in
              Settings → Marketplaces (§3), and `currency` is a tenant field
              edited on Company Profile (§11). */}
          <Route path="/master-data/cost-types" element={
            <ProtectedRoute roles={ADMIN_ROLES}><CostTypes /></ProtectedRoute>
          } />
        </Route>

        <Route path="/" element={<RootRedirect />} />
        <Route path="/403" element={<Forbidden />} />
        <Route path="/error" element={<ErrorPage />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  )
}
