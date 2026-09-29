package com.priceintel.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.constants.EmailTemplateKey;
import com.priceintel.backend.dto.request.EmailTemplateUpdateRequest;
import com.priceintel.backend.dto.request.SendTestEmailRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.EmailTemplateResponse;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.service.EmailTemplateService;
import com.priceintel.backend.service.MailService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * SUPER_ADMIN management of the client-facing email templates
 * (Settings → Email Templates).
 */
@RestController
@RequestMapping("/api/v1/admin/email-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "SUPER_ADMIN · Email Templates", description = "View, edit, preview and test the emails sent to clients")
public class EmailTemplateController {

    private final EmailTemplateService templateService;
    private final MailService mailService;

    @GetMapping
    @Operation(summary = "List all email templates (with current subject/body and supported variables)")
    public ResponseEntity<ApiResponse<List<EmailTemplateResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(templateService.listTemplates(), "Email templates"));
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get one email template")
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> get(@PathVariable String key) {
        return ResponseEntity.ok(ApiResponse.success(
                templateService.getTemplate(parse(key)), "Email template"));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Save edits to an email template")
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> update(
            @PathVariable String key, @Valid @RequestBody EmailTemplateUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                templateService.updateTemplate(parse(key), request), "Template saved"));
    }

    @GetMapping("/{key}/preview")
    @Operation(summary = "Preview the rendered subject + HTML body using sample data")
    public ResponseEntity<ApiResponse<EmailTemplateService.Rendered>> preview(@PathVariable String key) {
        EmailTemplateKey k = parse(key);
        return ResponseEntity.ok(ApiResponse.success(
                templateService.render(k, templateService.sampleVars(k)), "Preview"));
    }

    @PostMapping("/{key}/test")
    @Operation(summary = "Send a test render of the template to an address")
    public ResponseEntity<ApiResponse<Void>> sendTest(
            @PathVariable String key, @Valid @RequestBody SendTestEmailRequest request) {
        EmailTemplateKey k = parse(key);
        EmailTemplateService.Rendered r = templateService.render(k, templateService.sampleVars(k));
        mailService.sendRawHtml(request.getToEmail(), "[TEST] " + r.subject(), r.htmlBody());
        return ResponseEntity.ok(ApiResponse.success("Test email sent to " + request.getToEmail()));
    }

    private EmailTemplateKey parse(String key) {
        try {
            return EmailTemplateKey.valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown email template: " + key);
        }
    }
}
