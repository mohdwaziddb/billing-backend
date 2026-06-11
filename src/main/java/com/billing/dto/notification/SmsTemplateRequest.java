package com.billing.dto.notification;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SmsTemplateRequest {
    @NotBlank
    private String templateName;

    @NotBlank
    private String templateBody;

    private Boolean active;
}
