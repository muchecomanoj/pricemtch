package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.priceintel.backend.constants.EmailTemplateKey;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.service.EmailTemplateService;
import com.priceintel.backend.service.MailService;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * SMTP implementation. Subject + body come from the editable email templates
 * (super admin can override them in Settings → Email Templates); when a template
 * is unedited the built-in default is used. Controlled by {@code mail.enabled} —
 * when off the rendered message is logged instead of sent.
 */
@Slf4j
@Service
public class MailServiceImpl implements MailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final EmailTemplateService templates;
    private final TenantEmailConfigService tenantSmtp;
    private final boolean enabled;
    private final String from;
    private final String supportEmail;
    private final String appName;

    public MailServiceImpl(ObjectProvider<JavaMailSender> mailSenderProvider,
                           EmailTemplateService templates,
                           TenantEmailConfigService tenantSmtp,
                           @Value("${mail.enabled:false}") boolean enabled,
                           @Value("${mail.from:no-reply@priceintel.com}") String from,
                           @Value("${app.support-email:support@priceintel.com}") String supportEmail,
                           @Value("${app.name:Price Intelligence}") String appName) {
        this.mailSenderProvider = mailSenderProvider;
        this.templates = templates;
        this.tenantSmtp = tenantSmtp;
        this.enabled = enabled;
        this.from = from;
        this.supportEmail = supportEmail;
        this.appName = appName;
    }

    @Override
    @Async
    public void sendActivationEmail(Tenant tenant, String toEmail, String recipientName,
                                    String activationUrl, String verificationCode, int expiryHours) {
        Map<String, Object> v = base(recipientName);
        v.put("companyName", tenant.getCompanyName());
        v.put("companyCode", tenant.getCompanyCode());
        v.put("activationLink", activationUrl);
        v.put("verificationCode", verificationCode);
        v.put("expiryHours", expiryHours);
        sendTemplate(EmailTemplateKey.CLIENT_ACTIVATION, toEmail, v, tenant.getId());
    }

    @Override
    @Async
    public void sendVerificationCodeEmail(String toEmail, String recipientName,
                                          String companyName, String verificationCode, int expiryMinutes) {
        Map<String, Object> v = base(recipientName);
        v.put("companyName", companyName);
        v.put("verificationCode", verificationCode);
        v.put("expiryMinutes", expiryMinutes);
        sendTemplate(EmailTemplateKey.VERIFICATION_CODE, toEmail, v);
    }

    @Override
    @Async
    public void sendPasswordResetCodeEmail(String toEmail, String recipientName,
                                           String resetCode, int expiryMinutes) {
        Map<String, Object> v = base(recipientName);
        v.put("code", resetCode);
        v.put("expiryMinutes", expiryMinutes);
        sendTemplate(EmailTemplateKey.PASSWORD_RESET, toEmail, v);
    }

    @Override
    @Async
    public void sendWelcomeEmail(Tenant tenant, String toEmail, String recipientName, String loginUrl) {
        Map<String, Object> v = base(recipientName);
        v.put("companyName", tenant.getCompanyName());
        v.put("companyCode", tenant.getCompanyCode());
        v.put("planName", String.valueOf(tenant.getSubscriptionPlan()));
        v.put("billingCycle", tenant.getBillingCycle() != null ? tenant.getBillingCycle().name() : "");
        v.put("status", String.valueOf(tenant.getSubscriptionStatus()));
        v.put("trialLine", tenant.getTrialEndDate() != null
                ? "Free trial until " + tenant.getTrialEndDate() : "");
        v.put("loginUrl", loginUrl);
        sendTemplate(EmailTemplateKey.WELCOME, toEmail, v, tenant.getId());
    }

    @Override
    @Async
    public void sendPlanChangePayLinkEmail(String toEmail, String recipientName, String planName,
                                           String billingCycle, BigDecimal amount, String currency,
                                           String checkoutUrl, boolean isUpgrade) {
        Map<String, Object> v = base(recipientName);
        v.put("planName", planName);
        v.put("billingCycle", billingCycle);
        v.put("amount", currency + " " + amount);
        v.put("payLink", checkoutUrl);
        sendTemplate(EmailTemplateKey.PLAN_CHANGE_PAYLINK, toEmail, v);
    }

    @Override
    @Async
    public void sendSubscriptionNotice(EmailTemplateKey key, String toEmail, String recipientName,
                                       Map<String, Object> vars) {
        Map<String, Object> v = base(recipientName);
        v.putAll(vars);
        sendTemplate(key, toEmail, v);   // no tenant id: the platform mailbox, not the client's SMTP
    }

    @Override
    public void sendRawHtml(String toEmail, String subject, String htmlBody) {
        send(toEmail, subject, htmlBody);
    }

    // ---------- internals ----------

    private Map<String, Object> base(String recipientName) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("appName", appName);
        v.put("supportEmail", supportEmail);
        v.put("contactPerson", recipientName != null ? recipientName : "there");
        return v;
    }

    private void sendTemplate(EmailTemplateKey key, String toEmail, Map<String, ?> vars) {
        sendTemplate(key, toEmail, vars, null);
    }

    private void sendTemplate(EmailTemplateKey key, String toEmail, Map<String, ?> vars, Long tenantId) {
        EmailTemplateService.Rendered r = templates.render(key, vars);
        send(toEmail, r.subject(), r.htmlBody(), tenantId);
    }

    private void send(String to, String subject, String html) {
        send(to, subject, html, null);
    }

    /**
     * Sends via the tenant's own SMTP when configured+enabled; otherwise via the
     * platform default (when {@code mail.enabled}); otherwise logs the message.
     */
    private void send(String to, String subject, String html, Long tenantId) {
        JavaMailSender sender;
        String fromAddress;
        String fromName = null;

        var resolved = tenantSmtp.resolve(tenantId);
        if (resolved.isPresent()) {
            sender = resolved.get().getSender();
            fromAddress = resolved.get().getFromAddress();
            fromName = resolved.get().getFromName();
        } else {
            sender = mailSenderProvider.getIfAvailable();
            fromAddress = from;
            if (!enabled || sender == null) {
                log.info("[MAIL DISABLED] would send to {} — subject: {}", to, subject);
                return;
            }
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(fromAddress, fromName);
            } else {
                helper.setFrom(fromAddress);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            sender.send(message);
            log.info("Email sent to {} via {}: {}", to,
                    resolved.isPresent() ? "tenant SMTP" : "platform SMTP", subject);
        } catch (Exception e) {
            // Never fail the business operation because email delivery failed.
            log.error("Failed to send email to {} ({}). Subject: {}", to, e.getMessage(), subject);
        }
    }
}
