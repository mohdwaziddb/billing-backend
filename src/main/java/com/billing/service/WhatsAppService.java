package com.billing.service;

import com.billing.dto.notification.NotificationStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class WhatsAppService {
    public WhatsAppDeliveryResult sendWhatsApp() {
        return new WhatsAppDeliveryResult(NotificationStatus.PENDING, "WhatsApp provider is reserved for future integration", LocalDateTime.now());
    }

    public record WhatsAppDeliveryResult(NotificationStatus status, String providerResponse, LocalDateTime sentAt) {
    }
}
