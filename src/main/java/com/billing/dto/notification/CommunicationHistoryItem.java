package com.billing.dto.notification;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Unified history row for Communication History (Mail/SMS/WhatsApp pages).
 * Mail merges email_logs (template mails) with notification_logs EMAIL
 * (free-form sends); SMS/WhatsApp read notification_logs by channel.
 * `source` tells which table the row came from.
 */
@Getter
@Builder
public class CommunicationHistoryItem {
    private Long id;
    private String channel;
    private String source;
    private String recipient;
    private String subject;
    private String message;
    private String status;
    private String errorMessage;
    private String providerName;
    private String messageId;
    private String sentBy;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
