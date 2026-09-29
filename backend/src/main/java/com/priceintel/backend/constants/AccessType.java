package com.priceintel.backend.constants;

/**
 * The access level a tenant user holds inside their own organization. This
 * replaces per-platform roles for tenant users. SUPER_ADMIN is NOT an access
 * type — it is the single platform role (see {@code User.superAdmin}).
 */
public enum AccessType {
    ADMIN,     // full access inside the tenant (the "Client" / Tenant Administrator)
    MANAGER,   // product management, reports, dashboard
    ANALYST,   // product search, competitor analysis, AI, reports
    FINANCE,   // cost, profit, ROI, reports
    VIEWER     // read only
}
