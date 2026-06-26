package com.billing.service.sms;

import com.billing.dto.notification.NotificationStatus;
import com.billing.entity.Company;
import com.billing.entity.SmsProviderSetting;
import com.billing.service.sms.config.SmsProviderConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NsTechSmsProviderService implements SmsProviderService {

    private final SmsProviderConfigurationService configurationService;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Override
    public String providerType() {
        return "NSTECH";
    }

    @Override
    public List<SmsSendResult> sendSms(Company company, SmsProviderSetting settings, List<String> mobileNumbers, String message) {
        return mobileNumbers.stream().map(number -> dispatch(settings, number, message)).toList();
    }

    @Override
    public SmsSendResult sendOtp(Company company, SmsProviderSetting settings, String mobileNumber, String message) {
        return dispatch(settings, mobileNumber, message);
    }

    @Override
    public SmsSendResult testConnection(Company company, SmsProviderSetting settings, String mobileNumber, String message) {
        return dispatch(settings, mobileNumber, message);
    }

    private SmsSendResult dispatch(SmsProviderSetting settings, String mobileNumber, String message) {
        Map<String, String> config = configurationService.decrypted(settings);
        validate(config);
        int retryCount = safeInt(config.get("retryCount"), 0);
        int timeoutSeconds = Math.max(1, safeInt(config.get("timeout"), 15));
        RuntimeException runtimeFailure = null;
        for (int attempt = 0; attempt <= retryCount; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(buildProviderUrl(config, mobileNumber, message)))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                NotificationStatus status = response.statusCode() >= 200 && response.statusCode() < 300
                        ? NotificationStatus.SENT
                        : NotificationStatus.FAILED;
                return new SmsSendResult(mobileNumber, status, response.body(), LocalDateTime.now());
            } catch (IOException ex) {
                runtimeFailure = new IllegalStateException(ex.getMessage(), ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return new SmsSendResult(mobileNumber, NotificationStatus.FAILED, ex.getMessage(), LocalDateTime.now());
            } catch (RuntimeException ex) {
                runtimeFailure = ex;
            }
        }
        return new SmsSendResult(mobileNumber, NotificationStatus.FAILED, runtimeFailure == null ? "NSTech SMS request failed" : runtimeFailure.getMessage(), LocalDateTime.now());
    }

    private void validate(Map<String, String> config) {
        require(config, "apiUrl", "NSTech API URL is required");
        require(config, "username", "NSTech username is required");
        require(config, "password", "NSTech password is required");
        require(config, "senderId", "NSTech sender ID is required");
        require(config, "channel", "NSTech channel is required");
    }

    private String buildProviderUrl(Map<String, String> config, String mobileNumber, String message) {
        String baseUrl = config.get("apiUrl").trim();
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator
                + "user=" + encode(config.get("username"))
                + "&password=" + encode(config.get("password"))
                + "&senderid=" + encode(config.get("senderId"))
                + "&channel=" + encode(config.get("channel"))
                + "&DCS=" + encode(defaultIfBlank(config.get("dcs"), "0"))
                + "&flashsms=" + encode(booleanFlag(config.get("flashSms")))
                + "&number=" + encode("91" + mobileNumber)
                + "&text=" + encode(message)
                + "&unicode=" + encode(booleanFlag(config.get("unicode")));
    }

    private String booleanFlag(String value) {
        return Boolean.parseBoolean(defaultIfBlank(value, "false")) ? "1" : "0";
    }

    private int safeInt(String value, int fallback) {
        try {
            return Integer.parseInt(defaultIfBlank(value, String.valueOf(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String require(Map<String, String> config, String key, String message) {
        String value = config.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String encode(String value) {
        return UriUtils.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
