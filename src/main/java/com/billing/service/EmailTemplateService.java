package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.email.EmailLogResponse;
import com.billing.dto.email.EmailPreviewResponse;
import com.billing.dto.email.EmailRenderRequest;
import com.billing.dto.email.EmailSendRequest;
import com.billing.dto.email.EmailTemplateRequest;
import com.billing.dto.email.EmailTemplateResponse;
import com.billing.entity.Company;
import com.billing.entity.EmailTemplate;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.EmailLogRepository;
import com.billing.repository.EmailTemplateRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final AccessControlService accessControlService;
    private final EmailTemplateRepository emailTemplateRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailTemplateVariableService variableService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;

    @Transactional(readOnly = true)
    public PageResponse<EmailTemplateResponse> page(String email, String search, Boolean active, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        return PageResponse.from(emailTemplateRepository.findPageByCompanyWithFilters(company, active, normalizeSearch(search), pageRequest(page, size))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<EmailTemplateResponse> page(Map<String, Object> param, String email) {
        String search = DataTypeUtility.stringValue(param.get("search"));
        if (search.length() == 0) {
            search = null;
        }
        Boolean active = null;
        Object activeObj = param.get("active");
        if (activeObj != null) {
            String activeStr = DataTypeUtility.stringValue(activeObj);
            if (activeStr.length() > 0) {
                active = DataTypeUtility.booleanValue(activeObj);
            }
        }
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return page(email, search, active, page, size);
    }

    @Transactional(readOnly = true)
    public List<EmailTemplateResponse> activeTemplates(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        return emailTemplateRepository.findByCompanyAndActiveTrueOrderByTemplateNameAsc(company).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmailTemplateResponse get(String email, Long templateId) {
        Company company = accessControlService.getCurrentCompany(email);
        return toResponse(getTemplateOrThrow(company, templateId));
    }

    @Transactional
    public EmailTemplateResponse create(Map<String, Object> param, String email) {
        EmailTemplateRequest emailTemplateRequest = mapToTemplateRequest(param);
        return create(email, emailTemplateRequest);
    }

    @Transactional
    public EmailTemplateResponse create(String email, EmailTemplateRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        String templateName = required(request.getTemplateName(), "Template name is required");
        if (emailTemplateRepository.existsByCompanyAndTemplateNameIgnoreCase(company, templateName)) {
            throw new BadRequestException("Email template already exists");
        }
        EmailTemplate template = EmailTemplate.builder()
                .company(company)
                .templateName(templateName)
                .subject(required(request.getSubject(), "Subject is required"))
                .emailBody(required(request.getEmailBody(), "Email body is required"))
                .active(request.getActive() == null || Boolean.TRUE.equals(request.getActive()))
                .build();
        EmailTemplate saved = emailTemplateRepository.save(template);
        auditLogService.logCreate(email, company, "Email Template", "EmailTemplate", saved.getId(), snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public EmailTemplateResponse update(Map<String, Object> param, Long templateId, String email) {
        EmailTemplateRequest emailTemplateRequest = mapToTemplateRequest(param);
        return update(email, templateId, emailTemplateRequest);
    }

    @Transactional
    public EmailTemplateResponse update(String email, Long templateId, EmailTemplateRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        EmailTemplate template = getTemplateOrThrow(company, templateId);
        Map<String, Object> oldData = snapshot(template);
        String templateName = required(request.getTemplateName(), "Template name is required");
        if (emailTemplateRepository.existsByCompanyAndTemplateNameIgnoreCaseAndIdNot(company, templateName, templateId)) {
            throw new BadRequestException("Email template already exists");
        }
        template.setTemplateName(templateName);
        template.setSubject(required(request.getSubject(), "Subject is required"));
        template.setEmailBody(required(request.getEmailBody(), "Email body is required"));
        template.setActive(request.getActive() == null || Boolean.TRUE.equals(request.getActive()));
        EmailTemplate saved = emailTemplateRepository.save(template);
        auditLogService.logUpdate(email, company, "Email Template", "EmailTemplate", saved.getId(), oldData, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(String email, Long templateId) {
        Company company = accessControlService.getCurrentCompany(email);
        EmailTemplate template = getTemplateOrThrow(company, templateId);
        Map<String, Object> oldData = snapshot(template);
        template.setActive(false);
        EmailTemplate saved = emailTemplateRepository.save(template);
        auditLogService.logDelete(email, company, "Email Template", "EmailTemplate", saved.getId(), oldData);
    }

    @Transactional(readOnly = true)
    public EmailPreviewResponse preview(Map<String, Object> param, Long templateId, String email) {
        EmailRenderRequest emailRenderRequest = new EmailRenderRequest();
        Object variablesObj = param.get("variables");
        if (variablesObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> variablesMap = (Map<String, Object>) variablesObj;
            emailRenderRequest.setVariables(variablesMap);
        } else {
            Object variablesData = param.get("variablesData");
            if (variablesData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> variablesMap = (Map<String, Object>) variablesData;
                emailRenderRequest.setVariables(variablesMap);
            }
        }
        return preview(email, templateId, emailRenderRequest);
    }

    @Transactional(readOnly = true)
    public EmailPreviewResponse preview(String email, Long templateId, EmailRenderRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        EmailTemplate template = getTemplateOrThrow(company, templateId);
        Map<String, Object> variables = request == null ? null : request.getVariables();
        return EmailPreviewResponse.builder()
                .subject(variableService.render(template.getSubject(), company, variables))
                .emailBody(variableService.render(template.getEmailBody(), company, variables))
                .build();
    }

    @Transactional
    public EmailLogResponse send(Map<String, Object> param, String email) {
        EmailSendRequest emailSendRequest = mapToSendRequest(param);
        return send(email, emailSendRequest);
    }

    @Transactional
    public EmailLogResponse send(String email, EmailSendRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        EmailTemplate template = emailTemplateRepository.findByIdAndCompanyAndActiveTrue(request.getTemplateId(), company)
                .orElseThrow(() -> new ResourceNotFoundException("Active email template not found"));
        return emailService.sendTemplateEmail(email, company, template, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmailLogResponse> logs(Map<String, Object> param, String email) {
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return logs(email, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<EmailLogResponse> logs(String email, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        return PageResponse.from(emailLogRepository.findByCompanyOrderBySentAtDescCreatedAtDesc(company, pageRequest(page, size))
                .map(emailService::toResponse));
    }

    @Transactional(readOnly = true)
    public Map<String, String> variables() {
        return EmailTemplateVariableService.AVAILABLE_VARIABLES;
    }

    private EmailTemplateRequest mapToTemplateRequest(Map<String, Object> param) {
        EmailTemplateRequest emailTemplateRequest = new EmailTemplateRequest();
        String templateName = DataTypeUtility.stringValue(param.get("templateName"));
        if (templateName.length() == 0) {
            templateName = null;
        }
        emailTemplateRequest.setTemplateName(templateName);
        String subject = DataTypeUtility.stringValue(param.get("subject"));
        if (subject.length() == 0) {
            subject = null;
        }
        emailTemplateRequest.setSubject(subject);
        String emailBody = DataTypeUtility.stringValue(param.get("emailBody"));
        if (emailBody.length() == 0) {
            emailBody = null;
        }
        emailTemplateRequest.setEmailBody(emailBody);
        Object activeObj = param.get("active");
        Boolean active = null;
        if (activeObj != null) {
            String activeStr = DataTypeUtility.stringValue(activeObj);
            if (activeStr.length() > 0) {
                active = DataTypeUtility.booleanValue(activeObj);
            }
        }
        emailTemplateRequest.setActive(active);
        return emailTemplateRequest;
    }

    private EmailSendRequest mapToSendRequest(Map<String, Object> param) {
        EmailSendRequest emailSendRequest = new EmailSendRequest();
        Long templateId = DataTypeUtility.getForeignKeyValue(param.get("templateId"));
        emailSendRequest.setTemplateId(templateId);
        String recipientEmail = DataTypeUtility.stringValue(param.get("recipientEmail"));
        if (recipientEmail.length() == 0) {
            recipientEmail = null;
        }
        emailSendRequest.setRecipientEmail(recipientEmail);
        Object variablesObj = param.get("variables");
        if (variablesObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> variablesMap = (Map<String, Object>) variablesObj;
            emailSendRequest.setVariables(variablesMap);
        }
        return emailSendRequest;
    }

    private EmailTemplate getTemplateOrThrow(Company company, Long templateId) {
        return emailTemplateRepository.findByIdAndCompany(templateId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Email template not found"));
    }

    private EmailTemplateResponse toResponse(EmailTemplate template) {
        return EmailTemplateResponse.builder()
                .id(template.getId())
                .templateName(template.getTemplateName())
                .subject(template.getSubject())
                .emailBody(template.getEmailBody())
                .active(template.isActive())
                .createdBy(auditNameResolver.displayName(template.getCreatedBy()))
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private Map<String, Object> snapshot(EmailTemplate template) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("templateName", template.getTemplateName());
        data.put("subject", template.getSubject());
        data.put("emailBody", template.getEmailBody());
        data.put("active", template.isActive());
        return data;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
    }
}
