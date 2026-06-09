package com.billing.dto.email;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmailTemplateRequest {

    @NotBlank
    private String templateName;

    @NotBlank
    private String subject;

    @NotBlank
    private String emailBody;

    private Boolean active;
}
