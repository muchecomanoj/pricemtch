package com.priceintel.backend.constants;

/** Category of an in-app notification (drives the icon/colour on the frontend). */
public enum NotificationType {
    PLAN_ASSIGNED,
    PLAN_UPGRADED,
    PLAN_DOWNGRADE_SCHEDULED,
    PLAN_DOWNGRADED,
    PAYMENT_SUCCESS,
    PAYMENT_PENDING,
    ACCOUNT_ACTIVATED,
    ALERT_TRIGGERED,

    /** A scheduled competitor check failed, and has not been failing already. */
    MONITOR_FAILED,

    /** A previously failing monitor completed again — closes the loop. */
    MONITOR_RECOVERED,

    /** Someone filled in a form on the public site: a demo request or a message. */
    LEAD_RECEIVED,

    GENERAL
}
