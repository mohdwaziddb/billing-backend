package com.billing.service.whatsapp;

import com.billing.dto.notification.NotificationStatus;

import java.time.LocalDateTime;

public record WhatsAppSendResult(String mobileNumber,
                                 NotificationStatus status,
                                 String providerResponse,
                                 LocalDateTime sentAt) {
}
