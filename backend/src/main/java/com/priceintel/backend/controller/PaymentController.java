package com.priceintel.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.CheckoutRequest;
import com.priceintel.backend.dto.request.ConfirmPaymentRequest;
import com.priceintel.backend.dto.request.MockPaymentRequest;
import com.priceintel.backend.dto.response.ActivationCompleteResponse;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.CheckoutResponse;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.payment.StripeService;
import com.priceintel.backend.service.OnboardingService;
import com.priceintel.backend.service.SelfRegistrationService;
import com.priceintel.backend.service.SubscriptionService;
import com.priceintel.backend.service.impl.SelfRegistrationServiceImpl;
import com.priceintel.backend.service.impl.SubscriptionServiceImpl;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Public payment endpoints for the onboarding flow.
 * <ul>
 *   <li>{@code /checkout} — start payment for the chosen plan.</li>
 *   <li>{@code /webhook} — Stripe calls this on successful payment.</li>
 *   <li>{@code /mock-confirm} — simulate success when Stripe is not configured.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/public/payment")
@RequiredArgsConstructor
@Tag(name = "Payment (Public)", description = "Subscription checkout, Stripe webhook, and mock confirmation")
public class PaymentController {

    private final OnboardingService onboardingService;
    private final SelfRegistrationService selfRegistrationService;
    private final SubscriptionService subscriptionService;
    private final StripeService stripeService;

    @PostMapping("/checkout")
    @Operation(summary = "Start payment for the selected plan (returns a Stripe URL, or MOCK mode)")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(@Valid @RequestBody CheckoutRequest request) {
        CheckoutResponse response = onboardingService.startCheckout(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Checkout ready"));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Stripe webhook — activates the subscription on checkout.session.completed")
    public ResponseEntity<ApiResponse<Void>> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (!stripeService.verifySignature(payload, signature)) {
            log.warn("Rejected Stripe webhook: invalid signature");
            throw new BadRequestException("Invalid webhook signature");
        }
        applyByReference(stripeService.extractClientReference(payload), "card");
        return ResponseEntity.ok(ApiResponse.success("Webhook processed"));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm payment from the success page (checks Stripe directly; no webhook needed)")
    public ResponseEntity<ApiResponse<Void>> confirm(@Valid @RequestBody ConfirmPaymentRequest request) {
        if (!stripeService.isConfigured()) {
            throw new BadRequestException("Stripe is not configured");
        }
        StripeService.SessionStatus session = stripeService.retrieveSession(request.getSessionId());
        if (!session.isPaid()) {
            throw new BadRequestException("Payment is not completed yet. Please finish checkout.");
        }
        applyByReference(session.clientReferenceId(), "card");
        return ResponseEntity.ok(ApiResponse.success("Payment confirmed; your account is active."));
    }

    /**
     * Routes a paid checkout to the right completion handler based on our
     * client_reference_id. Used by BOTH the webhook and the success-page confirm,
     * and is idempotent (each handler no-ops if already completed).
     */
    private void applyByReference(String reference, String method) {
        if (reference == null) {
            return;
        }
        if (reference.startsWith(SubscriptionServiceImpl.PAY_REF_PREFIX)) {
            // Plan change (upgrade / scheduled downgrade): apply the paid payment.
            Long paymentId = Long.valueOf(reference.substring(SubscriptionServiceImpl.PAY_REF_PREFIX.length()));
            subscriptionService.applyPaidPayment(paymentId, method);
        } else if (reference.startsWith(SelfRegistrationServiceImpl.REF_PREFIX)) {
            // Self-registration: create the tenant now that payment succeeded.
            selfRegistrationService.completePayment(
                    reference.substring(SelfRegistrationServiceImpl.REF_PREFIX.length()));
        } else {
            // Super-admin onboarding: activate the existing tenant.
            onboardingService.completePayment(reference);
        }
    }

    @PostMapping("/mock-confirm")
    @Operation(summary = "Simulate a successful payment (only when Stripe is NOT configured). Body: { token }")
    public ResponseEntity<ApiResponse<ActivationCompleteResponse>> mockConfirm(
            @jakarta.validation.Valid @RequestBody MockPaymentRequest request) {
        if (stripeService.isConfigured()) {
            throw new BadRequestException("Stripe is configured; use the real checkout + webhook flow");
        }
        ActivationCompleteResponse response = onboardingService.completePayment(request.getToken());
        return ResponseEntity.ok(ApiResponse.success(response, "Payment simulated; account activated"));
    }
}
