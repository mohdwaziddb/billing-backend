package com.billing.service.sms;

import com.billing.dto.notification.NotificationStatus;

import java.time.LocalDateTime;

public record SmsSendResult(String mobileNumber, NotificationStatus status, String providerResponse, LocalDateTime sentAt) {
}
