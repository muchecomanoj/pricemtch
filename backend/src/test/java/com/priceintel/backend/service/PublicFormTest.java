package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.priceintel.backend.service.impl.PublicFormServiceImpl;

/**
 * The landing page's three forms.
 *
 * <p>These endpoints take no token, so the tests worth having are about what
 * happens when they are abused rather than when they are used correctly.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicFormTest {

    @Mock DemoRequestRepository demoRequestRepository;
    @Mock ContactMessageRepository contactMessageRepository;
    @Mock NewsletterSubscriberRepository newsletterRepository;
    @Mock NotificationService notificationService;
    @Mock MailService mailService;

    @InjectMocks PublicFormServiceImpl service;

    @BeforeEach
    void savesReturnWhatTheyWereGiven() {
        when(demoRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(contactMessageRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        ReflectionTestUtils.setField(service, "leadsNotifyEmail", "leads@priceintel.com");
    }

    private static DemoRequestSubmission demo() {
        return DemoRequestSubmission.builder()
                .name("Prativa").email("prativa@example.com").companyName("Suyog").build();
    }

    @Nested
    @DisplayName("recording what was submitted")
    class Recording {

        @Test
        @DisplayName("a demo request is stored, unhandled, with the address lowercased")
        void demoRequestIsStored() {
            service.submitDemoRequest(DemoRequestSubmission.builder()
                    .name("  Prativa  ").email("  Prativa@Example.COM ").companyName("Suyog")
                    .message("Interested in the Pro plan").build(), "10.0.0.1");

            ArgumentCaptor<DemoRequest> saved = ArgumentCaptor.forClass(DemoRequest.class);
            verify(demoRequestRepository).save(saved.capture());

            assertThat(saved.getValue().getName()).isEqualTo("Prativa");
            // Lowercased so the same person cannot appear twice under two spellings.
            assertThat(saved.getValue().getEmail()).isEqualTo("prativa@example.com");
            assertThat(saved.getValue().isHandled()).isFalse();
        }

        @Test
        @DisplayName("blank optional fields are stored as null, not as empty strings")
        void blanksBecomeNull() {
            service.submitContactMessage(ContactMessageSubmission.builder()
                    .name("Tapas").email("tapas@example.com").phone("   ").subject("")
                    .message("How do monitors work?").build(), "10.0.0.2");

            ArgumentCaptor<ContactMessage> saved = ArgumentCaptor.forClass(ContactMessage.class);
            verify(contactMessageRepository).save(saved.capture());

            assertThat(saved.getValue().getPhone()).isNull();
            assertThat(saved.getValue().getSubject()).isNull();
        }
    }

    @Nested
    @DisplayName("the newsletter list")
    class Newsletter {

        @Test
        @DisplayName("subscribing twice adds one row, and says nothing about the first")
        void subscribingTwiceIsNotAnError() {
            NewsletterSubscriber existing = NewsletterSubscriber.builder()
                    .email("tapas@example.com").active(true).unsubscribeToken("tok").build();
            when(newsletterRepository.findByEmailIgnoreCase("tapas@example.com"))
                    .thenReturn(Optional.of(existing));

            service.subscribe(NewsletterSubscription.builder().email("Tapas@Example.com").build(), "10.0.0.3");

            // Already on the list and already active: nothing to write.
            verify(newsletterRepository, never()).save(any());
        }

        @Test
        @DisplayName("subscribing again after leaving puts the address back on the list")
        void resubscribeReactivates() {
            NewsletterSubscriber gone = NewsletterSubscriber.builder()
                    .email("tapas@example.com").active(false).unsubscribeToken("tok").build();
            when(newsletterRepository.findByEmailIgnoreCase("tapas@example.com"))
                    .thenReturn(Optional.of(gone));

            service.subscribe(NewsletterSubscription.builder().email("tapas@example.com").build(), "10.0.0.4");

            verify(newsletterRepository).save(gone);
            assertThat(gone.isActive()).isTrue();
            // The original token survives, so the old email footers still work.
            assertThat(gone.getUnsubscribeToken()).isEqualTo("tok");
        }

        @Test
        @DisplayName("a new subscriber gets an unsubscribe token")
        void newSubscriberGetsToken() {
            when(newsletterRepository.findByEmailIgnoreCase("new@example.com")).thenReturn(Optional.empty());

            service.subscribe(NewsletterSubscription.builder().email("new@example.com").build(), "10.0.0.5");

            ArgumentCaptor<NewsletterSubscriber> saved = ArgumentCaptor.forClass(NewsletterSubscriber.class);
            verify(newsletterRepository).save(saved.capture());
            assertThat(saved.getValue().getUnsubscribeToken()).hasSize(32);
            assertThat(saved.getValue().isActive()).isTrue();
        }

        @Test
        @DisplayName("unsubscribing deactivates rather than deletes, so nobody is silently re-added")
        void unsubscribeDeactivates() {
            NewsletterSubscriber subscriber = NewsletterSubscriber.builder()
                    .email("tapas@example.com").active(true).unsubscribeToken("tok123").build();
            when(newsletterRepository.findByUnsubscribeToken("tok123")).thenReturn(Optional.of(subscriber));

            assertThat(service.unsubscribe("tok123")).isTrue();
            assertThat(subscriber.isActive()).isFalse();
            verify(newsletterRepository, never()).delete(any());
        }

        @Test
        @DisplayName("an unknown or missing token changes nothing")
        void unknownTokenDoesNothing() {
            when(newsletterRepository.findByUnsubscribeToken("nope")).thenReturn(Optional.empty());

            assertThat(service.unsubscribe("nope")).isFalse();
            assertThat(service.unsubscribe(null)).isFalse();
            assertThat(service.unsubscribe("  ")).isFalse();
            verify(newsletterRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("telling the team a lead arrived")
    class Announcing {

        @Test
        @DisplayName("a demo request raises an in-app notification and one email")
        void demoRequestIsAnnounced() {
            service.submitDemoRequest(demo(), "10.0.0.6");

            verify(notificationService).notifySuperAdmins(
                    org.mockito.ArgumentMatchers.eq(NotificationType.LEAD_RECEIVED),
                    org.mockito.ArgumentMatchers.contains("Prativa"),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString());

            ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
            verify(mailService).sendRawHtml(to.capture(),
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
            // Always the configured address, never the one the visitor typed —
            // otherwise a stranger chooses where our mailbox sends.
            assertThat(to.getValue()).isEqualTo("leads@priceintel.com");
        }

        @Test
        @DisplayName("what the visitor typed is escaped before it reaches anyone's inbox")
        void submittedTextIsEscaped() {
            service.submitContactMessage(ContactMessageSubmission.builder()
                    .name("<script>alert(1)</script>")
                    .email("x@example.com")
                    .message("1 < 2 & \"quoted\"")
                    .build(), "10.0.0.7");

            ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
            verify(mailService).sendRawHtml(org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(), html.capture());

            assertThat(html.getValue()).doesNotContain("<script>");
            assertThat(html.getValue()).contains("&lt;script&gt;");
            assertThat(html.getValue()).contains("1 &lt; 2 &amp; &quot;quoted&quot;");
        }

        @Test
        @DisplayName("a newsletter sign-up is not announced — it needs no reply")
        void newsletterIsNotAnnounced() {
            when(newsletterRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

            service.subscribe(NewsletterSubscription.builder().email("quiet@example.com").build(), "10.0.0.8");

            verify(notificationService, never()).notifySuperAdmins(any(), any(), any(), any());
            verify(mailService, never()).sendRawHtml(any(), any(), any());
        }

        @Test
        @DisplayName("a mail failure does not lose the lead that was already saved")
        void mailFailureDoesNotFailTheSubmission() {
            org.mockito.Mockito.doThrow(new RuntimeException("SMTP is down"))
                    .when(mailService).sendRawHtml(any(), any(), any());

            // The visitor is told their request was received, so it had better be.
            service.submitDemoRequest(demo(), "10.0.0.9");

            verify(demoRequestRepository).save(any());
        }

        @Test
        @DisplayName("with no address configured the email is skipped, not attempted")
        void noConfiguredAddressMeansNoEmail() {
            ReflectionTestUtils.setField(service, "leadsNotifyEmail", "");

            service.submitDemoRequest(demo(), "10.0.0.10");

            verify(mailService, never()).sendRawHtml(any(), any(), any());
            // The in-app notification still happens: that is the one that cannot fail.
            verify(notificationService).notifySuperAdmins(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("abuse")
    class Abuse {

        @Test
        @DisplayName("one address is cut off after five submissions in the hour")
        void throttleStopsFlooding() {
            for (int i = 0; i < 5; i++) {
                service.submitDemoRequest(demo(), "203.0.113.9");
            }

            assertThatThrownBy(() -> service.submitDemoRequest(demo(), "203.0.113.9"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("try again");

            verify(demoRequestRepository, org.mockito.Mockito.times(5)).save(any());
        }

        @Test
        @DisplayName("the limit is per address — one flooder does not lock out everyone else")
        void throttleIsPerCaller() {
            for (int i = 0; i < 5; i++) {
                service.submitDemoRequest(demo(), "203.0.113.9");
            }

            // A different visitor, arriving during the flood, is still served.
            service.submitDemoRequest(demo(), "198.51.100.4");
            verify(demoRequestRepository, org.mockito.Mockito.times(6)).save(any());
        }

        @Test
        @DisplayName("the limit is shared across the forms, so it cannot be sidestepped")
        void throttleSpansAllThreeForms() {
            service.submitDemoRequest(demo(), "203.0.113.10");
            service.submitDemoRequest(demo(), "203.0.113.10");
            when(newsletterRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
            service.subscribe(NewsletterSubscription.builder().email("a@example.com").build(), "203.0.113.10");
            service.submitContactMessage(ContactMessageSubmission.builder()
                    .name("X").email("x@example.com").message("hi").build(), "203.0.113.10");
            service.submitContactMessage(ContactMessageSubmission.builder()
                    .name("X").email("x@example.com").message("hi").build(), "203.0.113.10");

            assertThatThrownBy(() -> service.submitDemoRequest(demo(), "203.0.113.10"))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("an unknown caller address is served rather than refused")
        void unknownAddressIsAllowed() {
            for (int i = 0; i < 20; i++) {
                service.submitDemoRequest(demo(), null);
            }
            // Refusing everything we cannot attribute would break the form for
            // everybody the moment the proxy stopped forwarding the header.
            verify(demoRequestRepository, org.mockito.Mockito.times(20)).save(any());
        }
    }
}
