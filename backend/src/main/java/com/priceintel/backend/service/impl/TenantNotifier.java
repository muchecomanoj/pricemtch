package com.priceintel.backend.service.impl;

import org.springframework.stereotype.Service;

import com.priceintel.backend.constants.NotificationType;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.repository.UserRepository;
import com.priceintel.backend.service.MailService;
import com.priceintel.backend.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Delivers one message to everyone on an account, in-app and optionally by
 * email.
 *
 * <p>The bell is the record that something happened and is written first: it
 * must not depend on a mail server being reachable. Email is best-effort on top
 * — a delivery failure is logged and never propagates, because losing a whole
 * evaluation sweep to one bad address would cost far more than the missed
 * mail.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantNotifier {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final MailService mailService;

    /** Bell only. */
    public void notify(Long tenantId, NotificationType type,
            String title, String message, String link) {
        notificationService.notifyTenant(tenantId, type, title, message, link);
    }

    /** Bell, then email to every active user on the account. */
    public void notifyAndEmail(Long tenantId, NotificationType type,
            String title, String message, String link) {
        notify(tenantId, type, title, message, link);
        email(tenantId, title, message);
    }

    /**
     * Emails everyone on the account.
     *
     * <p>Sent per user rather than to a shared address: the platform has no
     * concept of an alerts mailbox, and a price change usually needs whoever is
     * at their desk rather than one nominated person.</p>
     */
    public void email(Long tenantId, String title, String message) {
        if (tenantId == null) {
            return;
        }
        try {
            for (User u : userRepository.findByTenantId(tenantId)) {
                if (u.getEmail() == null || u.isDeleted()) {
                    continue;
                }
                mailService.sendRawHtml(u.getEmail(), title,
                        "<p>" + escape(message) + "</p>"
                        + "<p style=\"color:#666;font-size:13px\">"
                        + "You are receiving this because of a setting on your Price "
                        + "Intelligence account.</p>");
            }
        } catch (Exception e) {
            log.warn("Notification email for tenant {} failed: {}", tenantId, e.getMessage());
        }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
