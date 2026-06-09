package com.billing.dto.email;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EmailLogResponse {
    private Long id;
    private Long templateId;
    private String recipientEmail;
    private String subject;
    private String emailBody;
    private String status;
    private String errorMessage;
    private String sentBy;
    private LocalDateTime sentAt;
}
