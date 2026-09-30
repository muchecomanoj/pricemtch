package com.priceintel.backend.service.impl;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.NotificationType;
import com.priceintel.backend.dto.request.ContactMessageSubmission;
import com.priceintel.backend.dto.request.DemoRequestSubmission;
import com.priceintel.backend.dto.request.NewsletterSubscription;
import com.priceintel.backend.entity.ContactMessage;
import com.priceintel.backend.entity.DemoRequest;
import com.priceintel.backend.entity.NewsletterSubscriber;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.ContactMessageRepository;
import com.priceintel.backend.repository.DemoRequestRepository;
import com.priceintel.backend.repository.NewsletterSubscriberRepository;
import com.priceintel.backend.service.MailService;
import com.priceintel.backend.service.NotificationService;
import com.priceintel.backend.service.PublicFormService;
import com.priceintel.backend.utils.SubmissionThrottle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Records what the landing page's forms collect, and makes sure somebody is
 * told about it.
 *
 * <p>Nothing here replies to the visitor or creates an account. A public form
 * that sends mail to an address it was handed is a way to have the platform's
 * mailbox used for spam, so the only outbound message goes to one configured
 * internal address.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicFormServiceImpl implements PublicFormService {

    private final DemoRequestRepository demoRequestRepository;
    private final ContactMessageRepository contactMessageRepository;
    private final NewsletterSubscriberRepository newsletterRepository;
    private final NotificationService notificationService;
    private final MailService mailService;

    @Value("${app.leads.notify-email:}")
    private String leadsNotifyEmail;

    /**
     * Five submissions an hour from one address. High enough that nobody filling
     * in a form honestly will ever meet it, low enough that a script fills five
     * rows instead of fifty thousand.
     */
    private final SubmissionThrottle throttle = new SubmissionThrottle(5, Duration.ofHours(1));

    @Override
    @Transactional
    public void submitDemoRequest(DemoRequestSubmission submission, String callerKey) {
        guard(callerKey);
        DemoRequest saved = demoRequestRepository.save(DemoRequest.builder()
                .name(trim(submission.getName()))
                .email(normaliseEmail(submission.getEmail()))
                .companyName(trim(submission.getCompanyName()))
                .phone(trim(submission.getPhone()))
                .message(trim(submission.getMessage()))
                .handled(false)
                .build());

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Name", saved.getName());
        details.put("Email", saved.getEmail());
        details.put("Company", saved.getCompanyName());
        details.put("Phone", saved.getPhone());
        details.put("Message", saved.getMessage());

        announce("Demo request", saved.getName()
                        + (saved.getCompanyName() == null ? "" : " (" + saved.getCompanyName() + ")")
                        + " asked for a demo.",
                "/platform/submissions/demo-requests", details);
    }

    @Override
    @Transactional
    public void submitContactMessage(ContactMessageSubmission submission, String callerKey) {
        guard(callerKey);
        ContactMessage saved = contactMessageRepository.save(ContactMessage.builder()
                .name(trim(submission.getName()))
                .email(normaliseEmail(submission.getEmail()))
                .phone(trim(submission.getPhone()))
                .subject(trim(submission.getSubject()))
                .message(trim(submission.getMessage()))
                .handled(false)
                .build());

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Name", saved.getName());
        details.put("Email", saved.getEmail());
        details.put("Phone", saved.getPhone());
        details.put("Subject", saved.getSubject());
        details.put("Message", saved.getMessage());

        announce("Contact message",
                saved.getName() + " sent a message"
                        + (saved.getSubject() == null ? "." : ": " + saved.getSubject()),
                "/platform/submissions/contact-messages", details);
    }

    @Override
    @Transactional
    public void subscribe(NewsletterSubscription subscription, String callerKey) {
        guard(callerKey);
        String email = normaliseEmail(subscription.getEmail());

        Optional<NewsletterSubscriber> existing = newsletterRepository.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            // Signing up twice is not a mistake worth reporting, and saying "you
            // are already subscribed" would confirm to a stranger that this
            // address is on the list. Re-subscribing simply reactivates.
            NewsletterSubscriber subscriber = existing.get();
            if (!subscriber.isActive()) {
                subscriber.setActive(true);
                newsletterRepository.save(subscriber);
            }
            return;
        }

        newsletterRepository.save(NewsletterSubscriber.builder()
                .email(email)
                .active(true)
                .unsubscribeToken(newToken())
                .build());
        // No announcement: a newsletter sign-up needs no reply, and one alert per
        // subscriber would train whoever reads them to ignore the lot.
    }

    @Override
    @Transactional
    public boolean unsubscribe(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return newsletterRepository.findByUnsubscribeToken(token)
                .map(subscriber -> {
                    subscriber.setActive(false);
                    newsletterRepository.save(subscriber);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Tells the team a lead arrived — in-app for every super admin, and by email
     * to the one configured address.
     *
     * <p>Best-effort on purpose. The visitor has already been told their message
     * was received, and it has been; failing their submission because our own
     * SMTP is down would lose the lead we are trying not to lose.</p>
     */
    private void announce(String kind, String summary, String link, Map<String, String> details) {
        try {
            notificationService.notifySuperAdmins(NotificationType.LEAD_RECEIVED,
                    kind + " from " + details.get("Name"), summary, link);
        } catch (Exception e) {
            log.error("Could not raise the in-app notification for a {}: {}", kind, e.getMessage());
        }

        if (leadsNotifyEmail == null || leadsNotifyEmail.isBlank()) {
            return;
        }
        try {
            mailService.sendRawHtml(leadsNotifyEmail, kind + " — " + details.get("Name"),
                    emailBody(kind, details));
        } catch (Exception e) {
            log.error("Could not email the {} alert to {}: {}", kind, leadsNotifyEmail, e.getMessage());
        }
    }

    /**
     * Every value is escaped. All of it was typed by a stranger into a form that
     * needs no login, so a name of {@code <script>} must arrive as text in the
     * reader's mailbox rather than as markup.
     */
    private static String emailBody(String kind, Map<String, String> details) {
        StringBuilder html = new StringBuilder("<p><strong>")
                .append(escape(kind))
                .append("</strong> received from the website.</p><table cellpadding=\"6\">");
        for (Map.Entry<String, String> field : details.entrySet()) {
            if (field.getValue() == null) {
                continue;
            }
            html.append("<tr><td valign=\"top\"><strong>").append(escape(field.getKey()))
                    .append("</strong></td><td>").append(escape(field.getValue()))
                    .append("</td></tr>");
        }
        return html.append("</table>").toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void guard(String callerKey) {
        if (!throttle.allow(callerKey)) {
            throw new BadRequestException(
                    "That is a lot of submissions in a short time. Please try again a little later, "
                            + "or email us directly if this is urgent.");
        }
    }

    /** Stored lowercase so the same person cannot appear on the list twice. */
    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
