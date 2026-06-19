package com.billing.service;

import com.billing.dto.notification.EmailProviderTestRequest;
import com.billing.dto.notification.NotificationStatus;
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
    private final SecretEncryptionService secretEncryptionService;
    private final EmailService emailService;

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
        String providerName = normalizeEmailProvider(request.getProviderName());
        validateEmailSettings(providerName, request, setting);
        setting.setProviderName(providerName);
        setting.setSenderEmail(blankDefault(request.getSenderEmail(), ""));
        setting.setSmtpHost("GMAIL_SMTP".equals(providerName) ? blankDefault(request.getSmtpHost(), "smtp.gmail.com") : null);
        setting.setSmtpPort("GMAIL_SMTP".equals(providerName) ? request.getSmtpPort() == null ? 587 : request.getSmtpPort() : null);
        setting.setSmtpUsername("GMAIL_SMTP".equals(providerName) ? blankDefault(request.getSmtpUsername(), request.getSenderEmail()) : null);
        setting.setSmtpPassword("GMAIL_SMTP".equals(providerName) ? keepExistingEncryptedSecret(request.getSmtpPassword(), setting.getSmtpPassword()) : null);
        setting.setSmtpTlsEnabled(!"GMAIL_SMTP".equals(providerName) || request.getSmtpTlsEnabled() == null || Boolean.TRUE.equals(request.getSmtpTlsEnabled()));
        setting.setAwsAccessKey("AWS_SES".equals(providerName) ? keepExistingSecret(request.getAwsAccessKey(), setting.getAwsAccessKey()) : null);
        setting.setAwsSecretKey("AWS_SES".equals(providerName) ? keepExistingEncryptedSecret(request.getAwsSecretKey(), setting.getAwsSecretKey()) : null);
        setting.setAwsRegion("AWS_SES".equals(providerName) ? request.getAwsRegion() : null);
        setting.setActive(active);
        EmailProviderSetting saved = emailProviderSettingRepository.save(setting);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProviderSettingsResponse sendTestEmail(String email, EmailProviderTestRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        EmailProviderSetting activeProvider = emailProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company)
                .orElseThrow(() -> new BadRequestException("No active email provider configured"));
        String recipient = request == null || request.getRecipientEmail() == null || request.getRecipientEmail().isBlank()
                ? company.getEmail()
                : request.getRecipientEmail().trim();
        if (recipient == null || recipient.isBlank()) {
            throw new BadRequestException("Test recipient email is required");
        }
        EmailService.EmailDeliveryResult result = emailService.sendEmail(
                company,
                "Test email from " + company.getName(),
                "<p>This is a test email from your active email provider.</p>",
                List.of(recipient),
                List.of(),
                List.of(),
                List.of(),
                email
        );
        if (result.status() != NotificationStatus.SENT) {
            throw new BadRequestException("Test email failed: " + result.providerResponse());
        }
        return toResponse(activeProvider);
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
        setting.setPassword(keepExistingEncryptedSecret(request.getPassword(), setting.getPassword()));
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
                .smtpHost(setting.getSmtpHost())
                .smtpPort(setting.getSmtpPort())
                .smtpUsername(setting.getSmtpUsername())
                .smtpTlsEnabled(!Boolean.FALSE.equals(setting.getSmtpTlsEnabled()))
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

    private String keepExistingEncryptedSecret(String incoming, String existing) {
        if (incoming == null || incoming.isBlank() || incoming.startsWith("****")) {
            return existing;
        }
        return secretEncryptionService.encrypt(incoming.trim());
    }

    private String normalizeEmailProvider(String value) {
        String provider = blankDefault(value, "GMAIL_SMTP").toUpperCase().replace('-', '_').replace(' ', '_');
        if ("GMAIL".equals(provider) || "GMAILSMTP".equals(provider)) {
            return "GMAIL_SMTP";
        }
        if ("AWSSES".equals(provider) || "SES".equals(provider) || "AWS_SES".equals(provider)) {
            return "AWS_SES";
        }
        if (!"GMAIL_SMTP".equals(provider)) {
            throw new BadRequestException("Unsupported email provider. Use GMAIL_SMTP or AWS_SES");
        }
        return provider;
    }

    private void validateEmailSettings(String providerName, ProviderSettingsRequest request, EmailProviderSetting existing) {
        if (blankDefault(request.getSenderEmail(), "").isBlank()) {
            throw new BadRequestException("Sender email is required");
        }
        if ("GMAIL_SMTP".equals(providerName)) {
            if (blankDefault(request.getSmtpUsername(), request.getSenderEmail()).isBlank()) {
                throw new BadRequestException("SMTP username is required");
            }
            if ((request.getSmtpPassword() == null || request.getSmtpPassword().isBlank()) && (existing == null || existing.getSmtpPassword() == null || existing.getSmtpPassword().isBlank())) {
                throw new BadRequestException("SMTP password is required");
            }
            return;
        }
        if (blankDefault(request.getAwsAccessKey(), existing != null ? existing.getAwsAccessKey() : "").isBlank()) {
            throw new BadRequestException("AWS access key is required");
        }
        if ((request.getAwsSecretKey() == null || request.getAwsSecretKey().isBlank()) && (existing == null || existing.getAwsSecretKey() == null || existing.getAwsSecretKey().isBlank())) {
            throw new BadRequestException("AWS secret key is required");
        }
        if (blankDefault(request.getAwsRegion(), "").isBlank()) {
            throw new BadRequestException("AWS region is required");
        }
    }

    private String mask(String value) {
        if (value == null || value.length() < 4) {
            return value;
        }
        return "****" + value.substring(value.length() - 4);
    }
}
