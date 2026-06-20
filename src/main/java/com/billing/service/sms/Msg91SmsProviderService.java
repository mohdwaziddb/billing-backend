package com.billing.service.sms;

import com.billing.dto.notification.NotificationStatus;
import com.billing.entity.Company;
import com.billing.entity.SmsProviderSetting;
import com.billing.service.SecretEncryptionService;
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

@Service
@RequiredArgsConstructor
public class Msg91SmsProviderService implements SmsProviderService {

    private static final String DEFAULT_MSG91_API_URL = "https://api.msg91.com/api/v2/sendsms";

    private final SecretEncryptionService secretEncryptionService;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Override
    public String providerType() {
        return "MSG91";
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
        try {
            validateSettings(settings);
            String url = buildProviderUrl(settings, mobileNumber, message);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("authkey", secretEncryptionService.decrypt(settings.getAuthKey()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            NotificationStatus status = response.statusCode() >= 200 && response.statusCode() < 300
                    ? NotificationStatus.SENT
                    : NotificationStatus.FAILED;
            return new SmsSendResult(mobileNumber, status, response.body(), LocalDateTime.now());
        } catch (IOException ex) {
            return new SmsSendResult(mobileNumber, NotificationStatus.FAILED, ex.getMessage(), LocalDateTime.now());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new SmsSendResult(mobileNumber, NotificationStatus.FAILED, ex.getMessage(), LocalDateTime.now());
        } catch (RuntimeException ex) {
            return new SmsSendResult(mobileNumber, NotificationStatus.FAILED, ex.getMessage(), LocalDateTime.now());
        }
    }

    private void validateSettings(SmsProviderSetting settings) {
        if (!hasText(settings.getSenderId()) || !hasText(settings.getTemplateId()) || !hasText(settings.getAuthKey())) {
            throw new IllegalStateException("MSG91 provider is missing sender ID, template ID, or auth key");
        }
        if (!hasText(secretEncryptionService.decrypt(settings.getAuthKey()))) {
            throw new IllegalStateException("MSG91 auth key is unavailable");
        }
    }

    private String buildProviderUrl(SmsProviderSetting settings, String mobileNumber, String message) {
        String baseUrl = hasText(settings.getApiUrl()) ? settings.getApiUrl().trim() : DEFAULT_MSG91_API_URL;
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator
                + "mobiles=91" + encode(mobileNumber)
                + "&sender=" + encode(settings.getSenderId())
                + "&route=4"
                + "&DLT_TE_ID=" + encode(settings.getTemplateId())
                + "&unicode=1"
                + "&message=" + encode(message);
    }

    private String encode(String value) {
        return UriUtils.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
