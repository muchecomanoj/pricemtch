package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Save a tenant's SMTP settings (Settings → Email). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailConfigRequest {

    private boolean enabled;

    @Size(max = 200)
    private String host;

    @Min(1) @Max(65535)
    private Integer port;

    @Size(max = 200)
    private String username;

    /** Plain password/app-password. Leave null/blank on update to keep the existing one. */
    @Size(max = 500)
    private String password;

    @Email
    @Size(max = 200)
    private String fromAddress;

    @Size(max = 200)
    private String fromName;

    private Boolean startTls;
    private Boolean sslEnabled;
}
