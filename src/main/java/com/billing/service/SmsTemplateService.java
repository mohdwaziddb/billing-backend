package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.email.EmailPreviewResponse;
import com.billing.dto.email.EmailRenderRequest;
import com.billing.dto.notification.SmsTemplateRequest;
import com.billing.dto.notification.SmsTemplateResponse;
import com.billing.entity.Company;
import com.billing.entity.SmsTemplate;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.SmsTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SmsTemplateService {

    private final AccessControlService accessControlService;
    private final SmsTemplateRepository smsTemplateRepository;
    private final EmailTemplateVariableService variableService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;

    @Transactional(readOnly = true)
    public PageResponse<SmsTemplateResponse> page(String email, String search, Boolean active, int page, int size) {
        Company company = accessControlService.getCurrentCompany(email);
        return PageResponse.from(smsTemplateRepository.findPageByCompanyWithFilters(company, active, normalizeSearch(search), PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))))
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<SmsTemplateResponse> activeTemplates(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        return smsTemplateRepository.findByCompanyAndActiveTrueOrderByTemplateNameAsc(company).stream().map(this::toResponse).toList();
    }

    @Transactional
    public SmsTemplateResponse create(String email, SmsTemplateRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        String name = required(request.getTemplateName(), "Template name is required");
        if (smsTemplateRepository.existsByCompanyAndTemplateNameIgnoreCase(company, name)) {
            throw new BadRequestException("SMS template already exists");
        }
        SmsTemplate saved = smsTemplateRepository.save(SmsTemplate.builder()
                .company(company)
                .templateName(name)
                .templateBody(required(request.getTemplateBody(), "Template body is required"))
                .active(request.getActive() == null || Boolean.TRUE.equals(request.getActive()))
                .build());
        auditLogService.logCreate(email, company, "SMS Template", "SmsTemplate", saved.getId(), snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public SmsTemplateResponse update(String email, Long id, SmsTemplateRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        SmsTemplate template = smsTemplateRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> new ResourceNotFoundException("SMS template not found"));
        Map<String, Object> oldData = snapshot(template);
        String name = required(request.getTemplateName(), "Template name is required");
        if (smsTemplateRepository.existsByCompanyAndTemplateNameIgnoreCaseAndIdNot(company, name, id)) {
            throw new BadRequestException("SMS template already exists");
        }
        template.setTemplateName(name);
        template.setTemplateBody(required(request.getTemplateBody(), "Template body is required"));
        template.setActive(request.getActive() == null || Boolean.TRUE.equals(request.getActive()));
        SmsTemplate saved = smsTemplateRepository.save(template);
        auditLogService.logUpdate(email, company, "SMS Template", "SmsTemplate", saved.getId(), oldData, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(String email, Long id) {
        Company company = accessControlService.getCurrentCompany(email);
        SmsTemplate template = smsTemplateRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> new ResourceNotFoundException("SMS template not found"));
        Map<String, Object> oldData = snapshot(template);
        template.setActive(false);
        SmsTemplate saved = smsTemplateRepository.save(template);
        auditLogService.logDelete(email, company, "SMS Template", "SmsTemplate", saved.getId(), oldData);
    }

    @Transactional(readOnly = true)
    public EmailPreviewResponse preview(String email, Long id, EmailRenderRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        SmsTemplate template = smsTemplateRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> new ResourceNotFoundException("SMS template not found"));
        return EmailPreviewResponse.builder()
                .subject("")
                .emailBody(variableService.render(template.getTemplateBody(), company, request == null ? null : request.getVariables()))
                .build();
    }

    private SmsTemplateResponse toResponse(SmsTemplate template) {
        return SmsTemplateResponse.builder()
                .id(template.getId())
                .templateName(template.getTemplateName())
                .templateBody(template.getTemplateBody())
                .active(template.isActive())
                .createdBy(auditNameResolver.displayName(template.getCreatedBy()))
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private Map<String, Object> snapshot(SmsTemplate template) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("template_name", template.getTemplateName());
        data.put("template_body", template.getTemplateBody());
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
}
