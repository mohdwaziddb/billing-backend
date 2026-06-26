package com.billing.service.whatsapp;

import com.billing.dto.notification.NotificationStatus;

import java.time.LocalDateTime;

public record WhatsAppSendResult(String mobileNumber,
                                 NotificationStatus status,
                                 String providerName,
                                 String messageId,
                                 String failureReason,
                                 String providerResponse,
                                 LocalDateTime sentAt) {
}
