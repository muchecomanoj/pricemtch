package com.priceintel.backend.config;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.priceintel.backend.constants.UserStatus;
import com.priceintel.backend.entity.SubscriptionPlan;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds the single SUPER_ADMIN (platform owner) and the default subscription
 * plans at startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SubscriptionPlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String SUPER_ADMIN_EMAIL = "akash@yopmail.com";
    private static final String SUPER_ADMIN_PASSWORD = "Admin@12345";

    @Override
    public void run(String... args) {
        seedPlans();
        seedSuperAdmin();
    }

    private void seedPlans() {
        createPlanIfMissing("FREE", "Free Trial", new BigDecimal("0.00"), 14, 3, 1440,
                "Product search,Basic reports");
        createPlanIfMissing("BASIC", "Basic", new BigDecimal("49.00"), 30, 10, 720,
                "Product management,Competitor pricing,Reports");
        createPlanIfMissing("PRO", "Professional", new BigDecimal("149.00"), 30, 50, 360,
                "Everything in Basic,AI matching,Marketplace integration");
        createPlanIfMissing("ENTERPRISE", "Enterprise", new BigDecimal("499.00"), 30, 500, 60,
                "Everything in Pro,Priority support,Custom limits");
    }

    private void createPlanIfMissing(String code, String name, BigDecimal price, int cycle,
                                     int maxUsers, int minIntervalMinutes, String features) {
        if (!planRepository.existsByCodeIgnoreCase(code)) {
            planRepository.save(SubscriptionPlan.builder()
                    .code(code).name(name).price(price).currency("USD")
                    .billingCycleDays(cycle).maxUsers(maxUsers).minIntervalMinutes(minIntervalMinutes)
                    .features(features).active(true)
                    .build());
            log.info("Seeded plan: {}", code);
        }
    }

    private void seedSuperAdmin() {
        if (userRepository.existsByEmail(SUPER_ADMIN_EMAIL)) {
            return;
        }
        User admin = User.builder()
                .superAdmin(true)
                .tenantId(null)
                .accessType(null)
                .firstName("Platform")
                .lastName("Owner")
                .email(SUPER_ADMIN_EMAIL)
                .password(passwordEncoder.encode(SUPER_ADMIN_PASSWORD))
                .status(UserStatus.ACTIVE)
                .build();
        userRepository.save(admin);

        log.info("========================================================");
        log.info(" SUPER_ADMIN created:");
        log.info("   email    : {}", SUPER_ADMIN_EMAIL);
        log.info("   password : {}", SUPER_ADMIN_PASSWORD);
        log.info("   >>> CHANGE THIS PASSWORD AFTER FIRST LOGIN <<<");
        log.info("========================================================");
    }
}
