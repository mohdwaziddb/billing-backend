package com.billing.service.whatsapp.providers;

import com.billing.dto.notification.NotificationAttachmentRequest;
import com.billing.dto.notification.WhatsAppProviderMetadataResponse;
import com.billing.entity.Company;
import com.billing.entity.enums.WhatsAppProviderType;
import com.billing.service.whatsapp.WhatsAppProvider;
import com.billing.service.whatsapp.WhatsAppSendResult;
import com.billing.service.whatsapp.client.PinnacleClient;
import com.billing.service.whatsapp.config.WhatsAppProviderFieldDefinition;
import com.billing.service.whatsapp.config.WhatsAppResolvedSettings;
import com.billing.service.whatsapp.dto.WhatsAppHealthCheckResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PinnacleWhatsAppProvider implements WhatsAppProvider {

    private static final String DEFAULT_API_URL = "https://api.pinnacle.example.com/v1/whatsapp/messages";

    private final PinnacleClient pinnacleClient;

    @Override
    public WhatsAppProviderType providerType() {
        return WhatsAppProviderType.PINNACLE;
    }

    @Override
    public WhatsAppProviderMetadataResponse metadata() {
        return WhatsAppProviderMetadataResponse.builder()
                .providerType(providerType().name())
                .providerName("Pinnacle")
                .supportsBalance(true)
                .supportsTemplates(true)
                .supportsMedia(true)
                .fields(List.of(
                        field("apiUrl", "API URL", "url", true, false, false, DEFAULT_API_URL, "Pinnacle WhatsApp API endpoint", DEFAULT_API_URL),
                        field("apiKey", "API Key", "password", true, true, true, "", "Pinnacle API key", null),
                        field("clientId", "Client ID", "text", true, true, true, "", "Pinnacle client identifier", null),
                        field("clientSecret", "Client Secret", "password", true, true, true, "", "Pinnacle client secret", null),
                        field("senderId", "Sender ID", "text", false, false, false, "", "Sender identifier for outgoing messages", null),
                        field("businessNumber", "Business Number", "text", true, false, false, "", "Registered WhatsApp business number", null),
                        field("templatePrefix", "Template Prefix", "text", false, false, false, "", "Optional prefix added before template names", null),
                        field("webhookSecret", "Webhook Secret", "password", false, true, true, "", "Webhook verification secret", null),
                        field("status", "Status", "text", false, false, false, "ACTIVE", "Optional provider-side status flag", "ACTIVE"),
                        field("sandboxMode", "Sandbox Mode", "boolean", false, false, false, "", "Enable Pinnacle sandbox mode for testing", "false")
                ).stream().map(WhatsAppProviderFieldDefinition::toResponse).toList())
                .build();
    }

    @Override
    public void validateConfiguration(WhatsAppResolvedSettings settings) {
        settings.required("apiUrl", "Pinnacle API URL is required");
        settings.required("apiKey", "Pinnacle API key is required");
        settings.required("clientId", "Pinnacle client ID is required");
        settings.required("clientSecret", "Pinnacle client secret is required");
        settings.required("businessNumber", "Pinnacle business number is required");
    }

    @Override
    public List<WhatsAppSendResult> sendMessage(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String message) {
        validateConfiguration(settings);
        return mobileNumbers.stream().map(number -> pinnacleClient.sendText(settings, number, message)).toList();
    }

    @Override
    public List<WhatsAppSendResult> sendTemplate(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String templateName, Map<String, Object> variables) {
        validateConfiguration(settings);
        String resolvedTemplateName = settings.optional("templatePrefix") == null ? templateName : settings.optional("templatePrefix") + templateName;
        return mobileNumbers.stream().map(number -> pinnacleClient.sendTemplate(settings, number, resolvedTemplateName, variables)).toList();
    }

    @Override
    public List<WhatsAppSendResult> sendMedia(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments) {
        validateConfiguration(settings);
        String mediaType = attachments.stream().anyMatch(attachment -> attachment.getContentType() != null && attachment.getContentType().toLowerCase().contains("image"))
                ? "image"
                : "document";
        return mobileNumbers.stream().map(number -> pinnacleClient.sendMedia(settings, number, message, mediaType, attachments)).toList();
    }

    @Override
    public List<WhatsAppSendResult> sendDocument(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments) {
        validateConfiguration(settings);
        return mobileNumbers.stream().map(number -> pinnacleClient.sendMedia(settings, number, message, "document", attachments)).toList();
    }

    @Override
    public WhatsAppHealthCheckResult healthCheck(Company company, WhatsAppResolvedSettings settings, String mobileNumber, String message) {
        validateConfiguration(settings);
        WhatsAppSendResult result = pinnacleClient.healthCheck(settings, mobileNumber, message);
        return WhatsAppHealthCheckResult.builder()
                .status(result.status())
                .providerResponse(result.providerResponse())
                .providerName(result.providerName())
                .messageId(result.messageId())
                .failureReason(result.failureReason())
                .sentAt(result.sentAt())
                .build();
    }

    private WhatsAppProviderFieldDefinition field(String key, String label, String type, boolean required, boolean sensitive, boolean encrypted, String placeholder, String helpText, String defaultValue) {
        return WhatsAppProviderFieldDefinition.builder()
                .key(key)
                .label(label)
                .type(type)
                .required(required)
                .sensitive(sensitive)
                .encrypted(encrypted)
                .placeholder(placeholder)
                .helpText(helpText)
                .defaultValue(defaultValue)
                .build();
    }
}
