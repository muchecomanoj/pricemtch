package com.priceintel.backend.dto.response;

import java.time.LocalDateTime;

import com.priceintel.backend.entity.NewsletterSubscriber;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A newsletter subscriber as the Super Admin sees it. The unsubscribe token is
 * deliberately not included — it authorises removal, so it belongs only in the
 * email sent to that address.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterSubscriberResponse {

    private Long id;
    private String email;
    private boolean active;
    private LocalDateTime subscribedAt;

    public static NewsletterSubscriberResponse from(NewsletterSubscriber s) {
        return NewsletterSubscriberResponse.builder()
                .id(s.getId())
                .email(s.getEmail())
                .active(s.isActive())
                .subscribedAt(s.getCreatedAt())
                .build();
    }
}
