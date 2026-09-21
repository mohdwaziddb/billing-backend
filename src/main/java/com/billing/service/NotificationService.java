package com.billing.service;

import com.billing.dto.notification.NotificationAttachmentRequest;
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
import com.billing.repository.SmsProviderSettingRepository;
import com.billing.repository.SmsTemplateRepository;
import com.billing.service.sms.CommonSmsService;
import com.billing.service.sms.SmsSendResult;
import com.billing.service.whatsapp.CommonWhatsAppService;
import com.billing.service.whatsapp.WhatsAppSendResult;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final SmsProviderSettingRepository smsProviderSettingRepository;
    private final EmailTemplateVariableService variableService;
    private final EmailService emailService;
    private final CommonSmsService commonSmsService;
    private final CommonWhatsAppService commonWhatsAppService;
    private final AuditLogService auditLogService;

    @Transactional
    public List<NotificationLogResponse> sendNotification(Map<String, Object> param, String email) {
        NotificationSendRequest notificationSendRequest = mapToSendRequest(param);
        return sendNotification(email, notificationSendRequest);
    }

    @Transactional
    public List<NotificationLogResponse> sendEmail(Map<String, Object> param, String email) {
        NotificationSendRequest notificationSendRequest = mapToSendRequest(param);
        notificationSendRequest.setChannel(NotificationChannelType.EMAIL);
        return sendNotification(email, notificationSendRequest);
    }

    @Transactional
    public List<NotificationLogResponse> sendSms(Map<String, Object> param, String email) {
        NotificationSendRequest notificationSendRequest = mapToSendRequest(param);
        notificationSendRequest.setChannel(NotificationChannelType.SMS);
        return sendNotification(email, notificationSendRequest);
    }

    @Transactional
    public List<NotificationLogResponse> sendWhatsApp(Map<String, Object> param, String email) {
        NotificationSendRequest notificationSendRequest = mapToSendRequest(param);
        notificationSendRequest.setChannel(NotificationChannelType.WHATSAPP);
        return sendNotification(email, notificationSendRequest);
    }

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
        List<String> toEmails = request.getToEmails() == null ? List.of() : request.getToEmails();
        if (toEmails.isEmpty()) {
            throw new BadRequestException("At least one recipient email is required");
        }
        Long templateId = request.getTemplateId();
        String subject;
        String body;
        if (templateId != null) {
            EmailTemplate template = emailTemplateRepository.findByIdAndCompanyAndActiveTrue(templateId, company)
                    .orElseThrow(() -> new ResourceNotFoundException("Active email template not found"));
            subject = variableService.render(template.getSubject(), company, request.getVariables());
            body = variableService.render(template.getEmailBody(), company, request.getVariables());
        } else {
            subject = request.getSubject() == null || request.getSubject().isBlank()
                    ? "Notification from " + company.getName()
                    : request.getSubject().trim();
            body = renderEmailBody(request.getMessage());
        }
        EmailService.EmailDeliveryResult result = emailService.sendEmail(company, subject, body, toEmails, safeList(request.getCcEmails()), safeList(request.getBccEmails()), request.getAttachments(), email);
        String recipient = String.join(",", toEmails);
        NotificationLog saved = saveLog(company, NotificationChannelType.EMAIL, templateId, recipient, subject, body, "EMAIL", null, null, result.providerResponse(), result.status(), email, result.sentAt());
        writeAudit(email, company, saved, result.status() == NotificationStatus.SENT ? "EMAIL_SENT" : result.status() == NotificationStatus.FAILED ? "EMAIL_FAILED" : "EMAIL_PENDING");
        return List.of(toResponse(saved));
    }

    private List<NotificationLogResponse> sendSms(String email, Company company, NotificationSendRequest request) {
        Long templateId = request.getTemplateId();
        String message;
        if (templateId != null) {
            SmsTemplate template = smsTemplateRepository.findByIdAndCompanyAndActiveTrue(templateId, company)
                    .orElseThrow(() -> new ResourceNotFoundException("Active SMS template not found"));
            message = variableService.render(template.getTemplateBody(), company, request.getVariables());
        } else {
            message = requireMessage(request.getMessage(), "SMS message is required");
        }
        List<SmsSendResult> results = commonSmsService.sendSms(company, request.getMobileNumbers(), message);
        if (results.isEmpty()) {
            throw new BadRequestException("At least one valid mobile number is required");
        }
        String providerName = smsProviderSettingRepository.findFirstByCompanyAndActiveTrueOrderByIdDesc(company)
                .map(setting -> setting.getProviderName() == null || setting.getProviderName().isBlank() ? setting.getProviderType().name() : setting.getProviderName())
                .orElse("SMS");
        return results.stream().map(result -> {
            NotificationLog saved = saveLog(company, NotificationChannelType.SMS, templateId, result.mobileNumber(), null, message, providerName, null, result.status() == NotificationStatus.FAILED ? result.providerResponse() : null, result.providerResponse(), result.status(), email, result.sentAt());
            writeAudit(email, company, saved, result.status() == NotificationStatus.SENT ? "SMS_SENT" : result.status() == NotificationStatus.FAILED ? "SMS_FAILED" : "SMS_PENDING");
            return toResponse(saved);
        }).toList();
    }

    private List<NotificationLogResponse> sendWhatsApp(String email, Company company, NotificationSendRequest request) {
        String message = requireMessage(request.getMessage(), "WhatsApp message is required");
        List<WhatsAppSendResult> results;
        if (request.getAttachments() == null || request.getAttachments().isEmpty()) {
            results = commonWhatsAppService.sendMessage(company, request.getMobileNumbers(), message);
        } else if (request.getAttachments().stream().anyMatch(attachment -> attachment.getContentType() != null && attachment.getContentType().toLowerCase().contains("pdf"))) {
            results = commonWhatsAppService.sendDocument(company, request.getMobileNumbers(), message, request.getAttachments());
        } else {
            results = commonWhatsAppService.sendMedia(company, request.getMobileNumbers(), message, request.getAttachments());
        }
        if (results.isEmpty()) {
            throw new BadRequestException("At least one valid mobile number is required");
        }
        return results.stream().map(result -> {
            NotificationLog saved = saveLog(company, NotificationChannelType.WHATSAPP, request.getTemplateId(), result.mobileNumber(), null, message, result.providerName(), result.messageId(), result.failureReason(), result.providerResponse(), result.status(), email, result.sentAt());
            writeAudit(email, company, saved, result.status() == NotificationStatus.SENT ? "WHATSAPP_SENT" : result.status() == NotificationStatus.FAILED ? "WHATSAPP_FAILED" : "WHATSAPP_PENDING");
            return toResponse(saved);
        }).toList();
    }

    private String renderEmailBody(String message) {
        String value = requireMessage(message, "Email body is required");
        if (value.contains("<") && value.contains(">")) {
            return value;
        }
        return "<p>" + value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>") + "</p>";
    }

    private String requireMessage(String message, String errorMessage) {
        if (message == null || message.isBlank()) {
            throw new BadRequestException(errorMessage);
        }
        return message.trim();
    }

    @Transactional(readOnly = true)
    public com.billing.dto.PageResponse<NotificationLogResponse> logs(Map<String, Object> param, String email) {
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return logs(email, page, size);
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
                                    String providerName,
                                    String messageId,
                                    String failureReason,
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
                .providerName(providerName)
                .messageId(messageId)
                .failureReason(failureReason)
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
        data.put("provider_name", log.getProviderName());
        data.put("message_id", log.getMessageId());
        data.put("failure_reason", log.getFailureReason());
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
                .providerName(log.getProviderName())
                .messageId(log.getMessageId())
                .failureReason(log.getFailureReason())
                .providerResponse(log.getProviderResponse())
                .status(log.getStatus())
                .sentBy(log.getSentBy())
                .sentAt(log.getSentAt())
                .build();
    }

    private NotificationSendRequest mapToSendRequest(Map<String, Object> param) {
        NotificationSendRequest notificationSendRequest = new NotificationSendRequest();
        String channelStr = DataTypeUtility.stringValue(param.get("channel"));
        if (channelStr.length() > 0) {
            try {
                notificationSendRequest.setChannel(NotificationChannelType.valueOf(channelStr.toUpperCase()));
            } catch (Exception ignore) {
                notificationSendRequest.setChannel(null);
            }
        }
        Long templateId = DataTypeUtility.getForeignKeyValue(param.get("templateId"));
        notificationSendRequest.setTemplateId(templateId);
        String subject = DataTypeUtility.stringValue(param.get("subject"));
        if (subject.length() == 0) {
            subject = null;
        }
        notificationSendRequest.setSubject(subject);
        String message = DataTypeUtility.stringValue(param.get("message"));
        if (message.length() == 0) {
            message = null;
        }
        notificationSendRequest.setMessage(message);
        Object toEmailsObj = param.get("toEmails");
        if (toEmailsObj instanceof List) {
            List<String> toEmails = new ArrayList<>();
            for (Object emailObj : (List<?>) toEmailsObj) {
                String emailValue = DataTypeUtility.stringValue(emailObj);
                if (emailValue.length() > 0) {
                    toEmails.add(emailValue);
                }
            }
            notificationSendRequest.setToEmails(toEmails);
        } else {
            String singleEmail = DataTypeUtility.stringValue(param.get("toEmail"));
            if (singleEmail.length() > 0) {
                notificationSendRequest.setToEmails(List.of(singleEmail));
            }
        }
        Object ccEmailsObj = param.get("ccEmails");
        if (ccEmailsObj instanceof List) {
            List<String> ccEmails = new ArrayList<>();
            for (Object emailObj : (List<?>) ccEmailsObj) {
                String emailValue = DataTypeUtility.stringValue(emailObj);
                if (emailValue.length() > 0) {
                    ccEmails.add(emailValue);
                }
            }
            notificationSendRequest.setCcEmails(ccEmails);
        }
        Object bccEmailsObj = param.get("bccEmails");
        if (bccEmailsObj instanceof List) {
            List<String> bccEmails = new ArrayList<>();
            for (Object emailObj : (List<?>) bccEmailsObj) {
                String emailValue = DataTypeUtility.stringValue(emailObj);
                if (emailValue.length() > 0) {
                    bccEmails.add(emailValue);
                }
            }
            notificationSendRequest.setBccEmails(bccEmails);
        }
        Object mobileNumbersObj = param.get("mobileNumbers");
        if (mobileNumbersObj instanceof List) {
            List<String> mobileNumbers = new ArrayList<>();
            for (Object mobileObj : (List<?>) mobileNumbersObj) {
                String mobileValue = DataTypeUtility.stringValue(mobileObj);
                if (mobileValue.length() > 0) {
                    mobileNumbers.add(mobileValue);
                }
            }
            notificationSendRequest.setMobileNumbers(mobileNumbers);
        } else {
            String singleMobile = DataTypeUtility.stringValue(param.get("mobileNumber"));
            if (singleMobile.length() > 0) {
                notificationSendRequest.setMobileNumbers(List.of(singleMobile));
            }
        }
        Object variablesObj = param.get("variables");
        if (variablesObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> variablesMap = (Map<String, Object>) variablesObj;
            notificationSendRequest.setVariables(variablesMap);
        }
        return notificationSendRequest;
    }
}
