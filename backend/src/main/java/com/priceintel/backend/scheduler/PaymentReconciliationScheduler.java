package com.priceintel.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.priceintel.backend.service.OnboardingService;
import com.priceintel.backend.service.SelfRegistrationService;
import com.priceintel.backend.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Safety net for payment completion. Every minute it asks Stripe whether any
 * pending onboarding / self-registration checkout was actually paid, and
 * activates it if so. This means an account still goes live after a real
 * payment even when the Stripe webhook never reaches us and the success page
 * never calls /confirm.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private final OnboardingService onboardingService;
    private final SelfRegistrationService selfRegistrationService;
    private final SubscriptionService subscriptionService;

    @Scheduled(fixedDelayString = "${app.scheduler.payment-reconcile-ms:60000}",
            initialDelayString = "${app.scheduler.payment-reconcile-initial-ms:30000}")
    public void reconcile() {
        try {
            onboardingService.reconcilePendingCheckouts();
        } catch (Exception e) {
            log.error("Onboarding payment reconciliation failed: {}", e.getMessage(), e);
        }
        try {
            selfRegistrationService.reconcilePendingCheckouts();
        } catch (Exception e) {
            log.error("Self-registration payment reconciliation failed: {}", e.getMessage(), e);
        }
        try {
            subscriptionService.reconcilePendingPayments();
        } catch (Exception e) {
            log.error("Plan-change payment reconciliation failed: {}", e.getMessage(), e);
        }
    }
}
