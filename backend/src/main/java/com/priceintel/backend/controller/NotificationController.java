package com.priceintel.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.NotificationResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * The logged-in user's in-app notifications (the bell icon). Works for any
 * authenticated principal — super admin or tenant user.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications for the current user")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @Operation(summary = "My notifications (newest first)")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(service.myNotifications(page, size), "Notifications"));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "My unread notification count (for the bell badge)")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("unread", service.myUnreadCount()), "Unread count"));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one notification read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id) {
        service.markRead(id);
        return ResponseEntity.ok(ApiResponse.success("Marked read"));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all my notifications read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("updated", service.markAllRead()), "All marked read"));
    }
}
