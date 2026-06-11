package com.billing.service;

import com.billing.dto.notification.ProviderSettingsRequest;
import com.billing.dto.notification.ProviderSettingsResponse;
import com.billing.entity.Company;
import com.billing.entity.EmailProviderSetting;
import com.billing.entity.SmsProviderSetting;
import com.billing.exception.BadRequestException;
import com.billing.repository.EmailProviderSettingRepository;
import com.billing.repository.SmsProviderSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final AccessControlService accessControlService;
    private final EmailProviderSettingRepository emailProviderSettingRepository;
    private final SmsProviderSettingRepository smsProviderSettingRepository;

    @Transactional(readOnly = true)
    public List<ProviderSettingsResponse> emailSettings(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        return emailProviderSettingRepository.findByCompanyOrderByActiveDescProviderNameAsc(company).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProviderSettingsResponse saveEmailSettings(String email, ProviderSettingsRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        EmailProviderSetting setting = request.getId() == null
                ? EmailProviderSetting.builder().company(company).build()
                : emailProviderSettingRepository.findByIdAndCompany(request.getId(), company)
                        .orElseThrow(() -> new BadRequestException("Email provider settings not found"));
        boolean active = request.getActive() == null || Boolean.TRUE.equals(request.getActive());
        if (active) {
            emailProviderSettingRepository.findByCompanyAndActiveTrue(company).forEach(existing -> {
                if (setting.getId() == null || !existing.getId().equals(setting.getId())) {
                    existing.setActive(false);
                    emailProviderSettingRepository.save(existing);
                }
            });
        }
        setting.setProviderName(blankDefault(request.getProviderName(), "AWS_SES"));
        setting.setSenderEmail(blankDefault(request.getSenderEmail(), ""));
        setting.setAwsAccessKey(keepExistingSecret(request.getAwsAccessKey(), setting.getAwsAccessKey()));
        setting.setAwsSecretKey(keepExistingSecret(request.getAwsSecretKey(), setting.getAwsSecretKey()));
        setting.setAwsRegion(request.getAwsRegion());
        setting.setActive(active);
        EmailProviderSetting saved = emailProviderSettingRepository.save(setting);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProviderSettingsResponse> smsSettings(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        return smsProviderSettingRepository.findByCompanyOrderByActiveDescProviderNameAsc(company).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProviderSettingsResponse saveSmsSettings(String email, ProviderSettingsRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        SmsProviderSetting setting = request.getId() == null
                ? SmsProviderSetting.builder().company(company).build()
                : smsProviderSettingRepository.findByIdAndCompany(request.getId(), company)
                        .orElseThrow(() -> new BadRequestException("SMS provider settings not found"));
        boolean active = request.getActive() == null || Boolean.TRUE.equals(request.getActive());
        if (active) {
            smsProviderSettingRepository.findByCompanyAndActiveTrue(company).forEach(existing -> {
                if (setting.getId() == null || !existing.getId().equals(setting.getId())) {
                    existing.setActive(false);
                    smsProviderSettingRepository.save(existing);
                }
            });
        }
        setting.setProviderName(blankDefault(request.getProviderName(), "HTTP_SMS"));
        setting.setApiUrl(blankDefault(request.getApiUrl(), ""));
        setting.setUsername(request.getUsername());
        setting.setPassword(keepExistingSecret(request.getPassword(), setting.getPassword()));
        setting.setSenderId(request.getSenderId());
        setting.setChannelName(request.getChannelName());
        setting.setActive(active);
        SmsProviderSetting saved = smsProviderSettingRepository.save(setting);
        return toResponse(saved);
    }

    private ProviderSettingsResponse toResponse(EmailProviderSetting setting) {
        return ProviderSettingsResponse.builder()
                .id(setting.getId())
                .providerName(setting.getProviderName())
                .senderEmail(setting.getSenderEmail())
                .awsAccessKey(mask(setting.getAwsAccessKey()))
                .awsRegion(setting.getAwsRegion())
                .active(setting.isActive())
                .build();
    }

    private ProviderSettingsResponse toResponse(SmsProviderSetting setting) {
        return ProviderSettingsResponse.builder()
                .id(setting.getId())
                .providerName(setting.getProviderName())
                .apiUrl(setting.getApiUrl())
                .username(setting.getUsername())
                .senderId(setting.getSenderId())
                .channelName(setting.getChannelName())
                .active(setting.isActive())
                .build();
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String keepExistingSecret(String incoming, String existing) {
        if (incoming == null || incoming.isBlank() || incoming.startsWith("****")) {
            return existing;
        }
        return incoming.trim();
    }

    private String mask(String value) {
        if (value == null || value.length() < 4) {
            return value;
        }
        return "****" + value.substring(value.length() - 4);
    }
}
