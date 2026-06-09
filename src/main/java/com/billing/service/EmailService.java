package com.billing.service;

import com.billing.dto.email.EmailLogResponse;
import com.billing.dto.email.EmailSendRequest;
import com.billing.entity.Company;
import com.billing.entity.EmailLog;
import com.billing.entity.EmailTemplate;
import com.billing.repository.EmailLogRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailLogRepository emailLogRepository;
    private final EmailTemplateVariableService variableService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public EmailLogResponse sendTemplateEmail(String email, Company company, EmailTemplate template, EmailSendRequest request) {
        String subject = variableService.render(template.getSubject(), company, request.getVariables());
        String body = variableService.render(template.getEmailBody(), company, request.getVariables());
        String status = "Pending";
        String errorMessage = null;
        LocalDateTime sentAt = null;

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender != null) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
                helper.setTo(request.getRecipientEmail().trim());
                helper.setSubject(subject);
                helper.setText(body, true);
                mailSender.send(message);
                status = "Sent";
                sentAt = LocalDateTime.now();
            } catch (Exception ex) {
                status = "Failed";
                errorMessage = ex.getMessage();
                sentAt = LocalDateTime.now();
            }
        }

        EmailLog log = EmailLog.builder()
                .company(company)
                .template(template)
                .recipientEmail(request.getRecipientEmail().trim())
                .subject(subject)
                .emailBody(body)
                .status(status)
                .errorMessage(errorMessage)
                .sentBy(email)
                .sentAt(sentAt)
                .build();

        return toResponse(emailLogRepository.save(log));
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
}
