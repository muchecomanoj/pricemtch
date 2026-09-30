package com.priceintel.backend.service;

import com.priceintel.backend.dto.request.ContactMessageSubmission;
import com.priceintel.backend.dto.request.DemoRequestSubmission;
import com.priceintel.backend.dto.request.NewsletterSubscription;

/**
 * The three forms on the public landing page. Everything here is reachable
 * without a token, so every method takes the caller's address for throttling
 * and none of them reveal whether an address is already known.
 */
public interface PublicFormService {

    /** "Book a demo". */
    void submitDemoRequest(DemoRequestSubmission submission, String callerKey);

    /** "Contact us". */
    void submitContactMessage(ContactMessageSubmission submission, String callerKey);

    /** Footer newsletter sign-up. Subscribing twice is not an error. */
    void subscribe(NewsletterSubscription subscription, String callerKey);

    /**
     * Leaves the newsletter.
     *
     * @return true if the token matched; false lets the caller show the same
     *         page either way rather than confirming which tokens exist
     */
    boolean unsubscribe(String token);
}
