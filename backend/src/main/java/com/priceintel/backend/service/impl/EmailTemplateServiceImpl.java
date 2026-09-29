package com.priceintel.backend.service.impl;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.EmailTemplateKey;
import com.priceintel.backend.dto.request.EmailTemplateUpdateRequest;
import com.priceintel.backend.dto.response.EmailTemplateResponse;
import com.priceintel.backend.entity.EmailTemplate;
import com.priceintel.backend.repository.EmailTemplateRepository;
import com.priceintel.backend.service.EmailTemplateService;
import com.priceintel.backend.utils.TemplateRenderer;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailTemplateServiceImpl implements EmailTemplateService {

    private final EmailTemplateRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<EmailTemplateResponse> listTemplates() {
        return Arrays.stream(EmailTemplateKey.values()).map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmailTemplateResponse getTemplate(EmailTemplateKey key) {
        return toResponse(key);
    }

    @Override
    @Transactional
    public EmailTemplateResponse updateTemplate(EmailTemplateKey key, EmailTemplateUpdateRequest r) {
        EmailTemplate t = repository.findByTemplateKey(key)
                .orElseGet(() -> EmailTemplate.builder().templateKey(key).build());
        t.setSubject(r.getSubject());
        t.setBody(r.getBody());
        t.setEdited(true);
        repository.save(t);
        return toResponse(key);
    }

    @Override
    @Transactional(readOnly = true)
    public Rendered render(EmailTemplateKey key, Map<String, ?> vars) {
        EmailTemplate t = repository.findByTemplateKey(key).orElse(null);
        String subject = t != null ? t.getSubject() : key.defaultSubject();
        String body = t != null ? t.getBody() : key.defaultBody();
        return new Rendered(
                TemplateRenderer.render(subject, vars),
                TemplateRenderer.toHtml(TemplateRenderer.render(body, vars)));
    }

    @Override
    public Map<String, Object> sampleVars(EmailTemplateKey key) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("appName", "Price Intelligence");
        s.put("contactPerson", "Jane Doe");
        s.put("companyName", "Acme Corp");
        s.put("companyCode", "ACME-1234");
        s.put("activationLink", "https://app.example.com/activate?token=SAMPLE");
        s.put("verificationCode", "482913");
        s.put("code", "482913");
        s.put("expiryHours", 48);
        s.put("expiryMinutes", 15);
        s.put("planName", "Professional");
        s.put("billingCycle", "YEARLY");
        s.put("status", "TRIAL");
        s.put("trialLine", "Free trial until 2026-08-10");
        s.put("loginUrl", "https://app.example.com/login");
        s.put("amount", "USD 1430.40");
        s.put("payLink", "https://checkout.stripe.com/c/pay/SAMPLE");
        // Expiry reminder and expired notice.
        s.put("paidThrough", "29 Sep 2026");
        s.put("endsIn", "in 7 days");
        s.put("daysLeft", 7);
        s.put("graceDays", 3);
        s.put("accessEndsOn", "2 Oct 2026");
        s.put("renewLink", "https://app.example.com/my-subscription");
        return s;
    }

    // ---------- helpers ----------

    private EmailTemplateResponse toResponse(EmailTemplateKey key) {
        EmailTemplate t = repository.findByTemplateKey(key).orElse(null);
        return EmailTemplateResponse.builder()
                .key(key.name())
                .name(key.displayName())
                .description(key.description())
                .subject(t != null ? t.getSubject() : key.defaultSubject())
                .body(t != null ? t.getBody() : key.defaultBody())
                .edited(t != null && t.isEdited())
                .variables(List.of(key.variables().split(",")))
                .build();
    }
}
