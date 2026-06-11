package com.billing.dto.notification;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationLogResponse {
    private Long id;
    private String channel;
    private Long templateId;
    private String recipient;
    private String subject;
    private String message;
    private String providerResponse;
    private String status;
    private String sentBy;
    private LocalDateTime sentAt;
}
