package com.billing.service.whatsapp.client;

import com.billing.dto.notification.NotificationAttachmentRequest;
import com.billing.dto.notification.NotificationStatus;
import com.billing.service.whatsapp.WhatsAppSendResult;
import com.billing.service.whatsapp.config.WhatsAppResolvedSettings;
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
public class PinnacleClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public WhatsAppSendResult sendText(WhatsAppResolvedSettings settings, String mobileNumber, String message) {
        return dispatch(settings, mobileNumber, "text", message, null, List.of(), Map.of());
    }

    public WhatsAppSendResult sendTemplate(WhatsAppResolvedSettings settings, String mobileNumber, String templateName, Map<String, Object> variables) {
        return dispatch(settings, mobileNumber, "template", templateName, null, List.of(), variables);
    }

    public WhatsAppSendResult sendMedia(WhatsAppResolvedSettings settings, String mobileNumber, String message, String mediaType, List<NotificationAttachmentRequest> attachments) {
        return dispatch(settings, mobileNumber, mediaType, message, mediaType, attachments, Map.of());
    }

    public WhatsAppSendResult healthCheck(WhatsAppResolvedSettings settings, String mobileNumber, String message) {
        return sendText(settings, mobileNumber, message);
    }

    private WhatsAppSendResult dispatch(WhatsAppResolvedSettings settings,
                                        String mobileNumber,
                                        String messageType,
                                        String message,
                                        String contentMode,
                                        List<NotificationAttachmentRequest> attachments,
                                        Map<String, Object> variables) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(settings.required("apiUrl", "Pinnacle API URL is required")))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("X-API-KEY", settings.required("apiKey", "Pinnacle API key is required"))
                    .header("X-CLIENT-ID", settings.required("clientId", "Pinnacle client ID is required"))
                    .header("X-CLIENT-SECRET", settings.required("clientSecret", "Pinnacle client secret is required"))
                    .POST(HttpRequest.BodyPublishers.ofString(buildPayload(settings, mobileNumber, messageType, message, contentMode, attachments, variables)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            NotificationStatus status = response.statusCode() >= 200 && response.statusCode() < 300 ? NotificationStatus.SENT : NotificationStatus.FAILED;
            String messageId = extractMessageId(responseBody);
            String failureReason = status == NotificationStatus.FAILED ? shorten(responseBody) : null;
            return new WhatsAppSendResult(mobileNumber, status, "Pinnacle", messageId, failureReason, responseBody, LocalDateTime.now());
        } catch (IOException ex) {
            return failure(mobileNumber, ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failure(mobileNumber, ex.getMessage());
        } catch (RuntimeException ex) {
            return failure(mobileNumber, ex.getMessage());
        }
    }

    private String buildPayload(WhatsAppResolvedSettings settings,
                                String mobileNumber,
                                String messageType,
                                String message,
                                String contentMode,
                                List<NotificationAttachmentRequest> attachments,
                                Map<String, Object> variables) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("senderId", settings.optional("senderId"));
        payload.put("businessNumber", settings.required("businessNumber", "Pinnacle business number is required"));
        payload.put("mobileNumber", withCountryCode(mobileNumber));
        payload.put("type", messageType);
        payload.put("message", message);
        payload.put("templatePrefix", settings.optional("templatePrefix"));
        payload.put("sandboxMode", settings.flag("sandboxMode"));
        payload.put("status", settings.optional("status"));
        if (variables != null && !variables.isEmpty()) {
            payload.put("variables", variables);
        }
        if (attachments != null && !attachments.isEmpty()) {
            NotificationAttachmentRequest attachment = attachments.get(0);
            payload.put("media", Map.of(
                    "mode", contentMode == null ? "media" : contentMode,
                    "fileName", attachment.getFileName(),
                    "contentType", attachment.getContentType(),
                    "base64Content", attachment.getBase64Content()
            ));
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize Pinnacle WhatsApp payload", ex);
        }
    }

    private String extractMessageId(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> map = objectMapper.readValue(responseBody, Map.class);
            Object id = map.get("messageId");
            if (id == null) {
                id = map.get("message_id");
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
        return new WhatsAppSendResult(mobileNumber, NotificationStatus.FAILED, "Pinnacle", null, reason, reason, LocalDateTime.now());
    }

    private String withCountryCode(String mobileNumber) {
        return mobileNumber != null && mobileNumber.startsWith("91") ? mobileNumber : "91" + mobileNumber;
    }

    private String shorten(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
