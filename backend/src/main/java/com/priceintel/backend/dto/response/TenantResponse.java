package com.priceintel.backend.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.priceintel.backend.constants.BillingCycle;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponse {

    private Long id;
    private String companyName;
    private String companyCode;
    private String companyEmail;
    private String companyPhone;
    private String companyAddress;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String timezone;
    private String currency;
    private String website;
    private String logo;
    private String contactPerson;
    private String industry;

    /** The tenant admin's login email. */
    private String adminEmail;

    /**
     * True when an activation email (link + verification code) was sent to the
     * client on creation. The client sets their own password — no password is
     * ever emailed or returned.
     */
    private boolean activationEmailSent;

    private String subscriptionPlan;
    private BillingCycle billingCycle;
    private SubscriptionStatus subscriptionStatus;
    private LocalDate subscriptionStartDate;
    private LocalDate subscriptionEndDate;
    private LocalDate trialEndDate;
    // Pending (scheduled) downgrade, so the UI can show "Downgrade to X on <date>".
    private String scheduledPlanCode;
    private BillingCycle scheduledBillingCycle;
    private LocalDate scheduledPlanEffectiveDate;
    private int maxUsers;

    private TenantStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
