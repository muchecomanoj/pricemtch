package com.priceintel.backend.dto.response;

import java.time.LocalDateTime;

import com.priceintel.backend.entity.ContactMessage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A contact-form message as the Super Admin sees it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactMessageResponse {

    private Long id;
    private String name;
    private String email;
    private String phone;
    private String subject;
    private String message;
    private boolean handled;
    private LocalDateTime handledAt;
    private String handledBy;
    private LocalDateTime submittedAt;

    public static ContactMessageResponse from(ContactMessage m) {
        return ContactMessageResponse.builder()
                .id(m.getId())
                .name(m.getName())
                .email(m.getEmail())
                .phone(m.getPhone())
                .subject(m.getSubject())
                .message(m.getMessage())
                .handled(m.isHandled())
                .handledAt(m.getHandledAt())
                .handledBy(m.getHandledBy())
                .submittedAt(m.getCreatedAt())
                .build();
    }
}
