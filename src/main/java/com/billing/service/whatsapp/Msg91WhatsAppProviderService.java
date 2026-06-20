package com.billing.service.whatsapp;

import com.billing.dto.notification.NotificationAttachmentRequest;
import com.billing.dto.notification.NotificationStatus;
import com.billing.entity.Company;
import com.billing.entity.WhatsAppProviderSetting;
import com.billing.service.SecretEncryptionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class Msg91WhatsAppProviderService implements WhatsAppProviderService {

    private static final String DEFAULT_MSG91_WHATSAPP_API_URL = "https://control.msg91.com/api/v5/whatsapp/whatsapp-outbound-message";

    private final SecretEncryptionService secretEncryptionService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Override
    public String providerType() {
        return "MSG91";
    }

    @Override
    public List<WhatsAppSendResult> sendMessage(Company company, WhatsAppProviderSetting settings, List<String> mobileNumbers, String message) {
        return mobileNumbers.stream().map(number -> dispatch(settings, number, message, null, List.of(), Map.of())).toList();
    }

    @Override
    public List<WhatsAppSendResult> sendTemplate(Company company, WhatsAppProviderSetting settings, List<String> mobileNumbers, String templateName, Map<String, Object> variables) {
        return mobileNumbers.stream().map(number -> dispatch(settings, number, templateName, "template", List.of(), variables)).toList();
    }

    @Override
    public List<WhatsAppSendResult> sendMedia(Company company, WhatsAppProviderSetting settings, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments) {
        return mobileNumbers.stream().map(number -> dispatch(settings, number, message, "media", attachments, Map.of())).toList();
    }

    @Override
    public List<WhatsAppSendResult> sendDocument(Company company, WhatsAppProviderSetting settings, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments) {
        return mobileNumbers.stream().map(number -> dispatch(settings, number, message, "document", attachments, Map.of())).toList();
    }

    @Override
    public WhatsAppSendResult testConnection(Company company, WhatsAppProviderSetting settings, String mobileNumber, String message) {
        return dispatch(settings, mobileNumber, message, null, List.of(), Map.of());
    }

    private WhatsAppSendResult dispatch(WhatsAppProviderSetting settings,
                                        String mobileNumber,
                                        String message,
                                        String contentMode,
                                        List<NotificationAttachmentRequest> attachments,
                                        Map<String, Object> variables) {
        try {
            validateSettings(settings);
            HttpRequest request = HttpRequest.newBuilder(URI.create(resolveApiUrl(settings)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("authkey", secretEncryptionService.decrypt(settings.getAuthKey()))
                    .POST(HttpRequest.BodyPublishers.ofString(buildPayload(settings, mobileNumber, message, contentMode, attachments, variables)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            NotificationStatus status = response.statusCode() >= 200 && response.statusCode() < 300
                    ? NotificationStatus.SENT
                    : NotificationStatus.FAILED;
            return new WhatsAppSendResult(mobileNumber, status, response.body(), LocalDateTime.now());
        } catch (IOException ex) {
            return new WhatsAppSendResult(mobileNumber, NotificationStatus.FAILED, ex.getMessage(), LocalDateTime.now());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new WhatsAppSendResult(mobileNumber, NotificationStatus.FAILED, ex.getMessage(), LocalDateTime.now());
        } catch (RuntimeException ex) {
            return new WhatsAppSendResult(mobileNumber, NotificationStatus.FAILED, ex.getMessage(), LocalDateTime.now());
        }
    }

    private void validateSettings(WhatsAppProviderSetting settings) {
        if (!hasText(settings.getWhatsappNumber()) || !hasText(settings.getAuthKey())) {
            throw new IllegalStateException("MSG91 WhatsApp provider is missing WhatsApp number or auth key");
        }
        if (!hasText(secretEncryptionService.decrypt(settings.getAuthKey()))) {
            throw new IllegalStateException("MSG91 WhatsApp auth key is unavailable");
        }
    }

    private String resolveApiUrl(WhatsAppProviderSetting settings) {
        return hasText(settings.getApiUrl()) ? settings.getApiUrl().trim() : DEFAULT_MSG91_WHATSAPP_API_URL;
    }

    private String buildPayload(WhatsAppProviderSetting settings,
                                String mobileNumber,
                                String message,
                                String contentMode,
                                List<NotificationAttachmentRequest> attachments,
                                Map<String, Object> variables) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("integrated_number", settings.getWhatsappNumber());
        payload.put("content_type", hasText(contentMode) ? contentMode : "text");
        payload.put("recipient", Map.of(
                "number", "91" + mobileNumber
        ));
        payload.put("message", message);
        payload.put("sender_name", settings.getSenderName());
        if (variables != null && !variables.isEmpty()) {
            payload.put("variables", variables);
        }
        if (attachments != null && !attachments.isEmpty()) {
            NotificationAttachmentRequest attachment = attachments.get(0);
            payload.put("attachment", Map.of(
                    "file_name", attachment.getFileName(),
                    "content_type", attachment.getContentType(),
                    "base64_content", attachment.getBase64Content()
            ));
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize WhatsApp payload", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
