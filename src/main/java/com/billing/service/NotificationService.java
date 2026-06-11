package com.billing.service;

import com.billing.dto.notification.NotificationChannelType;
import com.billing.dto.notification.NotificationLogResponse;
import com.billing.dto.notification.NotificationSendRequest;
import com.billing.dto.notification.NotificationStatus;
import com.billing.entity.Company;
import com.billing.entity.EmailTemplate;
import com.billing.entity.NotificationLog;
import com.billing.entity.SmsTemplate;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.EmailTemplateRepository;
import com.billing.repository.NotificationLogRepository;
import com.billing.repository.SmsTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AccessControlService accessControlService;
    private final EmailTemplateRepository emailTemplateRepository;
    private final SmsTemplateRepository smsTemplateRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailTemplateVariableService variableService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final WhatsAppService whatsAppService;
    private final AuditLogService auditLogService;

    @Transactional
    public List<NotificationLogResponse> sendNotification(String email, NotificationSendRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        if (request.getChannel() == null) {
            throw new BadRequestException("Notification channel is required");
        }
        return switch (request.getChannel()) {
            case EMAIL -> sendEmail(email, company, request);
            case SMS -> sendSms(email, company, request);
            case WHATSAPP -> sendWhatsApp(email, company, request);
        };
    }

    private List<NotificationLogResponse> sendEmail(String email, Company company, NotificationSendRequest request) {
        EmailTemplate template = emailTemplateRepository.findByIdAndCompanyAndActiveTrue(request.getTemplateId(), company)
                .orElseThrow(() -> new ResourceNotFoundException("Active email template not found"));
        String subject = variableService.render(template.getSubject(), company, request.getVariables());
        String body = variableService.render(template.getEmailBody(), company, request.getVariables());
        List<String> toEmails = request.getToEmails() == null ? List.of() : request.getToEmails();
        if (toEmails.isEmpty()) {
            throw new BadRequestException("At least one recipient email is required");
        }
        EmailService.EmailDeliveryResult result = emailService.sendEmail(company, subject, body, toEmails, safeList(request.getCcEmails()), safeList(request.getBccEmails()), request.getAttachments(), email);
        String recipient = String.join(",", toEmails);
        NotificationLog saved = saveLog(company, NotificationChannelType.EMAIL, template.getId(), recipient, subject, body, result.providerResponse(), result.status(), email, result.sentAt());
        writeAudit(email, company, saved, result.status() == NotificationStatus.SENT ? "EMAIL_SENT" : result.status() == NotificationStatus.FAILED ? "EMAIL_FAILED" : "EMAIL_PENDING");
        return List.of(toResponse(saved));
    }

    private List<NotificationLogResponse> sendSms(String email, Company company, NotificationSendRequest request) {
        SmsTemplate template = smsTemplateRepository.findByIdAndCompanyAndActiveTrue(request.getTemplateId(), company)
                .orElseThrow(() -> new ResourceNotFoundException("Active SMS template not found"));
        String message = variableService.render(template.getTemplateBody(), company, request.getVariables());
        List<SmsService.SmsDeliveryResult> results = smsService.sendSms(company, request.getMobileNumbers(), message);
        if (results.isEmpty()) {
            throw new BadRequestException("At least one valid mobile number is required");
        }
        return results.stream().map(result -> {
            NotificationLog saved = saveLog(company, NotificationChannelType.SMS, template.getId(), result.mobileNumber(), null, message, result.providerResponse(), result.status(), email, result.sentAt());
            writeAudit(email, company, saved, result.status() == NotificationStatus.SENT ? "SMS_SENT" : result.status() == NotificationStatus.FAILED ? "SMS_FAILED" : "SMS_PENDING");
            return toResponse(saved);
        }).toList();
    }

    private List<NotificationLogResponse> sendWhatsApp(String email, Company company, NotificationSendRequest request) {
        WhatsAppService.WhatsAppDeliveryResult result = whatsAppService.sendWhatsApp();
        NotificationLog saved = saveLog(company, NotificationChannelType.WHATSAPP, request.getTemplateId(), "", null, "", result.providerResponse(), result.status(), email, result.sentAt());
        return List.of(toResponse(saved));
    }

    @Transactional(readOnly = true)
    public com.billing.dto.PageResponse<NotificationLogResponse> logs(String email, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        return com.billing.dto.PageResponse.from(notificationLogRepository.findByCompanyOrderBySentAtDescCreatedAtDesc(company, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))))
                .map(this::toResponse));
    }

    private NotificationLog saveLog(Company company,
                                    NotificationChannelType channel,
                                    Long templateId,
                                    String recipient,
                                    String subject,
                                    String message,
                                    String providerResponse,
                                    NotificationStatus status,
                                    String sentBy,
                                    LocalDateTime sentAt) {
        return notificationLogRepository.save(NotificationLog.builder()
                .company(company)
                .channel(channel.name())
                .templateId(templateId)
                .recipient(recipient == null ? "" : recipient)
                .subject(subject)
                .message(message)
                .providerResponse(providerResponse)
                .status(status.name())
                .sentBy(sentBy)
                .sentAt(sentAt)
                .build());
    }

    private void writeAudit(String email, Company company, NotificationLog log, String action) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channel", log.getChannel());
        data.put("template_id", log.getTemplateId());
        data.put("recipient", log.getRecipient());
        data.put("status", log.getStatus());
        auditLogService.logEvent(email, company, "Notification", "NotificationLog", log.getId(), action, data);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private NotificationLogResponse toResponse(NotificationLog log) {
        return NotificationLogResponse.builder()
                .id(log.getId())
                .channel(log.getChannel())
                .templateId(log.getTemplateId())
                .recipient(log.getRecipient())
                .subject(log.getSubject())
                .message(log.getMessage())
                .providerResponse(log.getProviderResponse())
                .status(log.getStatus())
                .sentBy(log.getSentBy())
                .sentAt(log.getSentAt())
                .build();
    }
}
