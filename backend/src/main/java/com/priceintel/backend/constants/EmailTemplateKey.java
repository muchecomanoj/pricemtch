package com.priceintel.backend.constants;

/**
 * The editable email templates. Each carries a sensible default (subject + body)
 * used until a super admin overrides it, and the list of {{variables}} the
 * template supports (for the editor's "insert variable" chips).
 */
public enum EmailTemplateKey {

    CLIENT_ACTIVATION(
            "Client activation invite",
            "Sent when a super admin creates a client — carries the activation link + code.",
            "You're invited to {{appName}}",
            """
            Hi {{contactPerson}},

            {{companyName}} has been set up on {{appName}}. Click below to verify your email, create a password and activate your subscription.

            Activate your account: {{activationLink}}
            Your verification code: {{verificationCode}}

            This secure link expires in {{expiryHours}} hours. If you didn't expect this, you can ignore this email.""",
            "appName,contactPerson,companyName,activationLink,verificationCode,expiryHours,companyCode"),

    VERIFICATION_CODE(
            "Verification code",
            "Sent during self-registration — the 6-digit code to verify the email.",
            "Your {{appName}} verification code",
            """
            Hi {{contactPerson}},

            Enter this code to continue your {{companyName}} sign-up on {{appName}}:

            {{verificationCode}}

            This code expires in {{expiryMinutes}} minutes.""",
            "appName,contactPerson,companyName,verificationCode,expiryMinutes"),

    PASSWORD_RESET(
            "Password reset code",
            "The 6-digit reset code email from the forgot-password flow.",
            "Your {{appName}} password reset code",
            """
            Hi,

            Use this code to reset your {{appName}} password:

            {{code}}

            This code expires in {{expiryMinutes}} minutes. If you didn't request a reset, you can ignore this email.""",
            "appName,code,expiryMinutes"),

    WELCOME(
            "Welcome / payment confirmation",
            "Sent after a client's payment succeeds and their subscription activates.",
            "Welcome to {{appName}} — your {{planName}} plan is active",
            """
            Hi {{contactPerson}},

            Your payment was successful and {{companyName}} is now on the {{planName}} plan ({{billingCycle}}).

            Company code: {{companyCode}}
            Status: {{status}}
            {{trialLine}}

            Sign in to your workspace: {{loginUrl}}

            Thanks for choosing {{appName}}.""",
            "appName,contactPerson,companyName,companyCode,planName,billingCycle,status,trialLine,loginUrl"),

    PLAN_CHANGE_PAYLINK(
            "Plan change pay-link",
            "The Stripe pay-link email for a scheduled plan change (e.g. a downgrade at period end).",
            "Complete your payment for {{planName}}",
            """
            Hi {{contactPerson}},

            Your subscription for {{companyName}} is moving to the {{planName}} plan ({{billingCycle}}) for {{amount}}.

            Complete your payment to activate the new plan: {{payLink}}""",
            "appName,contactPerson,companyName,planName,billingCycle,amount,payLink"),
    SUBSCRIPTION_EXPIRING(
            "Plan expiry reminder",
            "Sent before a client's paid period ends (7 days and 1 day before, by default).",
            "Your {{appName}} {{planName}} plan ends {{endsIn}}",
            """
            Hi {{contactPerson}},
            The {{planName}} plan for {{companyName}} ends on {{paidThrough}} ({{endsIn}}).
            Renew now to keep searching marketplaces, running monitors and receiving alerts without interruption: {{renewLink}}
            Renewing early costs you nothing — the new period starts when the current one ends. Renewal: {{amount}} ({{billingCycle}}).
            If you don't renew, you keep full access for {{graceDays}} more day(s) after {{paidThrough}}. After that your account becomes read-only: you can still sign in and see your data, but searches, monitors and alerts pause until you renew.""",
            "appName,contactPerson,companyName,planName,billingCycle,amount,paidThrough,endsIn,daysLeft,graceDays,accessEndsOn,renewLink"),
    SUBSCRIPTION_EXPIRED(
            "Plan expired notice",
            "Sent when a client's plan expires and the account becomes read-only.",
            "Your {{appName}} {{planName}} plan has expired",
            """
            Hi {{contactPerson}},
            The {{planName}} plan for {{companyName}} ended on {{paidThrough}}, and your account is now read-only.
            You can still sign in, view and export your data. Marketplace searches, monitors and alerts are paused.
            Renew to restore full access straight away: {{renewLink}}""",
            "appName,contactPerson,companyName,planName,billingCycle,amount,paidThrough,renewLink");

    private final String displayName;
    private final String description;
    private final String defaultSubject;
    private final String defaultBody;
    private final String variables;

    EmailTemplateKey(String displayName, String description,
                     String defaultSubject, String defaultBody, String variables) {
        this.displayName = displayName;
        this.description = description;
        this.defaultSubject = defaultSubject;
        this.defaultBody = defaultBody;
        this.variables = variables;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String defaultSubject() {
        return defaultSubject;
    }

    public String defaultBody() {
        return defaultBody;
    }

    /** Comma-separated variable names (without braces) supported by this template. */
    public String variables() {
        return variables;
    }
}
