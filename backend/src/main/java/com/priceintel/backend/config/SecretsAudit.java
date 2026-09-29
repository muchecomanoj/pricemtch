package com.priceintel.backend.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Checks at startup that the application is not running on placeholder secrets.
 *
 * <p>A default that silently works is the dangerous kind: the app starts, the
 * encryption appears to function, and nobody discovers the key was published in
 * the source tree until it matters. This makes that state loud, and refuses it
 * outright where {@code app.security.strict-secrets} is on — which any real
 * deployment should set.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecretsAudit {

    /** The value shipped in application.properties, and therefore public. */
    static final String DEFAULT_CRYPTO_SECRET = "change-me-dev-secret-please-override";
    static final String DEFAULT_JWT_SECRET =
            "change-me-in-production-price-intelligence-super-secret-key-0123456789";

    private final Environment environment;

    @Value("${app.crypto.secret:}")
    private String cryptoSecret;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${mail.enabled:false}")
    private boolean mailEnabled;

    /**
     * Fail startup rather than warn. Off by default so local development is not
     * blocked, on for anything resembling a deployment.
     */
    @Value("${app.security.strict-secrets:false}")
    private boolean strict;

    @EventListener(ApplicationReadyEvent.class)
    public void audit() {
        List<String> problems = new ArrayList<>();

        if (DEFAULT_CRYPTO_SECRET.equals(cryptoSecret)) {
            problems.add("CRYPTO_SECRET is the published default — stored marketplace "
                    + "credentials and webhook URLs are readable by anyone with the source.");
        }
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            problems.add("JWT_SECRET is the published default — anyone with the source can "
                    + "mint a valid token for any user.");
        }
        if (mailEnabled && (mailPassword == null || mailPassword.isBlank())) {
            problems.add("mail.enabled is true but MAIL_PASSWORD is unset — outgoing email "
                    + "will fail at send time rather than now.");
        }

        if (problems.isEmpty()) {
            log.info("Secrets audit passed: no placeholder secrets in use.");
            return;
        }

        String profiles = String.join(",", environment.getActiveProfiles());
        if (strict) {
            problems.forEach(p -> log.error("SECRETS: {}", p));
            throw new IllegalStateException(
                    "Refusing to start with placeholder secrets (app.security.strict-secrets=true). "
                            + "Set the values listed above. Active profiles: "
                            + (profiles.isEmpty() ? "<none>" : profiles));
        }

        log.warn("========================================================");
        log.warn(" INSECURE CONFIGURATION — fine for local work, not for a");
        log.warn(" shared or deployed environment:");
        problems.forEach(p -> log.warn("   • {}", p));
        log.warn(" Set app.security.strict-secrets=true to make this fatal.");
        log.warn("========================================================");
    }
}
