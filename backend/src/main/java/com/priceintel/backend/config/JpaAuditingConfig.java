package com.priceintel.backend.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.priceintel.backend.constants.AppConstants;
import com.priceintel.backend.utils.SecurityUtils;

/**
 * Enables JPA auditing and tells it "who" is acting, so createdBy / updatedBy
 * on {@code BaseEntity} are populated with the current user's email (or SYSTEM
 * for background/seed operations).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(SecurityUtils.getCurrentUsername().orElse(AppConstants.SYSTEM_USER));
    }
}
