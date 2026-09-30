package com.priceintel.backend.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.ContactMessageResponse;
import com.priceintel.backend.dto.response.DemoRequestResponse;
import com.priceintel.backend.dto.response.NewsletterSubscriberResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.entity.ContactMessage;
import com.priceintel.backend.entity.DemoRequest;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.ContactMessageRepository;
import com.priceintel.backend.repository.DemoRequestRepository;
import com.priceintel.backend.repository.NewsletterSubscriberRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * What the landing page's forms collected.
 *
 * <p>Reads straight from the repositories rather than through a service: there
 * is no rule to apply beyond "show them newest first", and a service layer that
 * only forwards calls hides where the data comes from without adding
 * anything.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/submissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "SUPER_ADMIN · Submissions", description = "Demo requests, contact messages and newsletter subscribers")
public class SubmissionController {

    private final DemoRequestRepository demoRequestRepository;
    private final ContactMessageRepository contactMessageRepository;
    private final NewsletterSubscriberRepository newsletterRepository;

    @GetMapping("/summary")
    @Operation(summary = "Counts for the badges on the submissions screen")
    public ResponseEntity<ApiResponse<Map<String, Long>>> summary() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "demoRequestsPending", demoRequestRepository.countByHandledFalse(),
                "contactMessagesPending", contactMessageRepository.countByHandledFalse(),
                "newsletterSubscribers", newsletterRepository.countByActiveTrue()), "Submission summary"));
    }

    // ── Demo requests ──────────────────────────────────────────────────────

    @GetMapping("/demo-requests")
    @Operation(summary = "List demo requests, newest first")
    public ResponseEntity<ApiResponse<PagedResponse<DemoRequestResponse>>> demoRequests(
            @RequestParam(required = false) Boolean handled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = newestFirst(page, size);
        Page<DemoRequest> found = handled == null
                ? demoRequestRepository.findAll(pageable)
                : demoRequestRepository.findByHandled(handled, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.from(found.map(DemoRequestResponse::from)), "Demo requests"));
    }

    @PutMapping("/demo-requests/{id}/handled")
    @Operation(summary = "Mark a demo request handled, or put it back")
    public ResponseEntity<ApiResponse<DemoRequestResponse>> markDemoRequest(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean handled) {

        DemoRequest demoRequest = demoRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No demo request with id " + id + "."));
        demoRequest.setHandled(handled);
        demoRequest.setHandledAt(handled ? LocalDateTime.now() : null);
        demoRequest.setHandledBy(handled ? currentUser() : null);

        return ResponseEntity.ok(ApiResponse.success(
                DemoRequestResponse.from(demoRequestRepository.save(demoRequest)),
                handled ? "Marked as handled" : "Moved back to pending"));
    }

    // ── Contact messages ───────────────────────────────────────────────────

    @GetMapping("/contact-messages")
    @Operation(summary = "List contact messages, newest first")
    public ResponseEntity<ApiResponse<PagedResponse<ContactMessageResponse>>> contactMessages(
            @RequestParam(required = false) Boolean handled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = newestFirst(page, size);
        Page<ContactMessage> found = handled == null
                ? contactMessageRepository.findAll(pageable)
                : contactMessageRepository.findByHandled(handled, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.from(found.map(ContactMessageResponse::from)), "Contact messages"));
    }

    @PutMapping("/contact-messages/{id}/handled")
    @Operation(summary = "Mark a contact message handled, or put it back")
    public ResponseEntity<ApiResponse<ContactMessageResponse>> markContactMessage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean handled) {

        ContactMessage message = contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No contact message with id " + id + "."));
        message.setHandled(handled);
        message.setHandledAt(handled ? LocalDateTime.now() : null);
        message.setHandledBy(handled ? currentUser() : null);

        return ResponseEntity.ok(ApiResponse.success(
                ContactMessageResponse.from(contactMessageRepository.save(message)),
                handled ? "Marked as handled" : "Moved back to pending"));
    }

    // ── Newsletter ─────────────────────────────────────────────────────────

    @GetMapping("/newsletter-subscribers")
    @Operation(summary = "List newsletter subscribers, newest first")
    public ResponseEntity<ApiResponse<PagedResponse<NewsletterSubscriberResponse>>> subscribers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                PagedResponse.from(newsletterRepository.findAll(newestFirst(page, size))
                        .map(NewsletterSubscriberResponse::from)), "Newsletter subscribers"));
    }

    private static Pageable newestFirst(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static String currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }
}
