package com.billing.service;

import com.billing.dto.notification.NotificationStatus;
import com.billing.entity.Company;
import com.billing.entity.SmsProviderSetting;
import com.billing.repository.SmsProviderSettingRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SmsService {

    private final SmsProviderSettingRepository smsProviderSettingRepository;
    private final SecretEncryptionService secretEncryptionService;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public List<SmsDeliveryResult> sendSms(Company company, List<String> mobileNumbers, String message) {
        SmsProviderSetting settings = smsProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).orElse(null);
        Set<String> validNumbers = normalizeNumbers(mobileNumbers);
        if (settings == null) {
            return validNumbers.stream()
                    .map(number -> new SmsDeliveryResult(number, NotificationStatus.PENDING, "No active SMS provider configured", null))
                    .toList();
        }
        return validNumbers.stream()
                .map(number -> sendOne(settings, number, message))
                .toList();
    }

    private SmsDeliveryResult sendOne(SmsProviderSetting settings, String mobileNumber, String message) {
        try {
            String url = buildProviderUrl(settings, mobileNumber, message);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            NotificationStatus status = response.statusCode() >= 200 && response.statusCode() < 300
                    ? NotificationStatus.SENT
                    : NotificationStatus.FAILED;
            return new SmsDeliveryResult(mobileNumber, status, response.body(), LocalDateTime.now());
        } catch (IOException ex) {
            return new SmsDeliveryResult(mobileNumber, NotificationStatus.FAILED, ex.getMessage(), LocalDateTime.now());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new SmsDeliveryResult(mobileNumber, NotificationStatus.FAILED, ex.getMessage(), LocalDateTime.now());
        } catch (RuntimeException ex) {
            return new SmsDeliveryResult(mobileNumber, NotificationStatus.FAILED, ex.getMessage(), LocalDateTime.now());
        }
    }

    private String buildProviderUrl(SmsProviderSetting settings, String mobileNumber, String message) {
        String separator = settings.getApiUrl().contains("?") ? "&" : "?";
        return settings.getApiUrl() + separator
                + "user=" + encode(settings.getUsername())
                + "&password=" + encode(secretEncryptionService.decrypt(settings.getPassword()))
                + "&senderid=" + encode(settings.getSenderId())
                + "&number=" + encode(mobileNumber)
                + "&text=" + encode(message);
    }

    private Set<String> normalizeNumbers(List<String> mobileNumbers) {
        Set<String> numbers = new LinkedHashSet<>();
        if (mobileNumbers == null) {
            return numbers;
        }
        for (String raw : mobileNumbers) {
            String digits = raw == null ? "" : raw.replaceAll("\\s+", "").replaceAll("[^0-9]", "");
            if (digits.length() >= 10) {
                numbers.add(digits.substring(digits.length() - 10));
            }
        }
        return numbers;
    }

    private String encode(String value) {
        return UriUtils.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record SmsDeliveryResult(String mobileNumber, NotificationStatus status, String providerResponse, LocalDateTime sentAt) {
    }
}
