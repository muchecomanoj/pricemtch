package com.priceintel.backend.service;

import java.util.List;
import java.util.Map;

import com.priceintel.backend.constants.EmailTemplateKey;
import com.priceintel.backend.dto.request.EmailTemplateUpdateRequest;
import com.priceintel.backend.dto.response.EmailTemplateResponse;

/**
 * Manages the super-admin-editable email templates and renders them for sending.
 */
public interface EmailTemplateService {

    /** A rendered template ready to send. */
    record Rendered(String subject, String htmlBody) {
    }

    List<EmailTemplateResponse> listTemplates();

    EmailTemplateResponse getTemplate(EmailTemplateKey key);

    EmailTemplateResponse updateTemplate(EmailTemplateKey key, EmailTemplateUpdateRequest request);

    /** Renders the stored (or default) template with the given variables. */
    Rendered render(EmailTemplateKey key, Map<String, ?> vars);

    /** Sample values for each variable, used by preview and send-test. */
    Map<String, Object> sampleVars(EmailTemplateKey key);
}
