package com.billing.service;

import com.billing.core.appconfig.ProviderMessages;
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
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
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
import java.util.ArrayList;
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
                .providerName(activeEmailProviderName(company))
                .senderEmail(activeEmailSender(company))
                .status(result.status().name())
                .errorMessage(result.providerResponse())
                .sentBy(email)
                .sentAt(result.sentAt())
                .build();

        return toResponse(emailLogRepository.save(log));
    }

    private String activeEmailProviderName(Company company) {
        try {
            return emailProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company)
                    .map(setting -> {
                        String providerName = normalizeProvider(setting.getProviderName());
                        return providerName.isEmpty() ? null : providerName;
                    })
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String activeEmailSender(Company company) {
        try {
            return emailProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company)
                    .map(setting -> {
                        String senderEmail = setting.getSenderEmail();
                        return senderEmail == null || senderEmail.isBlank() ? null : senderEmail.trim();
                    })
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
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
            return new EmailDeliveryResult(NotificationStatus.PENDING, ProviderMessages.EMAIL_NOT_CONFIGURED, null);
        }
        try {
            String providerName = normalizeProvider(settings.getProviderName());
            if ("AWS_SES".equals(providerName)) {
                return sendWithSes(settings, subject, htmlBody, toEmails, ccEmails, bccEmails, attachments);
            }
            if ("SENDGRID".equals(providerName)) {
                return sendWithSendGrid(settings, subject, htmlBody, toEmails, ccEmails, bccEmails, attachments);
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

    /**
     * Company footer for outgoing mails (OTP, test mails, ...): name in bold
     * plus one muted contact line composed from whatever the company profile
     * has. Blank fields are skipped — no empty lines, no "null".
     */
    public String companySignatureHtml(Company company) {
        if (company == null) {
            return "";
        }
        String name = blankOrNull(company.getName());
        List<String> contactParts = new ArrayList<>();
        addSignaturePart(contactParts, company.getPhone());
        addSignaturePart(contactParts, company.getEmail());
        List<String> addressParts = new ArrayList<>();
        addSignaturePart(addressParts, company.getAddressLine1());
        addSignaturePart(addressParts, company.getAddressLine2());
        addSignaturePart(addressParts, company.getCity());
        addSignaturePart(addressParts, company.getState());
        addSignaturePart(addressParts, company.getPincode());
        addSignaturePart(addressParts, company.getCountry());
        StringBuilder footer = new StringBuilder("<hr><p>");
        if (name != null) {
            footer.append("<b>").append(escapeSignatureHtml(name)).append("</b>");
        }
        if (!contactParts.isEmpty()) {
            if (name != null) {
                footer.append("<br>");
            }
            footer.append("<small>").append(escapeSignatureHtml(String.join(" • ", contactParts))).append("</small>");
        }
        List<String> extraParts = new ArrayList<>();
        addSignaturePart(extraParts, company.getWebsiteUrl());
        if (company.getGstin() != null && !company.getGstin().isBlank()) {
            extraParts.add("GSTIN: " + company.getGstin().trim());
        }
        if (!addressParts.isEmpty()) {
            extraParts.add(String.join(", ", addressParts));
        }
        if (!extraParts.isEmpty()) {
            footer.append("<br><small>").append(escapeSignatureHtml(String.join(" • ", extraParts))).append("</small>");
        }
        footer.append("</p>");
        String built = footer.toString();
        return "<hr><p></p>".equals(built) ? "" : built;
    }

    private static void addSignaturePart(List<String> parts, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    private static String blankOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escapeSignatureHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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

    private EmailDeliveryResult sendWithSendGrid(EmailProviderSetting settings,
                                                 String subject,
                                                 String htmlBody,
                                                 List<String> toEmails,
                                                 List<String> ccEmails,
                                                 List<String> bccEmails,
                                                 List<NotificationAttachmentRequest> attachments) throws Exception {
        validateSendGridSettings(settings);

        Mail mail = new Mail();
        mail.setFrom(new Email(settings.getSenderEmail().trim()));
        mail.setSubject(subject);
        mail.addContent(new Content("text/plain", stripHtml(htmlBody)));
        mail.addContent(new Content("text/html", htmlBody == null ? "" : htmlBody));

        Personalization personalization = new Personalization();
        addSendGridRecipients(personalization::addTo, toEmails);
        addSendGridRecipients(personalization::addCc, ccEmails);
        addSendGridRecipients(personalization::addBcc, bccEmails);
        mail.addPersonalization(personalization);
        addSendGridAttachments(mail, attachments);

        SendGrid sendGrid = new SendGrid(secretEncryptionService.decrypt(settings.getSendgridApiKey()));
        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response response = sendGrid.api(request);
        int statusCode = response.getStatusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return new EmailDeliveryResult(NotificationStatus.SENT, "SendGrid email sent successfully", LocalDateTime.now());
        }
        throw new IllegalStateException("SendGrid returned status " + statusCode + (hasText(response.getBody()) ? ": " + response.getBody() : ""));
    }

    private JavaMailSender buildSmtpSender(EmailProviderSetting settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getSmtpHost());
        sender.setPort(settings.getSmtpPort() == null ? 587 : settings.getSmtpPort());
        sender.setUsername(settings.getSmtpUsername());
        sender.setPassword(secretEncryptionService.decrypt(settings.getSmtpPassword()));
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        boolean smtpTlsEnabled = !Boolean.FALSE.equals(settings.getSmtpTlsEnabled());
        properties.put("mail.smtp.starttls.enable", String.valueOf(smtpTlsEnabled));
        properties.put("mail.smtp.starttls.required", String.valueOf(smtpTlsEnabled));
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

    private void validateSendGridSettings(EmailProviderSetting settings) {
        if (!hasText(settings.getSenderEmail()) || !hasText(settings.getSendgridApiKey())) {
            throw new IllegalStateException("SendGrid provider is missing sender email or API key");
        }
        if (!hasText(secretEncryptionService.decrypt(settings.getSendgridApiKey()))) {
            throw new IllegalStateException("SendGrid API key is unavailable");
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

    private void addSendGridRecipients(java.util.function.Consumer<Email> consumer, List<String> recipients) {
        if (recipients == null) {
            return;
        }
        recipients.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(this::hasText)
                .map(Email::new)
                .forEach(consumer);
    }

    private void addAttachments(MimeMessageHelper helper, List<NotificationAttachmentRequest> attachments) throws Exception {
        for (NotificationAttachmentRequest attachment : validAttachments(attachments)) {
            helper.addAttachment(
                    resolveFileName(attachment),
                    new ByteArrayResource(decodeAttachment(attachment)),
                    resolveContentType(attachment));
        }
    }

    private void addSendGridAttachments(Mail mail, List<NotificationAttachmentRequest> attachments) {
        for (NotificationAttachmentRequest attachment : validAttachments(attachments)) {
            Attachments sendGridAttachment = new Attachments();
            sendGridAttachment.setFilename(resolveFileName(attachment));
            sendGridAttachment.setContent(attachment.getBase64Content().trim());
            sendGridAttachment.setDisposition("attachment");
            sendGridAttachment.setType(resolveContentType(attachment));
            mail.addAttachments(sendGridAttachment);
        }
    }

    private List<NotificationAttachmentRequest> validAttachments(List<NotificationAttachmentRequest> attachments) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream()
                .filter(attachment -> attachment != null
                        && hasText(attachment.getFileName())
                        && hasText(attachment.getBase64Content())
                        && hasText(resolveFileName(attachment)))
                .toList();
    }

    private String resolveFileName(NotificationAttachmentRequest attachment) {
        return attachment.getFileName().replaceAll("[\\r\\n]", "").trim();
    }

    private String resolveContentType(NotificationAttachmentRequest attachment) {
        String contentType = attachment.getContentType();
        if (!hasText(contentType)) {
            return detectContentType(attachment.getFileName());
        }
        return contentType.replaceAll("[\\r\\n]", "").trim();
    }

    private byte[] decodeAttachment(NotificationAttachmentRequest attachment) {
        try {
            return Base64.getDecoder().decode(attachment.getBase64Content().trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Attachment '" + resolveFileName(attachment) + "' has invalid base64 content", ex);
        }
    }

    private String detectContentType(String fileName) {
        if (!hasText(fileName)) {
            return "application/octet-stream";
        }
        String lowerCaseFileName = fileName.trim().toLowerCase();
        if (lowerCaseFileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerCaseFileName.endsWith(".png")) {
            return "image/png";
        }
        if (lowerCaseFileName.endsWith(".jpg") || lowerCaseFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerCaseFileName.endsWith(".txt")) {
            return "text/plain; charset=UTF-8";
        }
        if (lowerCaseFileName.endsWith(".csv")) {
            return "text/csv; charset=UTF-8";
        }
        if (lowerCaseFileName.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        return "application/octet-stream";
    }

    public EmailLogResponse toResponse(EmailLog log) {
        return EmailLogResponse.builder()
                .id(log.getId())
                .templateId(log.getTemplate() != null ? log.getTemplate().getId() : null)
                .recipientEmail(log.getRecipientEmail())
                .subject(log.getSubject())
                .emailBody(log.getEmailBody())
                .providerName(log.getProviderName())
                .senderEmail(log.getSenderEmail())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .sentBy(log.getSentBy())
                .sentAt(log.getSentAt())
                .build();
    }

    public record EmailDeliveryResult(NotificationStatus status, String providerResponse, LocalDateTime sentAt) {
    }
}
