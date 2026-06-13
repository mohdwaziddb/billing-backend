package com.billing.service;

import com.billing.dto.email.EmailLogResponse;
import com.billing.dto.email.EmailSendRequest;
import com.billing.dto.notification.NotificationAttachmentRequest;
import com.billing.dto.notification.NotificationStatus;
import com.billing.entity.Company;
import com.billing.entity.EmailLog;
import com.billing.entity.EmailProviderSetting;
import com.billing.entity.EmailTemplate;
import com.billing.repository.EmailLogRepository;
import com.billing.repository.EmailProviderSettingRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.RawMessage;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailLogRepository emailLogRepository;
    private final EmailTemplateVariableService variableService;
    private final EmailProviderSettingRepository emailProviderSettingRepository;
    private final SecretEncryptionService secretEncryptionService;

    public EmailLogResponse sendTemplateEmail(String email, Company company, EmailTemplate template, EmailSendRequest request) {
        String subject = variableService.render(template.getSubject(), company, request.getVariables());
        String body = variableService.render(template.getEmailBody(), company, request.getVariables());
        EmailDeliveryResult result = sendEmail(company, subject, body, List.of(request.getRecipientEmail()), List.of(), List.of(), List.of(), email);

        EmailLog log = EmailLog.builder()
                .company(company)
                .template(template)
                .recipientEmail(request.getRecipientEmail().trim())
                .subject(subject)
                .emailBody(body)
                .status(result.status().name())
                .errorMessage(result.providerResponse())
                .sentBy(email)
                .sentAt(result.sentAt())
                .build();

        return toResponse(emailLogRepository.save(log));
    }

    public EmailDeliveryResult sendEmail(Company company,
                                         String subject,
                                         String htmlBody,
                                         List<String> toEmails,
                                         List<String> ccEmails,
                                         List<String> bccEmails,
                                         List<NotificationAttachmentRequest> attachments,
                                         String sentBy) {
        EmailProviderSetting settings = emailProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company).orElse(null);
        if (settings == null) {
            return new EmailDeliveryResult(NotificationStatus.PENDING, "No active email provider configured", null);
        }
        try {
            String providerName = normalizeProvider(settings.getProviderName());
            if ("AWS_SES".equals(providerName)) {
                return sendWithSes(settings, subject, htmlBody, toEmails, ccEmails, bccEmails, attachments);
            }
            if ("GMAIL_SMTP".equals(providerName)) {
                return sendWithSmtp(settings, subject, htmlBody, toEmails, ccEmails, bccEmails, attachments);
            }
            return new EmailDeliveryResult(NotificationStatus.FAILED, "Unsupported email provider: " + settings.getProviderName(), LocalDateTime.now());
        } catch (Exception ex) {
            return new EmailDeliveryResult(NotificationStatus.FAILED, ex.getMessage(), LocalDateTime.now());
        }
    }

    private String stripHtml(String value) {
        return value == null ? "" : value.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private EmailDeliveryResult sendWithSes(EmailProviderSetting settings,
                                            String subject,
                                            String htmlBody,
                                            List<String> toEmails,
                                            List<String> ccEmails,
                                            List<String> bccEmails,
                                            List<NotificationAttachmentRequest> attachments) throws Exception {
        validateSesSettings(settings);
        MimeMessage message = new MimeMessage(jakarta.mail.Session.getInstance(new Properties()));
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(settings.getSenderEmail().trim());
        helper.setTo(toEmails.stream().map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new));
        if (ccEmails != null && !ccEmails.isEmpty()) {
            helper.setCc(ccEmails.stream().map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new));
        }
        if (bccEmails != null && !bccEmails.isEmpty()) {
            helper.setBcc(bccEmails.stream().map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new));
        }
        helper.setSubject(subject);
        helper.setText(stripHtml(htmlBody), htmlBody);
        addAttachments(helper, attachments);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        message.writeTo(outputStream);
        try (SesClient sesClient = SesClient.builder()
                .region(Region.of(settings.getAwsRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(settings.getAwsAccessKey(), secretEncryptionService.decrypt(settings.getAwsSecretKey()))))
                .build()) {
            sesClient.sendRawEmail(SendRawEmailRequest.builder()
                    .rawMessage(RawMessage.builder().data(SdkBytes.fromByteArray(outputStream.toByteArray())).build())
                    .build());
        }
        return new EmailDeliveryResult(NotificationStatus.SENT, "AWS SES SendRawEmail accepted", LocalDateTime.now());
    }

    private EmailDeliveryResult sendWithSmtp(EmailProviderSetting settings,
                                             String subject,
                                             String htmlBody,
                                             List<String> toEmails,
                                             List<String> ccEmails,
                                             List<String> bccEmails,
                                             List<NotificationAttachmentRequest> attachments) throws Exception {
        validateSmtpSettings(settings);
        JavaMailSender mailSender = buildSmtpSender(settings);
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(settings.getSenderEmail().trim());
        helper.setTo(toEmails.stream().map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new));
        if (ccEmails != null && !ccEmails.isEmpty()) {
            helper.setCc(ccEmails.stream().map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new));
        }
        if (bccEmails != null && !bccEmails.isEmpty()) {
            helper.setBcc(bccEmails.stream().map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new));
        }
        helper.setSubject(subject);
        helper.setText(stripHtml(htmlBody), htmlBody);
        addAttachments(helper, attachments);
        mailSender.send(message);
        return new EmailDeliveryResult(NotificationStatus.SENT, "Gmail SMTP email sent successfully", LocalDateTime.now());
    }

    private JavaMailSender buildSmtpSender(EmailProviderSetting settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getSmtpHost());
        sender.setPort(settings.getSmtpPort() == null ? 587 : settings.getSmtpPort());
        sender.setUsername(settings.getSmtpUsername());
        sender.setPassword(secretEncryptionService.decrypt(settings.getSmtpPassword()));
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", String.valueOf(settings.isSmtpTlsEnabled()));
        properties.put("mail.smtp.starttls.required", String.valueOf(settings.isSmtpTlsEnabled()));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    private void validateSesSettings(EmailProviderSetting settings) {
        if (!hasText(settings.getSenderEmail()) || !hasText(settings.getAwsAccessKey()) || !hasText(settings.getAwsSecretKey()) || !hasText(settings.getAwsRegion())) {
            throw new IllegalStateException("AWS SES provider is missing sender email, access key, secret key, or region");
        }
        if (!hasText(secretEncryptionService.decrypt(settings.getAwsSecretKey()))) {
            throw new IllegalStateException("AWS SES secret key is unavailable");
        }
    }

    private void validateSmtpSettings(EmailProviderSetting settings) {
        if (!hasText(settings.getSenderEmail()) || !hasText(settings.getSmtpHost()) || settings.getSmtpPort() == null || !hasText(settings.getSmtpUsername()) || !hasText(settings.getSmtpPassword())) {
            throw new IllegalStateException("Gmail SMTP provider is missing sender email, host, port, username, or password");
        }
        if (!hasText(secretEncryptionService.decrypt(settings.getSmtpPassword()))) {
            throw new IllegalStateException("Gmail SMTP password is unavailable");
        }
    }

    private String normalizeProvider(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void addAttachments(MimeMessageHelper helper, List<NotificationAttachmentRequest> attachments) throws Exception {
        if (attachments == null) {
            return;
        }
        for (NotificationAttachmentRequest attachment : attachments) {
            if (attachment.getFileName() == null || attachment.getFileName().isBlank() || attachment.getBase64Content() == null || attachment.getBase64Content().isBlank()) {
                continue;
            }
            byte[] content = Base64.getDecoder().decode(attachment.getBase64Content());
            helper.addAttachment(attachment.getFileName().trim(), new ByteArrayResource(content));
        }
    }

    public EmailLogResponse toResponse(EmailLog log) {
        return EmailLogResponse.builder()
                .id(log.getId())
                .templateId(log.getTemplate() != null ? log.getTemplate().getId() : null)
                .recipientEmail(log.getRecipientEmail())
                .subject(log.getSubject())
                .emailBody(log.getEmailBody())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .sentBy(log.getSentBy())
                .sentAt(log.getSentAt())
                .build();
    }

    public record EmailDeliveryResult(NotificationStatus status, String providerResponse, LocalDateTime sentAt) {
    }
}
