package com.priceintel.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.NotificationChannelsRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.NotificationChannelsResponse;
import com.priceintel.backend.service.impl.NotificationChannelService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Where a client's alerts get delivered, beyond the in-app bell.
 */
@RestController
@RequestMapping("/api/v1/notification-channels")
@RequiredArgsConstructor
@Tag(name = "Notification Channels", description = "Slack and Microsoft Teams alert delivery")
public class NotificationChannelController {

    private static final String ADMIN_ONLY = "hasAnyRole('ADMIN', 'SUPER_ADMIN')";

    private final NotificationChannelService service;

    @GetMapping
    @Operation(summary = "Current delivery channels",
            description = "Webhook URLs are returned masked — enough to recognise which is "
                    + "configured, never enough to post with.")
    public ResponseEntity<ApiResponse<NotificationChannelsResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success(service.get(), "Notification channels"));
    }

    @PutMapping
    @PreAuthorize(ADMIN_ONLY)
    @Operation(summary = "Update delivery channels",
            description = "Omit a webhook URL to leave the stored one unchanged; send an empty "
                    + "string to clear it. Enabling a channel without a URL is rejected.")
    public ResponseEntity<ApiResponse<NotificationChannelsResponse>> update(
            @RequestBody NotificationChannelsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                service.update(request), "Notification channels updated"));
    }

    @PostMapping("/test")
    @PreAuthorize(ADMIN_ONLY)
    @Operation(summary = "Send a test message to one channel",
            description = "Proves the webhook works while the user is still on the settings "
                    + "screen, rather than discovering a typo when an alert goes missing.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> test(
            @RequestBody Map<String, String> body) {
        boolean delivered = service.sendTest(body.get("channel"));
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("delivered", delivered),
                delivered ? "Test message sent" : "Delivery failed — check the webhook URL"));
    }
}
