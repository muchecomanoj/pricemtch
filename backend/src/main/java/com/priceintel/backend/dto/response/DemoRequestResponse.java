package com.priceintel.backend.dto.response;

import java.time.LocalDateTime;

import com.priceintel.backend.entity.DemoRequest;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A demo request as the Super Admin sees it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoRequestResponse {

    private Long id;
    private String name;
    private String email;
    private String companyName;
    private String phone;
    private String message;
    private boolean handled;
    private LocalDateTime handledAt;
    private String handledBy;
    private LocalDateTime submittedAt;

    public static DemoRequestResponse from(DemoRequest r) {
        return DemoRequestResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .email(r.getEmail())
                .companyName(r.getCompanyName())
                .phone(r.getPhone())
                .message(r.getMessage())
                .handled(r.isHandled())
                .handledAt(r.getHandledAt())
                .handledBy(r.getHandledBy())
                .submittedAt(r.getCreatedAt())
                .build();
    }
}
