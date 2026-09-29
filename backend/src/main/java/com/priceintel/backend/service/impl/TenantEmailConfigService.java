package com.priceintel.backend.service.impl;

import java.util.Optional;
import java.util.Properties;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.request.EmailConfigRequest;
import com.priceintel.backend.dto.response.EmailConfigResponse;
import com.priceintel.backend.entity.TenantEmailConfig;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.TenantEmailConfigRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.utils.EncryptionService;

import jakarta.mail.Transport;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-tenant SMTP configuration (Settings → Email). Stores the password
 * AES-encrypted and, when enabled, builds a {@link JavaMailSenderImpl} the mail
 * service uses so a tenant's emails go out from their own server/address.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantEmailConfigService {

    private final TenantEmailConfigRepository repo;
    private final EncryptionService crypto;

    /**
     * Sentinel tenant id for the platform-default SMTP (set by the Super Admin,
     * who has no tenant of their own). Used as the fallback when a tenant has no
     * config of its own.
     */
    private static final Long PLATFORM_TENANT_ID = 0L;

    /** A ready-to-use sender resolved for a tenant. */
    @Value
    public static class ResolvedMailSender {
        JavaMailSenderImpl sender;
        String fromAddress;
        String fromName;
    }

    // ---------- CRUD (current tenant) ----------

    @Transactional(readOnly = true)
    public EmailConfigResponse get() {
        Long tenantId = requireTenant();
        return repo.findByTenantId(tenantId).map(this::toResponse)
                .orElseGet(() -> EmailConfigResponse.builder()
                        .tenantId(tenantId).port(587).startTls(true).status("NOT_CONFIGURED")
                        .configured(false).build());
    }

    @Transactional
    public EmailConfigResponse save(EmailConfigRequest r) {
        Long tenantId = requireTenant();
        TenantEmailConfig c = repo.findByTenantId(tenantId)
                .orElseGet(() -> TenantEmailConfig.builder().tenantId(tenantId).build());
        c.setEnabled(r.isEnabled());
        c.setHost(trim(r.getHost()));
        if (r.getPort() != null) c.setPort(r.getPort());
        c.setUsername(trim(r.getUsername()));
        // Only replace the password when a new non-blank one is supplied.
        if (r.getPassword() != null && !r.getPassword().isBlank()) {
            c.setEncryptedPassword(crypto.encrypt(r.getPassword()));
        }
        c.setFromAddress(trim(r.getFromAddress()));
        c.setFromName(trim(r.getFromName()));
        if (r.getStartTls() != null) c.setStartTls(r.getStartTls());
        if (r.getSslEnabled() != null) c.setSslEnabled(r.getSslEnabled());
        c.setStatus(c.getHost() != null && c.getUsername() != null ? "CONFIGURED" : "NOT_CONFIGURED");
        return toResponse(repo.save(c));
    }

    /** Test the current tenant's SMTP by opening a connection. */
    @Transactional
    public EmailConfigResponse test() {
        Long tenantId = requireTenant();
        TenantEmailConfig c = repo.findByTenantId(tenantId)
                .orElseThrow(() -> new BadRequestException("Save the SMTP settings before testing."));
        try {
            JavaMailSenderImpl sender = build(c);
            Transport transport = sender.getSession().getTransport("smtp");
            transport.connect(sender.getHost(), sender.getPort(), sender.getUsername(), sender.getPassword());
            transport.close();
            c.setStatus("CONNECTED");
            c.setLastMessage("Connection successful");
        } catch (Exception e) {
            c.setStatus("ERROR");
            c.setLastMessage(e.getMessage());
            log.warn("SMTP test failed for tenant {}: {}", tenantId, e.getMessage());
        }
        c.setLastCheckedAt(java.time.Instant.now());
        return toResponse(repo.save(c));
    }

    // ---------- used by the mail service ----------

    /**
     * Resolve a sender for sending: the tenant's own enabled SMTP first, then the
     * platform-default SMTP (Super-Admin-set), then empty (caller falls back to
     * the application.properties default).
     */
    @Transactional(readOnly = true)
    public Optional<ResolvedMailSender> resolve(Long tenantId) {
        Optional<ResolvedMailSender> own = (tenantId == null)
                ? Optional.empty()
                : enabledSender(tenantId);
        if (own.isPresent()) {
            return own;
        }
        // Fall back to the platform-default row (unless we already looked it up).
        return PLATFORM_TENANT_ID.equals(tenantId) ? Optional.empty() : enabledSender(PLATFORM_TENANT_ID);
    }

    private Optional<ResolvedMailSender> enabledSender(Long tenantId) {
        return repo.findByTenantId(tenantId)
                .filter(c -> c.isEnabled() && c.getHost() != null && !c.getHost().isBlank())
                .map(c -> new ResolvedMailSender(build(c),
                        c.getFromAddress() != null ? c.getFromAddress() : c.getUsername(),
                        c.getFromName()));
    }

    // ---------- helpers ----------

    private JavaMailSenderImpl build(TenantEmailConfig c) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(c.getHost());
        sender.setPort(c.getPort());
        sender.setUsername(c.getUsername());
        sender.setPassword(c.getEncryptedPassword() != null ? crypto.decrypt(c.getEncryptedPassword()) : null);
        Properties p = sender.getJavaMailProperties();
        p.put("mail.transport.protocol", "smtp");
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.starttls.enable", String.valueOf(c.isStartTls()));
        if (c.isSslEnabled()) {
            p.put("mail.smtp.ssl.enable", "true");
        }
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "10000");
        p.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    /**
     * The row this caller manages: their own tenant, or the platform-default row
     * when a Super Admin (who has no tenant) is configuring the fallback sender.
     */
    private Long requireTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        if (TenantContext.isSuperAdmin()) {
            return PLATFORM_TENANT_ID;
        }
        throw new BadRequestException("No tenant in context; SMTP settings are per-tenant.");
    }

    private String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private EmailConfigResponse toResponse(TenantEmailConfig c) {
        return EmailConfigResponse.builder()
                .tenantId(c.getTenantId()).enabled(c.isEnabled())
                .host(c.getHost()).port(c.getPort()).username(c.getUsername())
                .passwordSet(c.getEncryptedPassword() != null && !c.getEncryptedPassword().isBlank())
                .fromAddress(c.getFromAddress()).fromName(c.getFromName())
                .startTls(c.isStartTls()).sslEnabled(c.isSslEnabled())
                .status(c.getStatus()).lastCheckedAt(c.getLastCheckedAt()).lastMessage(c.getLastMessage())
                .configured(true)
                .build();
    }
}
