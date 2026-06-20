package com.billing.service.sms;

import com.billing.dto.notification.NotificationStatus;
import com.billing.entity.Company;
import com.billing.entity.SmsProviderSetting;
import com.billing.repository.SmsProviderSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommonSmsService {

    private final SmsProviderSettingRepository smsProviderSettingRepository;
    private final SmsProviderFactory smsProviderFactory;

    public List<SmsSendResult> sendSms(Company company, List<String> mobileNumbers, String message) {
        Set<String> validNumbers = normalizeNumbers(mobileNumbers);
        SmsProviderSetting settings = smsProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).orElse(null);
        if (settings == null) {
            return validNumbers.stream()
                    .map(number -> new SmsSendResult(number, NotificationStatus.PENDING, "No active SMS provider configured", null))
                    .toList();
        }
        return smsProviderFactory.getProvider(settings).sendSms(company, settings, validNumbers.stream().toList(), message);
    }

    public SmsSendResult sendOtp(Company company, String mobileNumber, String message) {
        SmsProviderSetting settings = smsProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).orElse(null);
        String normalized = normalizeNumber(mobileNumber);
        if (settings == null) {
            return new SmsSendResult(normalized, NotificationStatus.PENDING, "No active SMS provider configured", null);
        }
        return smsProviderFactory.getProvider(settings).sendOtp(company, settings, normalized, message);
    }

    public SmsSendResult testConnection(Company company, String mobileNumber, String message) {
        SmsProviderSetting settings = smsProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).orElse(null);
        String normalized = normalizeNumber(mobileNumber);
        if (settings == null) {
            return new SmsSendResult(normalized, NotificationStatus.PENDING, "No active SMS provider configured", null);
        }
        return smsProviderFactory.getProvider(settings).testConnection(company, settings, normalized, message);
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

    private String normalizeNumber(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\s+", "").replaceAll("[^0-9]", "");
        if (digits.length() < 10) {
            return "";
        }
        return digits.substring(digits.length() - 10);
    }
}
