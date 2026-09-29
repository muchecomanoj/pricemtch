package com.priceintel.backend.service;

import com.priceintel.backend.entity.Tenant;

/**
 * Outbound email. When SMTP is not configured the message is logged instead of
 * sent, so the flow stays testable without a mail provider.
 */
public interface MailService {

    /** Activation email: encrypted link + separate verification code. */
    void sendActivationEmail(Tenant tenant, String toEmail, String recipientName,
                             String activationUrl, String verificationCode, int expiryHours);

    /** Self-registration: just the verification code (client is already on the site). */
    void sendVerificationCodeEmail(String toEmail, String recipientName,
                                   String companyName, String verificationCode, int expiryMinutes);

    /** Forgot password: the 6-digit reset code. */
    void sendPasswordResetCodeEmail(String toEmail, String recipientName,
                                    String resetCode, int expiryMinutes);

    /** Confirmation/welcome email once the account is activated. */
    void sendWelcomeEmail(Tenant tenant, String toEmail, String recipientName, String loginUrl);

    /**
     * Plan change (upgrade or scheduled downgrade): sends the client a Stripe
     * pay link to complete payment for the new plan.
     */
    void sendPlanChangePayLinkEmail(String toEmail, String recipientName, String planName,
                                    String billingCycle, java.math.BigDecimal amount, String currency,
                                    String checkoutUrl, boolean isUpgrade);

    /**
     * A plan-expiry reminder or expired notice. Always sent from the platform's
     * own mailbox, never a client's configured SMTP — it is the platform talking
     * to its customer, and a client's SMTP may itself be what stopped working.
     */
    void sendSubscriptionNotice(com.priceintel.backend.constants.EmailTemplateKey key, String toEmail,
                                String recipientName, java.util.Map<String, Object> vars);

    /** Sends a pre-rendered HTML email (used for template test-sends). */
    void sendRawHtml(String toEmail, String subject, String htmlBody);
}
