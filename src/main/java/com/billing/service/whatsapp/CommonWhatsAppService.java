package com.billing.service.whatsapp;

import com.billing.dto.notification.NotificationAttachmentRequest;
import com.billing.dto.notification.NotificationStatus;
import com.billing.entity.Company;
import com.billing.entity.WhatsAppProviderSetting;
import com.billing.repository.WhatsAppProviderSettingRepository;
import com.billing.service.whatsapp.config.WhatsAppProviderConfigurationService;
import com.billing.service.whatsapp.config.WhatsAppResolvedSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommonWhatsAppService {

    private final WhatsAppProviderSettingRepository whatsAppProviderSettingRepository;
    private final WhatsAppProviderFactory whatsAppProviderFactory;
    private final WhatsAppProviderConfigurationService configurationService;

    public List<WhatsAppSendResult> sendMessage(Company company, List<String> mobileNumbers, String message) {
        Set<String> validNumbers = normalizeNumbers(mobileNumbers);
        WhatsAppProviderSetting settings = whatsAppProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).orElse(null);
        if (settings == null) {
            return pending(validNumbers, "No active WhatsApp provider configured");
        }
        WhatsAppResolvedSettings resolved = configurationService.resolved(settings);
        return whatsAppProviderFactory.getProvider(settings).sendMessage(company, resolved, validNumbers.stream().toList(), message);
    }

    public List<WhatsAppSendResult> sendTemplate(Company company, List<String> mobileNumbers, String templateName, Map<String, Object> variables) {
        Set<String> validNumbers = normalizeNumbers(mobileNumbers);
        WhatsAppProviderSetting settings = whatsAppProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).orElse(null);
        if (settings == null) {
            return pending(validNumbers, "No active WhatsApp provider configured");
        }
        WhatsAppResolvedSettings resolved = configurationService.resolved(settings);
        return whatsAppProviderFactory.getProvider(settings).sendTemplate(company, resolved, validNumbers.stream().toList(), templateName, variables);
    }

    public List<WhatsAppSendResult> sendMedia(Company company, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments) {
        Set<String> validNumbers = normalizeNumbers(mobileNumbers);
        WhatsAppProviderSetting settings = whatsAppProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).orElse(null);
        if (settings == null) {
            return pending(validNumbers, "No active WhatsApp provider configured");
        }
        WhatsAppResolvedSettings resolved = configurationService.resolved(settings);
        return whatsAppProviderFactory.getProvider(settings).sendMedia(company, resolved, validNumbers.stream().toList(), message, attachments);
    }

    public List<WhatsAppSendResult> sendDocument(Company company, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments) {
        Set<String> validNumbers = normalizeNumbers(mobileNumbers);
        WhatsAppProviderSetting settings = whatsAppProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).orElse(null);
        if (settings == null) {
            return pending(validNumbers, "No active WhatsApp provider configured");
        }
        WhatsAppResolvedSettings resolved = configurationService.resolved(settings);
        return whatsAppProviderFactory.getProvider(settings).sendDocument(company, resolved, validNumbers.stream().toList(), message, attachments);
    }

    public WhatsAppSendResult testConnection(Company company, String mobileNumber, String message) {
        WhatsAppProviderSetting settings = whatsAppProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).orElse(null);
        return testConnection(company, settings, mobileNumber, message);
    }

    public WhatsAppSendResult testConnection(Company company, WhatsAppProviderSetting settings, String mobileNumber, String message) {
        String normalized = normalizeNumber(mobileNumber);
        if (settings == null) {
            return new WhatsAppSendResult(normalized, NotificationStatus.PENDING, null, null, "No active WhatsApp provider configured", "No active WhatsApp provider configured", null);
        }
        WhatsAppResolvedSettings resolved = configurationService.resolved(settings);
        var health = whatsAppProviderFactory.getProvider(settings).healthCheck(company, resolved, normalized, message);
        return new WhatsAppSendResult(normalized, health.getStatus(), health.getProviderName(), health.getMessageId(), health.getFailureReason(), health.getProviderResponse(), health.getSentAt());
    }

    public Set<String> normalizeNumbers(List<String> mobileNumbers) {
        Set<String> numbers = new LinkedHashSet<>();
        if (mobileNumbers == null) {
            return numbers;
        }
        for (String raw : mobileNumbers) {
            String digits = normalizeNumber(raw);
            if (!digits.isBlank()) {
                numbers.add(digits);
            }
        }
        return numbers;
    }

    private List<WhatsAppSendResult> pending(Set<String> numbers, String response) {
        return numbers.stream()
                .map(number -> new WhatsAppSendResult(number, NotificationStatus.PENDING, null, null, response, response, null))
                .toList();
    }

    private String normalizeNumber(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\s+", "").replaceAll("[^0-9]", "");
        if (digits.length() < 10) {
            return "";
        }
        return digits.substring(digits.length() - 10);
    }
}
