package com.priceintel.backend.service;

import com.priceintel.backend.dto.request.RegisterSetPasswordRequest;
import com.priceintel.backend.dto.request.RegisterVerifyRequest;
import com.priceintel.backend.dto.request.SelfRegisterRequest;
import com.priceintel.backend.dto.response.CheckoutResponse;
import com.priceintel.backend.dto.response.SelfRegisterResponse;

/**
 * Landing-page self-registration. Flow: choose plan + profile → code emailed →
 * verify → set password → pay → tenant + user created (only on payment).
 */
public interface SelfRegistrationService {

    /** Submit plan + profile; emails a verification code. No tenant yet. */
    SelfRegisterResponse register(SelfRegisterRequest request);

    SelfRegisterResponse verifyCode(RegisterVerifyRequest request);

    SelfRegisterResponse setPassword(RegisterSetPasswordRequest request);

    /** Payment summary for the chosen plan/cycle (Stripe URL or MOCK). */
    CheckoutResponse checkout(String token);

    /** On payment success: create the Tenant + admin User and activate. */
    SelfRegisterResponse completePayment(String token);

    /** Reconciles pending self-registration checkouts against Stripe (scheduled job). */
    int reconcilePendingCheckouts();
}
