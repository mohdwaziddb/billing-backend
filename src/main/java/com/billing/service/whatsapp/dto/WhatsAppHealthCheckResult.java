package com.billing.service.whatsapp.dto;

import com.billing.dto.notification.NotificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WhatsAppHealthCheckResult {
    private NotificationStatus status;
    private String providerResponse;
    private String providerName;
    private String messageId;
    private String failureReason;
    private LocalDateTime sentAt;
}
