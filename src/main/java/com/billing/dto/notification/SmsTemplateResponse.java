package com.billing.dto.notification;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SmsTemplateResponse {
    private Long id;
    private String templateName;
    private String templateBody;
    private boolean active;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
