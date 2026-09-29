package com.priceintel.backend.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.NotificationType;
import com.priceintel.backend.dto.response.NotificationResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.entity.Notification;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.NotificationRepository;
import com.priceintel.backend.repository.UserRepository;
import com.priceintel.backend.utils.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates and serves in-app notifications. The {@code notify*} methods are
 * best-effort — a failure to create a notification never breaks the business
 * operation that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;

    // ---------- creation (called by business flows) ----------

    /** Notify every user of a tenant (the client's team). */
    @Transactional
    public void notifyTenant(Long tenantId, NotificationType type, String title, String message, String link) {
        if (tenantId == null) {
            return;
        }
        try {
            List<User> users = userRepository.findByTenantId(tenantId);
            for (User u : users) {
                save(u.getId(), tenantId, type, title, message, link);
            }
        } catch (Exception e) {
            log.error("notifyTenant {} failed: {}", tenantId, e.getMessage());
        }
    }

    /** Notify every platform owner (super admin). */
    @Transactional
    public void notifySuperAdmins(NotificationType type, String title, String message, String link) {
        try {
            for (User u : userRepository.findBySuperAdminTrue()) {
                save(u.getId(), null, type, title, message, link);
            }
        } catch (Exception e) {
            log.error("notifySuperAdmins failed: {}", e.getMessage());
        }
    }

    /** Notify a single user. */
    @Transactional
    public void notifyUser(Long userId, Long tenantId, NotificationType type,
                           String title, String message, String link) {
        try {
            save(userId, tenantId, type, title, message, link);
        } catch (Exception e) {
            log.error("notifyUser {} failed: {}", userId, e.getMessage());
        }
    }

    private void save(Long userId, Long tenantId, NotificationType type,
                      String title, String message, String link) {
        repository.save(Notification.builder()
                .recipientUserId(userId).tenantId(tenantId)
                .type(type).title(title).message(message).link(link)
                .read(false).build());
    }

    // ---------- serving the current user ----------

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> myNotifications(int page, int size) {
        Long userId = currentUserId();
        Page<NotificationResponse> result = repository
                .findByRecipientUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, Math.min(size, 100)))
                .map(this::toResponse);
        return PagedResponse.from(result);
    }

    @Transactional(readOnly = true)
    public long myUnreadCount() {
        return repository.countByRecipientUserIdAndReadFalse(currentUserId());
    }

    @Transactional
    public void markRead(Long id) {
        Long userId = currentUserId();
        Notification n = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!n.getRecipientUserId().equals(userId)) {
            throw new BadRequestException("Not your notification");
        }
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(java.time.Instant.now());
            repository.save(n);
        }
    }

    @Transactional
    public int markAllRead() {
        return repository.markAllRead(currentUserId());
    }

    // ---------- helpers ----------

    private Long currentUserId() {
        String email = SecurityUtils.getCurrentUsername()
                .orElseThrow(() -> new BadRequestException("No authenticated user"));
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found")).getId();
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId()).type(n.getType().name()).title(n.getTitle())
                .message(n.getMessage()).link(n.getLink()).read(n.isRead())
                .createdAt(n.getCreatedAt()).build();
    }
}
