package com.priceintel.backend.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One email template for the Settings → Email Templates editor. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplateResponse {
    private String key;          // enum name, e.g. "CLIENT_ACTIVATION"
    private String name;         // display name
    private String description;
    private String subject;
    private String body;
    private boolean edited;
    private List<String> variables;   // supported {{vars}} (without braces)
}
