package com.priceintel.backend.entity;

import java.time.LocalDate;

import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A client organization (tenant) on the platform. Holds company profile,
 * subscription snapshot, and lifecycle status. All tenant users and data are
 * scoped to their tenant.
 */
@Entity
@Table(name = "tenants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenants_company_code", columnNames = "company_code"),
        @UniqueConstraint(name = "uk_tenants_company_email", columnNames = "company_email"),
        @UniqueConstraint(name = "uk_tenants_company_name", columnNames = "company_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "company_code", nullable = false, length = 50)
    private String companyCode;

    @Column(name = "company_email", nullable = false, length = 150)
    private String companyEmail;

    @Column(name = "company_phone", length = 30)
    private String companyPhone;

    @Column(name = "company_address", length = 500)
    private String companyAddress;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 100)
    private String country;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(length = 60)
    private String timezone;

    @Column(length = 10)
    private String currency;

    @Column(length = 200)
    private String website;

    /** Logo as a URL or a base64 data: URI (direct upload) — stored as TEXT (no length cap). */
    @Column(columnDefinition = "TEXT")
    private String logo;

    /** Main point of contact at the client organization. */
    @Column(name = "contact_person", length = 150)
    private String contactPerson;

    @Column(length = 100)
    private String industry;

    /** GST / tax registration number (India: GSTIN). */
    @Column(length = 40)
    private String gstin;

    @Column(name = "tax_id", length = 40)
    private String taxId;

    @Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "billing_cycle", length = 10)
    private com.priceintel.backend.constants.BillingCycle billingCycle;

    @Column(name = "trial_end_date")
    private LocalDate trialEndDate;

    // ---- subscription snapshot ----
    @Column(name = "subscription_plan", length = 50)
    private String subscriptionPlan; // plan code

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", length = 20)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.TRIAL;

    @Column(name = "subscription_start_date")
    private LocalDate subscriptionStartDate;

    @Column(name = "subscription_end_date")
    private LocalDate subscriptionEndDate;

    @Builder.Default
    @Column(name = "max_users", nullable = false)
    private int maxUsers = 5;

    // ---- scheduled downgrade (applied by a daily job at period end) ----
    /** Plan code the tenant will move to when the current period ends. */
    @Column(name = "scheduled_plan_code", length = 50)
    private String scheduledPlanCode;

    /** Billing cycle for the scheduled plan change. */
    @Enumerated(EnumType.STRING)
    @Column(name = "scheduled_billing_cycle", length = 10)
    private com.priceintel.backend.constants.BillingCycle scheduledBillingCycle;

    /** When the scheduled downgrade becomes effective (= current period end). */
    @Column(name = "scheduled_plan_effective_date")
    private LocalDate scheduledPlanEffectiveDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantStatus status = TenantStatus.ACTIVE;

    public boolean isDeleted() {
        return status == TenantStatus.DELETED;
    }
}
