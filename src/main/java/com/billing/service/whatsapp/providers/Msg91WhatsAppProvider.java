package com.billing.service.whatsapp.providers;

import com.billing.dto.notification.NotificationAttachmentRequest;
import com.billing.dto.notification.NotificationStatus;
import com.billing.dto.notification.WhatsAppProviderMetadataResponse;
import com.billing.entity.Company;
import com.billing.entity.enums.WhatsAppProviderType;
import com.billing.service.whatsapp.WhatsAppProvider;
import com.billing.service.whatsapp.WhatsAppSendResult;
import com.billing.service.whatsapp.config.WhatsAppProviderFieldDefinition;
import com.billing.service.whatsapp.config.WhatsAppResolvedSettings;
import com.billing.service.whatsapp.dto.WhatsAppHealthCheckResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

@Component
@RequiredArgsConstructor
public class Msg91WhatsAppProvider implements WhatsAppProvider {

    private static final String DEFAULT_API_URL = "https://control.msg91.com/api/v5/whatsapp/whatsapp-outbound-message";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Override
    public WhatsAppProviderType providerType() {
        return WhatsAppProviderType.MSG91;
    }

    @Override
    public WhatsAppProviderMetadataResponse metadata() {
        return WhatsAppProviderMetadataResponse.builder()
                .providerType(providerType().name())
                .providerName("MSG91")
                .supportsBalance(false)
                .supportsTemplates(true)
                .supportsMedia(true)
                .fields(List.of(
                        field("apiUrl", "API URL", "url", true, false, false, DEFAULT_API_URL, "MSG91 WhatsApp outbound API URL", DEFAULT_API_URL),
                        field("authKey", "Auth Key", "password", true, true, true, "", "MSG91 WhatsApp auth key", null),
                        field("whatsappNumber", "WhatsApp Number", "text", true, false, false, "", "Integrated WhatsApp number", null),
                        field("senderName", "Sender Name", "text", false, false, false, "", "Optional display sender name", null)
                ).stream().map(WhatsAppProviderFieldDefinition::toResponse).toList())
                .build();
    }

    @Override
    public void validateConfiguration(WhatsAppResolvedSettings settings) {
        settings.required("apiUrl", "MSG91 API URL is required");
        settings.required("authKey", "MSG91 auth key is required");
        settings.required("whatsappNumber", "MSG91 WhatsApp number is required");
    }

    @Override
    public List<WhatsAppSendResult> sendMessage(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String message) {
        return mobileNumbers.stream().map(number -> dispatch(settings, number, message, "text", List.of(), Map.of())).toList();
    }

    @Override
    public List<WhatsAppSendResult> sendTemplate(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String templateName, Map<String, Object> variables) {
        return mobileNumbers.stream().map(number -> dispatch(settings, number, templateName, "template", List.of(), variables)).toList();
    }

    @Override
    public List<WhatsAppSendResult> sendMedia(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments) {
        return mobileNumbers.stream().map(number -> dispatch(settings, number, message, "media", attachments, Map.of())).toList();
    }

    @Override
    public List<WhatsAppSendResult> sendDocument(Company company, WhatsAppResolvedSettings settings, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments) {
        return mobileNumbers.stream().map(number -> dispatch(settings, number, message, "document", attachments, Map.of())).toList();
    }

    @Override
    public WhatsAppHealthCheckResult healthCheck(Company company, WhatsAppResolvedSettings settings, String mobileNumber, String message) {
        WhatsAppSendResult result = dispatch(settings, mobileNumber, message, "text", List.of(), Map.of());
        return WhatsAppHealthCheckResult.builder()
                .status(result.status())
                .providerResponse(result.providerResponse())
                .providerName(result.providerName())
                .messageId(result.messageId())
                .failureReason(result.failureReason())
                .sentAt(result.sentAt())
                .build();
    }

    private WhatsAppSendResult dispatch(WhatsAppResolvedSettings settings,
                                        String mobileNumber,
                                        String message,
                                        String contentMode,
                                        List<NotificationAttachmentRequest> attachments,
                                        Map<String, Object> variables) {
        try {
            validateConfiguration(settings);
            HttpRequest request = HttpRequest.newBuilder(URI.create(resolveApiUrl(settings)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("authkey", settings.required("authKey", "MSG91 auth key is required"))
                    .POST(HttpRequest.BodyPublishers.ofString(buildPayload(settings, mobileNumber, message, contentMode, attachments, variables)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            NotificationStatus status = response.statusCode() >= 200 && response.statusCode() < 300 ? NotificationStatus.SENT : NotificationStatus.FAILED;
            String body = response.body();
            return new WhatsAppSendResult(
                    mobileNumber,
                    status,
                    "MSG91",
                    extractMessageId(body),
                    status == NotificationStatus.FAILED ? shorten(body) : null,
                    body,
                    LocalDateTime.now()
            );
        } catch (IOException ex) {
            return failure(mobileNumber, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failure(mobileNumber, ex.getMessage());
        } catch (RuntimeException ex) {
            return failure(mobileNumber, ex.getMessage());
        }
    }

    private String resolveApiUrl(WhatsAppResolvedSettings settings) {
        String apiUrl = settings.optional("apiUrl");
        return apiUrl == null ? DEFAULT_API_URL : apiUrl;
    }

    private String buildPayload(WhatsAppResolvedSettings settings,
                                String mobileNumber,
                                String message,
                                String contentMode,
                                List<NotificationAttachmentRequest> attachments,
                                Map<String, Object> variables) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("integrated_number", settings.required("whatsappNumber", "MSG91 WhatsApp number is required"));
        payload.put("content_type", contentMode);
        payload.put("recipient", Map.of("number", "91" + mobileNumber));
        payload.put("message", message);
        payload.put("sender_name", settings.optional("senderName"));
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

    private String extractMessageId(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> map = objectMapper.readValue(body, Map.class);
            Object id = map.get("messageId");
            if (id == null) {
                id = map.get("request_id");
            }
            if (id == null) {
                id = map.get("id");
            }
            return id == null ? null : String.valueOf(id);
        } catch (Exception ignored) {
            return null;
        }
    }

    private WhatsAppSendResult failure(String mobileNumber, String reason) {
        return new WhatsAppSendResult(mobileNumber, NotificationStatus.FAILED, "MSG91", null, reason, reason, LocalDateTime.now());
    }

    private String shorten(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
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
