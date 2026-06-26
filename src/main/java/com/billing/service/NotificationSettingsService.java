package com.billing.service;

import com.billing.dto.notification.EmailProviderTestRequest;
import com.billing.dto.notification.NotificationStatus;
import com.billing.dto.notification.ProviderSettingsRequest;
import com.billing.dto.notification.ProviderSettingsResponse;
import com.billing.dto.notification.SmsProviderMetadataResponse;
import com.billing.dto.notification.SmsProviderTestRequest;
import com.billing.dto.notification.WhatsAppProviderTestRequest;
import com.billing.dto.notification.WhatsAppProviderMetadataResponse;
import com.billing.entity.Company;
import com.billing.entity.EmailProviderSetting;
import com.billing.entity.SmsProviderSetting;
import com.billing.entity.WhatsAppProviderSetting;
import com.billing.entity.enums.SmsProviderType;
import com.billing.entity.enums.WhatsAppProviderType;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.CompanyRepository;
import com.billing.repository.EmailProviderSettingRepository;
import com.billing.repository.SmsProviderSettingRepository;
import com.billing.repository.WhatsAppProviderSettingRepository;
import com.billing.service.sms.CommonSmsService;
import com.billing.service.sms.SmsSendResult;
import com.billing.service.sms.config.SmsProviderConfigurationService;
import com.billing.service.whatsapp.CommonWhatsAppService;
import com.billing.service.whatsapp.WhatsAppProviderFactory;
import com.billing.service.whatsapp.WhatsAppSendResult;
import com.billing.service.whatsapp.config.WhatsAppProviderConfigurationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final AccessControlService accessControlService;
    private final CompanyRepository companyRepository;
    private final EmailProviderSettingRepository emailProviderSettingRepository;
    private final SmsProviderSettingRepository smsProviderSettingRepository;
    private final WhatsAppProviderSettingRepository whatsAppProviderSettingRepository;
    private final SecretEncryptionService secretEncryptionService;
    private final EmailService emailService;
    private final CommonSmsService commonSmsService;
    private final SmsProviderConfigurationService smsProviderConfigurationService;
    private final CommonWhatsAppService commonWhatsAppService;
    private final WhatsAppProviderFactory whatsAppProviderFactory;
    private final WhatsAppProviderConfigurationService whatsAppProviderConfigurationService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<WhatsAppProviderMetadataResponse> whatsAppProviderMetadata() {
        return whatsAppProviderFactory.metadata();
    }

    @Transactional(readOnly = true)
    public List<SmsProviderMetadataResponse> smsProviderMetadata() {
        return smsProviderConfigurationService.metadata();
    }

    @Transactional(readOnly = true)
    public List<ProviderSettingsResponse> emailSettings(String email) {
        return emailSettingsForCompany(accessControlService.getCurrentCompany(email));
    }

    @Transactional(readOnly = true)
    public List<ProviderSettingsResponse> emailSettingsForCompany(Long companyId) {
        return emailSettingsForCompany(resolveCompany(companyId));
    }

    private List<ProviderSettingsResponse> emailSettingsForCompany(Company company) {
        return emailProviderSettingRepository.findByCompanyOrderByActiveDescProviderNameAsc(company).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProviderSettingsResponse saveEmailSettings(String email, ProviderSettingsRequest request) {
        return saveEmailSettingsForCompany(accessControlService.getCurrentCompany(email), request, email, false);
    }

    @Transactional
    public ProviderSettingsResponse saveEmailSettingsForCompany(Long companyId, ProviderSettingsRequest request, String actorName) {
        return saveEmailSettingsForCompany(resolveCompany(companyId), request, actorName, true);
    }

    private ProviderSettingsResponse saveEmailSettingsForCompany(Company company, ProviderSettingsRequest request, String actorName, boolean actorScopedAudit) {
        EmailProviderSetting setting = request.getId() == null
                ? EmailProviderSetting.builder().company(company).build()
                : emailProviderSettingRepository.findByIdAndCompany(request.getId(), company)
                        .orElseThrow(() -> new BadRequestException("Email provider settings not found"));
        Map<String, Object> oldData = request.getId() == null ? null : snapshot(setting);
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
        setting.setSendgridApiKey("SENDGRID".equals(providerName) ? keepExistingEncryptedSecret(request.getSendgridApiKey(), setting.getSendgridApiKey()) : null);
        setting.setActive(active);
        EmailProviderSetting saved = emailProviderSettingRepository.save(setting);
        if (oldData == null) {
            logCreate(actorName, actorScopedAudit, company, "Email Provider", "EmailProviderSetting", saved.getId(), snapshot(saved));
        } else {
            logUpdate(actorName, actorScopedAudit, company, "Email Provider", "EmailProviderSetting", saved.getId(), oldData, snapshot(saved));
        }
        if (saved.isActive()) {
            logEvent(actorName, actorScopedAudit, company, "Email Provider", "EmailProviderSetting", saved.getId(), "EMAIL_PROVIDER_ACTIVATED", snapshot(saved));
        } else {
            logEvent(actorName, actorScopedAudit, company, "Email Provider", "EmailProviderSetting", saved.getId(), "EMAIL_PROVIDER_DEACTIVATED", snapshot(saved));
        }
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProviderSettingsResponse sendTestEmail(String email, EmailProviderTestRequest request) {
        return sendTestEmailForCompany(accessControlService.getCurrentCompany(email), request, email, false);
    }

    @Transactional(readOnly = true)
    public ProviderSettingsResponse sendTestEmailForCompany(Long companyId, EmailProviderTestRequest request, String actorName) {
        return sendTestEmailForCompany(resolveCompany(companyId), request, actorName, true);
    }

    private ProviderSettingsResponse sendTestEmailForCompany(Company company, EmailProviderTestRequest request, String actorName, boolean actorScopedAudit) {
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
                actorName
        );
        if (result.status() != NotificationStatus.SENT) {
            throw new BadRequestException("Test email failed: " + result.providerResponse());
        }
        logEvent(actorName, actorScopedAudit, company, "Email Provider", "EmailProviderSetting", activeProvider.getId(), "EMAIL_TEST_SENT", Map.of(
                "provider_name", activeProvider.getProviderName(),
                "recipient_email", recipient,
                "status", result.status().name()
        ));
        return toResponse(activeProvider);
    }

    @Transactional(readOnly = true)
    public List<ProviderSettingsResponse> smsSettings(String email) {
        return smsSettingsForCompany(accessControlService.getCurrentCompany(email));
    }

    @Transactional(readOnly = true)
    public List<ProviderSettingsResponse> smsSettingsForCompany(Long companyId) {
        return smsSettingsForCompany(resolveCompany(companyId));
    }

    private List<ProviderSettingsResponse> smsSettingsForCompany(Company company) {
        return smsProviderSettingRepository.findByCompanyOrderByActiveDescProviderNameAsc(company).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProviderSettingsResponse saveSmsSettings(String email, ProviderSettingsRequest request) {
        return saveSmsSettingsForCompany(accessControlService.getCurrentCompany(email), request, email, false);
    }

    @Transactional
    public ProviderSettingsResponse saveSmsSettingsForCompany(Long companyId, ProviderSettingsRequest request, String actorName) {
        return saveSmsSettingsForCompany(resolveCompany(companyId), request, actorName, true);
    }

    private ProviderSettingsResponse saveSmsSettingsForCompany(Company company, ProviderSettingsRequest request, String actorName, boolean actorScopedAudit) {
        SmsProviderSetting setting = request.getId() == null
                ? SmsProviderSetting.builder().company(company).build()
                : smsProviderSettingRepository.findByIdAndCompany(request.getId(), company)
                        .orElseThrow(() -> new BadRequestException("SMS provider settings not found"));
        Map<String, Object> oldData = request.getId() == null ? null : snapshot(setting);
        boolean active = request.getActive() == null || Boolean.TRUE.equals(request.getActive());
        if (active) {
            smsProviderSettingRepository.findByCompanyAndActiveTrue(company).forEach(existing -> {
                if (setting.getId() == null || !existing.getId().equals(setting.getId())) {
                    existing.setActive(false);
                    smsProviderSettingRepository.save(existing);
                }
            });
        }
        SmsProviderType providerType = normalizeSmsProviderType(request.getProviderType());
        validateSmsSettings(providerType, request, setting);
        setting.setProviderName(blankDefault(request.getProviderName(), providerType.name()));
        setting.setProviderType(providerType);
        Map<String, String> existingStoredValues = smsProviderConfigurationService.stored(setting);
        Map<String, String> normalizedConfig = normalizedSmsConfig(providerType, request);
        String providerConfig = smsProviderConfigurationService.serializeForStorage(providerType, normalizedConfig, existingStoredValues);
        setting.setProviderConfig(providerConfig);
        syncLegacySmsColumns(setting, providerType, smsProviderConfigurationService.decrypted(setting));
        setting.setActive(active);
        SmsProviderSetting saved = smsProviderSettingRepository.save(setting);
        if (oldData == null) {
            logCreate(actorName, actorScopedAudit, company, "SMS Provider", "SmsProviderSetting", saved.getId(), snapshot(saved));
        } else {
            logUpdate(actorName, actorScopedAudit, company, "SMS Provider", "SmsProviderSetting", saved.getId(), oldData, snapshot(saved));
        }
        if (saved.isActive()) {
            logEvent(actorName, actorScopedAudit, company, "SMS Provider", "SmsProviderSetting", saved.getId(), "SMS_PROVIDER_ACTIVATED", snapshot(saved));
        } else {
            logEvent(actorName, actorScopedAudit, company, "SMS Provider", "SmsProviderSetting", saved.getId(), "SMS_PROVIDER_DEACTIVATED", snapshot(saved));
        }
        return toResponse(saved);
    }

    @Transactional
    public ProviderSettingsResponse sendTestSms(String email, SmsProviderTestRequest request) {
        return sendTestSmsForCompany(accessControlService.getCurrentCompany(email), request, email, false);
    }

    @Transactional
    public ProviderSettingsResponse sendTestSmsForCompany(Long companyId, SmsProviderTestRequest request, String actorName) {
        return sendTestSmsForCompany(resolveCompany(companyId), request, actorName, true);
    }

    private ProviderSettingsResponse sendTestSmsForCompany(Company company, SmsProviderTestRequest request, String actorName, boolean actorScopedAudit) {
        SmsProviderSetting activeProvider = resolveSmsTestSetting(company, request);
        String mobileNumber = request == null || request.getMobileNumber() == null || request.getMobileNumber().isBlank()
                ? company.getPhone()
                : request.getMobileNumber().trim();
        if (mobileNumber == null || mobileNumber.isBlank()) {
            throw new BadRequestException("Test mobile number is required");
        }
        SmsSendResult result = commonSmsService.testConnection(company, mobileNumber, "This is a test SMS from your active SMS provider.");
        if (result.status() != NotificationStatus.SENT) {
            throw new BadRequestException("Test SMS failed: " + result.providerResponse());
        }
        logEvent(actorName, actorScopedAudit, company, "SMS Provider", "SmsProviderSetting", activeProvider.getId(), "SMS_TEST_SENT", Map.of(
                "provider_name", activeProvider.getProviderName(),
                "provider_type", activeProvider.getProviderType().name(),
                "mobile_number", result.mobileNumber(),
                "status", result.status().name()
        ));
        return toResponse(activeProvider);
    }

    @Transactional(readOnly = true)
    public List<ProviderSettingsResponse> whatsAppSettings(String email) {
        return whatsAppSettingsForCompany(accessControlService.getCurrentCompany(email));
    }

    @Transactional(readOnly = true)
    public List<ProviderSettingsResponse> whatsAppSettingsForCompany(Long companyId) {
        return whatsAppSettingsForCompany(resolveCompany(companyId));
    }

    private List<ProviderSettingsResponse> whatsAppSettingsForCompany(Company company) {
        return whatsAppProviderSettingRepository.findByCompanyOrderByActiveDescProviderNameAsc(company).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProviderSettingsResponse saveWhatsAppSettings(String email, ProviderSettingsRequest request) {
        return saveWhatsAppSettingsForCompany(accessControlService.getCurrentCompany(email), request, email, false);
    }

    @Transactional
    public ProviderSettingsResponse saveWhatsAppSettingsForCompany(Long companyId, ProviderSettingsRequest request, String actorName) {
        return saveWhatsAppSettingsForCompany(resolveCompany(companyId), request, actorName, true);
    }

    private ProviderSettingsResponse saveWhatsAppSettingsForCompany(Company company, ProviderSettingsRequest request, String actorName, boolean actorScopedAudit) {
        WhatsAppProviderSetting setting = request.getId() == null
                ? WhatsAppProviderSetting.builder().company(company).build()
                : whatsAppProviderSettingRepository.findByIdAndCompany(request.getId(), company)
                .orElseThrow(() -> new BadRequestException("WhatsApp provider settings not found"));
        Map<String, Object> oldData = request.getId() == null ? null : snapshot(setting);
        boolean active = request.getActive() == null || Boolean.TRUE.equals(request.getActive());
        if (active) {
            whatsAppProviderSettingRepository.findByCompanyAndActiveTrue(company).forEach(existing -> {
                if (setting.getId() == null || !existing.getId().equals(setting.getId())) {
                    existing.setActive(false);
                    whatsAppProviderSettingRepository.save(existing);
                }
            });
        }
        WhatsAppProviderType providerType = normalizeWhatsAppProviderType(request.getProviderType());
        validateWhatsAppSettings(providerType, request, setting);
        setting.setProviderName(blankDefault(request.getProviderName(), providerType.name()));
        setting.setProviderType(providerType);
        Map<String, String> existingStoredValues = whatsAppProviderConfigurationService.stored(setting);
        Map<String, String> normalizedConfig = normalizedWhatsAppConfig(providerType, request);
        String providerConfig = whatsAppProviderConfigurationService.serializeForStorage(providerType, normalizedConfig, existingStoredValues);
        setting.setProviderConfig(providerConfig);
        syncLegacyWhatsAppColumns(setting, providerType, whatsAppProviderConfigurationService.decrypted(setting));
        setting.setActive(active);
        WhatsAppProviderSetting saved = whatsAppProviderSettingRepository.save(setting);
        if (oldData == null) {
            logCreate(actorName, actorScopedAudit, company, "WhatsApp Provider", "WhatsAppProviderSetting", saved.getId(), snapshot(saved));
        } else {
            logUpdate(actorName, actorScopedAudit, company, "WhatsApp Provider", "WhatsAppProviderSetting", saved.getId(), oldData, snapshot(saved));
        }
        if (saved.isActive()) {
            logEvent(actorName, actorScopedAudit, company, "WhatsApp Provider", "WhatsAppProviderSetting", saved.getId(), "WHATSAPP_PROVIDER_ACTIVATED", snapshot(saved));
        } else {
            logEvent(actorName, actorScopedAudit, company, "WhatsApp Provider", "WhatsAppProviderSetting", saved.getId(), "WHATSAPP_PROVIDER_DEACTIVATED", snapshot(saved));
        }
        return toResponse(saved);
    }

    @Transactional
    public ProviderSettingsResponse sendTestWhatsApp(String email, WhatsAppProviderTestRequest request) {
        return sendTestWhatsAppForCompany(accessControlService.getCurrentCompany(email), request, email, false);
    }

    @Transactional
    public ProviderSettingsResponse sendTestWhatsAppForCompany(Long companyId, WhatsAppProviderTestRequest request, String actorName) {
        return sendTestWhatsAppForCompany(resolveCompany(companyId), request, actorName, true);
    }

    private ProviderSettingsResponse sendTestWhatsAppForCompany(Company company, WhatsAppProviderTestRequest request, String actorName, boolean actorScopedAudit) {
        WhatsAppProviderSetting activeProvider = resolveWhatsAppTestSetting(company, request);
        String mobileNumber = request == null || request.getMobileNumber() == null || request.getMobileNumber().isBlank()
                ? company.getPhone()
                : request.getMobileNumber().trim();
        if (mobileNumber == null || mobileNumber.isBlank()) {
            throw new BadRequestException("Test mobile number is required");
        }
        String message = request == null || request.getMessage() == null || request.getMessage().isBlank()
                ? "This is a test WhatsApp message from your active provider."
                : request.getMessage().trim();
        WhatsAppSendResult result = commonWhatsAppService.testConnection(company, activeProvider, mobileNumber, message);
        if (result.status() != NotificationStatus.SENT) {
            throw new BadRequestException("Test WhatsApp message failed: " + result.providerResponse());
        }
        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put("provider_name", activeProvider.getProviderName());
        auditData.put("provider_type", activeProvider.getProviderType().name());
        auditData.put("mobile_number", result.mobileNumber());
        auditData.put("message_id", result.messageId());
        auditData.put("status", result.status().name());
        logEvent(actorName, actorScopedAudit, company, "WhatsApp Provider", "WhatsAppProviderSetting", activeProvider.getId(), "WHATSAPP_TEST_SENT", auditData);
        return toResponse(activeProvider);
    }

    private Company resolveCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
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
                .sendgridApiKey(maskSecret(setting.getSendgridApiKey()))
                .active(setting.isActive())
                .build();
    }

    private ProviderSettingsResponse toResponse(SmsProviderSetting setting) {
        return ProviderSettingsResponse.builder()
                .id(setting.getId())
                .providerName(setting.getProviderName())
                .apiUrl(setting.getApiUrl())
                .providerType(setting.getProviderType() != null ? setting.getProviderType().name() : null)
                .authKey(maskSecret(setting.getAuthKey()))
                .senderId(setting.getSenderId())
                .templateId(setting.getTemplateId())
                .configValues(smsProviderConfigurationService.masked(setting))
                .active(setting.isActive())
                .build();
    }

    private ProviderSettingsResponse toResponse(WhatsAppProviderSetting setting) {
        return ProviderSettingsResponse.builder()
                .id(setting.getId())
                .providerName(setting.getProviderName())
                .providerType(setting.getProviderType() != null ? setting.getProviderType().name() : null)
                .apiUrl(setting.getApiUrl())
                .authKey(maskSecret(setting.getAuthKey()))
                .whatsappNumber(setting.getWhatsappNumber())
                .senderName(setting.getSenderName())
                .configValues(whatsAppProviderConfigurationService.masked(setting))
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

    private SmsProviderType normalizeSmsProviderType(String value) {
        String provider = blankDefault(value, "MSG91").toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return SmsProviderType.valueOf(provider);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unsupported SMS provider type: " + value);
        }
    }

    private WhatsAppProviderType normalizeWhatsAppProviderType(String value) {
        String provider = blankDefault(value, "MSG91").toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return WhatsAppProviderType.valueOf(provider);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unsupported WhatsApp provider type: " + value);
        }
    }

    private String normalizeEmailProvider(String value) {
        String provider = blankDefault(value, "GMAIL_SMTP").toUpperCase().replace('-', '_').replace(' ', '_');
        if ("GMAIL".equals(provider) || "GMAILSMTP".equals(provider)) {
            return "GMAIL_SMTP";
        }
        if ("AWSSES".equals(provider) || "SES".equals(provider) || "AWS_SES".equals(provider)) {
            return "AWS_SES";
        }
        if ("SEND_GRID".equals(provider) || "SENDGRID".equals(provider)) {
            return "SENDGRID";
        }
        if (!"GMAIL_SMTP".equals(provider)) {
            throw new BadRequestException("Unsupported email provider. Use GMAIL_SMTP, AWS_SES, or SENDGRID");
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
        if ("SENDGRID".equals(providerName)) {
            if ((request.getSendgridApiKey() == null || request.getSendgridApiKey().isBlank()) && (existing == null || existing.getSendgridApiKey() == null || existing.getSendgridApiKey().isBlank())) {
                throw new BadRequestException("SendGrid API key is required");
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

    private void validateSmsSettings(SmsProviderType providerType, ProviderSettingsRequest request, SmsProviderSetting existing) {
        if (blankDefault(request.getProviderName(), "").isBlank()) {
            throw new BadRequestException("Provider name is required");
        }
        Map<String, String> incoming = normalizedSmsConfig(providerType, request);
        Map<String, String> stored = existing == null ? Map.of() : smsProviderConfigurationService.stored(existing);
        for (var field : smsProviderConfigurationService.fields(providerType)) {
            String value = incoming.get(field.getKey());
            String existingValue = stored.get(field.getKey());
            if (field.isRequired() && (value == null || value.isBlank()) && (existingValue == null || existingValue.isBlank())) {
                throw new BadRequestException(field.getLabel() + " is required");
            }
        }
    }

    private void validateWhatsAppSettings(WhatsAppProviderType providerType, ProviderSettingsRequest request, WhatsAppProviderSetting existing) {
        if (blankDefault(request.getProviderName(), "").isBlank()) {
            throw new BadRequestException("Provider name is required");
        }
        WhatsAppProviderSetting probe = existing == null ? WhatsAppProviderSetting.builder().providerType(providerType).build() : existing;
        probe.setProviderType(providerType);
        Map<String, String> stored = existing == null ? Map.of() : whatsAppProviderConfigurationService.stored(existing);
        String providerConfig = whatsAppProviderConfigurationService.serializeForStorage(providerType, normalizedWhatsAppConfig(providerType, request), stored);
        probe.setProviderConfig(providerConfig);
        syncLegacyWhatsAppColumns(probe, providerType, whatsAppProviderConfigurationService.decrypted(probe));
        whatsAppProviderFactory.getProvider(providerType).validateConfiguration(whatsAppProviderConfigurationService.resolved(probe));
    }

    private String mask(String value) {
        if (value == null || value.length() < 4) {
            return value;
        }
        return "****" + value.substring(value.length() - 4);
    }

    private String maskSecret(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return encryptedValue;
        }
        try {
            return mask(secretEncryptionService.decrypt(encryptedValue));
        } catch (Exception ignored) {
            return "****";
        }
    }

    private Map<String, Object> snapshot(SmsProviderSetting setting) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider_name", setting.getProviderName());
        data.put("provider_type", setting.getProviderType() != null ? setting.getProviderType().name() : null);
        data.put("api_url", setting.getApiUrl());
        data.put("auth_key", maskSecret(setting.getAuthKey()));
        data.put("sender_id", setting.getSenderId());
        data.put("template_id", setting.getTemplateId());
        data.put("config_values", smsProviderConfigurationService.masked(setting));
        data.put("active", setting.isActive());
        return data;
    }

    private Map<String, String> normalizedSmsConfig(SmsProviderType providerType, ProviderSettingsRequest request) {
        Map<String, String> config = new LinkedHashMap<>();
        if (request.getConfigValues() != null) {
            request.getConfigValues().forEach((key, value) -> {
                if (key != null) {
                    config.put(key, value);
                }
            });
        }
        if (request.getApiUrl() != null) {
            config.put("apiUrl", request.getApiUrl());
        }
        if (providerType == SmsProviderType.MSG91) {
            if (request.getAuthKey() != null) {
                config.put("authKey", request.getAuthKey());
            }
            if (request.getSenderId() != null) {
                config.put("senderId", request.getSenderId());
            }
            if (request.getTemplateId() != null) {
                config.put("templateId", request.getTemplateId());
            }
        }
        return config;
    }

    private void syncLegacySmsColumns(SmsProviderSetting setting, SmsProviderType providerType, Map<String, String> decryptedConfig) {
        setting.setApiUrl(blankDefault(decryptedConfig.get("apiUrl"), providerType == SmsProviderType.MSG91 ? "https://api.msg91.com/api/v2/sendsms" : ""));
        if (providerType == SmsProviderType.MSG91) {
            Map<String, String> stored = smsProviderConfigurationService.stored(setting);
            setting.setAuthKey(stored.get("authKey"));
            setting.setSenderId(blankDefault(decryptedConfig.get("senderId"), ""));
            setting.setTemplateId(blankDefault(decryptedConfig.get("templateId"), ""));
            return;
        }
        setting.setAuthKey(null);
        setting.setSenderId(blankDefault(decryptedConfig.get("senderId"), ""));
        setting.setTemplateId(null);
    }

    private SmsProviderSetting resolveSmsTestSetting(Company company, SmsProviderTestRequest request) {
        boolean useDraftSettings = request != null && request.getProviderType() != null && request.getConfigValues() != null && !request.getConfigValues().isEmpty();
        if (!useDraftSettings) {
            return smsProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company)
                    .orElseThrow(() -> new BadRequestException("No active SMS provider configured"));
        }
        SmsProviderType providerType = normalizeSmsProviderType(request.getProviderType());
        ProviderSettingsRequest probeRequest = new ProviderSettingsRequest();
        probeRequest.setProviderName(blankDefault(request.getProviderName(), providerType.name()));
        probeRequest.setProviderType(providerType.name());
        probeRequest.setApiUrl(request.getApiUrl());
        probeRequest.setConfigValues(request.getConfigValues());
        probeRequest.setActive(true);
        validateSmsSettings(providerType, probeRequest, null);
        SmsProviderSetting probe = SmsProviderSetting.builder()
                .company(company)
                .providerName(blankDefault(request.getProviderName(), providerType.name()))
                .providerType(providerType)
                .active(true)
                .build();
        probe.setProviderConfig(smsProviderConfigurationService.serializeForStorage(providerType, normalizedSmsConfig(providerType, probeRequest), Map.of()));
        syncLegacySmsColumns(probe, providerType, smsProviderConfigurationService.decrypted(probe));
        return probe;
    }

    private Map<String, Object> snapshot(EmailProviderSetting setting) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider_name", setting.getProviderName());
        data.put("sender_email", setting.getSenderEmail());
        data.put("smtp_host", setting.getSmtpHost());
        data.put("smtp_port", setting.getSmtpPort());
        data.put("smtp_username", setting.getSmtpUsername());
        data.put("smtp_password", maskSecret(setting.getSmtpPassword()));
        data.put("smtp_tls_enabled", setting.getSmtpTlsEnabled());
        data.put("aws_access_key", mask(setting.getAwsAccessKey()));
        data.put("aws_secret_key", maskSecret(setting.getAwsSecretKey()));
        data.put("aws_region", setting.getAwsRegion());
        data.put("sendgrid_api_key", maskSecret(setting.getSendgridApiKey()));
        data.put("active", setting.isActive());
        return data;
    }

    private Map<String, Object> snapshot(WhatsAppProviderSetting setting) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider_name", setting.getProviderName());
        data.put("provider_type", setting.getProviderType() != null ? setting.getProviderType().name() : null);
        data.put("api_url", setting.getApiUrl());
        data.put("auth_key", maskSecret(setting.getAuthKey()));
        data.put("whatsapp_number", setting.getWhatsappNumber());
        data.put("sender_name", setting.getSenderName());
        data.put("config_values", whatsAppProviderConfigurationService.masked(setting));
        data.put("active", setting.isActive());
        return data;
    }

    private Map<String, String> normalizedWhatsAppConfig(WhatsAppProviderType providerType, ProviderSettingsRequest request) {
        Map<String, String> config = new LinkedHashMap<>();
        if (request.getConfigValues() != null) {
            request.getConfigValues().forEach((key, value) -> {
                if (key != null) {
                    config.put(key, value);
                }
            });
        }
        if (request.getApiUrl() != null) {
            config.put("apiUrl", request.getApiUrl());
        }
        if (providerType == WhatsAppProviderType.MSG91) {
            if (request.getAuthKey() != null) {
                config.put("authKey", request.getAuthKey());
            }
            if (request.getWhatsappNumber() != null) {
                config.put("whatsappNumber", request.getWhatsappNumber());
            }
            if (request.getSenderName() != null) {
                config.put("senderName", request.getSenderName());
            }
        }
        return config;
    }

    private void syncLegacyWhatsAppColumns(WhatsAppProviderSetting setting, WhatsAppProviderType providerType, Map<String, String> decryptedConfig) {
        setting.setApiUrl(blankDefault(decryptedConfig.get("apiUrl"), providerType == WhatsAppProviderType.MSG91
                ? "https://control.msg91.com/api/v5/whatsapp/whatsapp-outbound-message"
                : "https://api.pinnacle.example.com/v1/whatsapp/messages"));
        if (providerType == WhatsAppProviderType.MSG91) {
            Map<String, String> stored = whatsAppProviderConfigurationService.stored(setting);
            setting.setAuthKey(stored.get("authKey"));
            setting.setWhatsappNumber(blankDefault(decryptedConfig.get("whatsappNumber"), ""));
            setting.setSenderName(blankDefault(decryptedConfig.get("senderName"), ""));
            return;
        }
        setting.setAuthKey(null);
        setting.setWhatsappNumber(blankDefault(decryptedConfig.get("businessNumber"), ""));
        setting.setSenderName(blankDefault(decryptedConfig.get("senderId"), setting.getProviderName()));
    }

    private WhatsAppProviderSetting resolveWhatsAppTestSetting(Company company, WhatsAppProviderTestRequest request) {
        boolean useDraftSettings = request != null && request.getProviderType() != null && request.getConfigValues() != null && !request.getConfigValues().isEmpty();
        if (!useDraftSettings) {
            return whatsAppProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company)
                    .orElseThrow(() -> new BadRequestException("No active WhatsApp provider configured"));
        }
        WhatsAppProviderType providerType = normalizeWhatsAppProviderType(request.getProviderType());
        ProviderSettingsRequest probeRequest = new ProviderSettingsRequest();
        probeRequest.setProviderName(blankDefault(request.getProviderName(), providerType.name()));
        probeRequest.setProviderType(providerType.name());
        probeRequest.setApiUrl(request.getApiUrl());
        probeRequest.setConfigValues(request.getConfigValues());
        probeRequest.setActive(true);
        validateWhatsAppSettings(providerType, probeRequest, null);
        WhatsAppProviderSetting probe = WhatsAppProviderSetting.builder()
                .company(company)
                .providerName(blankDefault(request.getProviderName(), providerType.name()))
                .providerType(providerType)
                .active(true)
                .build();
        String providerConfig = whatsAppProviderConfigurationService.serializeForStorage(providerType, normalizedWhatsAppConfig(providerType, probeRequest), Map.of());
        probe.setProviderConfig(providerConfig);
        syncLegacyWhatsAppColumns(probe, providerType, whatsAppProviderConfigurationService.decrypted(probe));
        return probe;
    }

    private void logCreate(String actorName, boolean actorScopedAudit, Company company, String moduleName, String entityName, Long entityId, Map<String, Object> newData) {
        if (actorScopedAudit) {
            auditLogService.logCreateAsActor(actorName, company, moduleName, entityName, entityId, newData);
            return;
        }
        auditLogService.logCreate(actorName, company, moduleName, entityName, entityId, newData);
    }

    private void logUpdate(String actorName, boolean actorScopedAudit, Company company, String moduleName, String entityName, Long entityId, Map<String, Object> oldData, Map<String, Object> newData) {
        if (actorScopedAudit) {
            auditLogService.logUpdateAsActor(actorName, company, moduleName, entityName, entityId, oldData, newData);
            return;
        }
        auditLogService.logUpdate(actorName, company, moduleName, entityName, entityId, oldData, newData);
    }

    private void logEvent(String actorName, boolean actorScopedAudit, Company company, String moduleName, String entityName, Long entityId, String actionType, Map<String, Object> data) {
        if (actorScopedAudit) {
            auditLogService.logEventAsActor(actorName, company, moduleName, entityName, entityId, actionType, data);
            return;
        }
        auditLogService.logEvent(actorName, company, moduleName, entityName, entityId, actionType, data);
    }
}
