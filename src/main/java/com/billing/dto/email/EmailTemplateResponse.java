package com.billing.dto.email;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EmailTemplateResponse {
    private Long id;
    private String templateName;
    private String subject;
    private String emailBody;
    private boolean active;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
