package com.priceintel.backend.service;

import com.priceintel.backend.dto.request.CheckoutRequest;
import com.priceintel.backend.dto.request.ChoosePlanRequest;
import com.priceintel.backend.dto.request.CompleteProfileRequest;
import com.priceintel.backend.dto.request.SetPasswordRequest;
import com.priceintel.backend.dto.request.ValidateActivationRequest;
import com.priceintel.backend.dto.request.VerifyActivationCodeRequest;
import com.priceintel.backend.dto.response.ActivationCompleteResponse;
import com.priceintel.backend.dto.response.ActivationValidationResponse;
import com.priceintel.backend.dto.response.CheckoutResponse;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.entity.User;

/**
 * Client onboarding: emailed link + code → verify → set password → complete
 * profile → choose plan → pay → account live (with free trial).
 */
public interface OnboardingService {

    void issueActivation(Tenant tenant, User adminUser);

    ActivationValidationResponse validate(ValidateActivationRequest request);

    ActivationValidationResponse verifyCode(VerifyActivationCodeRequest request);

    /** Sets the password; account moves to PENDING_PAYMENT (not yet active). */
    ActivationCompleteResponse setPassword(SetPasswordRequest request);

    /** Client enters company/business details. */
    ActivationCompleteResponse completeProfile(CompleteProfileRequest request);

    /** Client selects a plan + billing cycle. */
    ActivationCompleteResponse choosePlan(ChoosePlanRequest request);

    /** Starts payment for the selected plan (Stripe, or mock when unconfigured). */
    CheckoutResponse startCheckout(CheckoutRequest request);

    /** Completes payment (from the Stripe webhook or the mock endpoint) → ACTIVE. */
    ActivationCompleteResponse completePayment(String token);

    /** Reconciles pending onboarding checkouts against Stripe (scheduled job). */
    int reconcilePendingCheckouts();

    void resendActivation(Long tenantId);
}
