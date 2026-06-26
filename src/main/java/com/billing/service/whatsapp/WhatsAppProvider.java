package com.billing.service.whatsapp;

import com.billing.dto.notification.NotificationAttachmentRequest;
import com.billing.dto.notification.WhatsAppProviderMetadataResponse;
import com.billing.entity.Company;
import com.billing.entity.enums.WhatsAppProviderType;
import com.billing.service.whatsapp.config.WhatsAppResolvedSettings;
import com.billing.service.whatsapp.dto.WhatsAppBalanceResult;
import com.billing.service.whatsapp.dto.WhatsAppHealthCheckResult;

import java.util.List;
import java.util.Map;

public interface WhatsAppProvider {

    WhatsAppProviderType providerType();

    WhatsAppProviderMetadataResponse metadata();

    void validateConfiguration(WhatsAppResolvedSettings settings);

    List<WhatsAppSendResult> sendMessage(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String message);

    List<WhatsAppSendResult> sendTemplate(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String templateName, Map<String, Object> variables);

    List<WhatsAppSendResult> sendMedia(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments);

    default List<WhatsAppSendResult> sendDocument(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments) {
        return sendMedia(company, settings, mobileNumbers, message, attachments);
    }

    default WhatsAppBalanceResult checkBalance(Company company, WhatsAppResolvedSettings settings) {
        return WhatsAppBalanceResult.builder()
                .supported(false)
                .rawResponse("Balance check is not supported by this provider.")
                .build();
    }

    WhatsAppHealthCheckResult healthCheck(Company company, WhatsAppResolvedSettings settings, String mobileNumber, String message);
}
