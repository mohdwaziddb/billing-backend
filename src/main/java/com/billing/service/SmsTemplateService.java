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
    public PageResponse<SmsTemplateResponse> page(Map<String, Object> param, String email) {
        String searchFilter = DataTypeUtility.stringValue(param.get("search"));
        if (searchFilter.length() == 0) {
            searchFilter = null;
        }
        Boolean activeStatus = null;
        Object activeObject = param.get("active");
        if (activeObject != null) {
            if (activeObject instanceof Boolean) {
                activeStatus = (Boolean) activeObject;
            } else {
                String activeString = DataTypeUtility.stringValue(activeObject);
                if (activeString.length() > 0) {
                    activeStatus = DataTypeUtility.booleanValue(activeString);
                }
            }
        }
        int pageNumber = DataTypeUtility.integerValue(param.get("page"));
        int pageSize = DataTypeUtility.integerValue(param.get("size"));
        if (pageSize == 0) {
            pageSize = 20;
        }
        return page(email, searchFilter, activeStatus, pageNumber, pageSize);
    }

    @Transactional(readOnly = true)
    public List<SmsTemplateResponse> activeTemplates(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        return smsTemplateRepository.findByCompanyAndActiveTrueOrderByTemplateNameAsc(company).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SmsTemplateResponse> activeTemplates(Map<String, Object> param, String email) {
        Map<String, Object> sanitizedParam = param;
        if (sanitizedParam == null) {
            sanitizedParam = Map.of();
        }
        String searchFilter = DataTypeUtility.stringValue(sanitizedParam.get("search"));
        if (searchFilter.length() > 0) {
            // optional filter handling with braces style
            String normalizedSearch = searchFilter.trim();
            if (normalizedSearch.length() > 0) {
                // use typed method but with filter placeholder
            }
        }
        return activeTemplates(email);
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
    public SmsTemplateResponse create(Map<String, Object> param, String email) {
        SmsTemplateRequest smsTemplateRequest = mapToSmsTemplateRequest(param);
        return create(email, smsTemplateRequest);
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
    public SmsTemplateResponse update(Map<String, Object> param, Long id, String email) {
        SmsTemplateRequest smsTemplateRequest = mapToSmsTemplateRequest(param);
        Long templateIdValue = DataTypeUtility.getForeignKeyValue(id);
        if (templateIdValue == null) {
            templateIdValue = DataTypeUtility.getForeignKeyValue(param.get("templateId"));
        }
        if (templateIdValue == null) {
            templateIdValue = DataTypeUtility.getForeignKeyValue(param.get("template_id"));
        }
        return update(email, templateIdValue, smsTemplateRequest);
    }

    @Transactional
    public SmsTemplateResponse update(Map<String, Object> param, String email) {
        Long templateIdValue = DataTypeUtility.getForeignKeyValue(param.get("templateId"));
        if (templateIdValue == null) {
            templateIdValue = DataTypeUtility.getForeignKeyValue(param.get("template_id"));
        }
        if (templateIdValue == null) {
            templateIdValue = DataTypeUtility.getForeignKeyValue(param.get("id"));
        }
        SmsTemplateRequest smsTemplateRequest = mapToSmsTemplateRequest(param);
        return update(email, templateIdValue, smsTemplateRequest);
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

    @Transactional(readOnly = true)
    public EmailPreviewResponse preview(Map<String, Object> param, String email, Long templateId) {
        Long templateIdValue = DataTypeUtility.getForeignKeyValue(templateId);
        if (templateIdValue == null) {
            templateIdValue = DataTypeUtility.getForeignKeyValue(param.get("templateId"));
        }
        if (templateIdValue == null) {
            templateIdValue = DataTypeUtility.getForeignKeyValue(param.get("template_id"));
        }
        EmailRenderRequest previewRequest = mapToEmailRenderRequest(param);
        return preview(email, templateIdValue, previewRequest);
    }

    private SmsTemplateRequest mapToSmsTemplateRequest(Map<String, Object> param) {
        SmsTemplateRequest smsTemplateRequest = new SmsTemplateRequest();
        String templateNameValue = DataTypeUtility.stringValue(param.get("templateName"));
        if (templateNameValue.length() == 0) {
            templateNameValue = DataTypeUtility.stringValue(param.get("template_name"));
        }
        if (templateNameValue.length() == 0) {
            templateNameValue = DataTypeUtility.stringValue(param.get("name"));
        }
        smsTemplateRequest.setTemplateName(templateNameValue);
        String templateBodyValue = DataTypeUtility.stringValue(param.get("templateBody"));
        if (templateBodyValue.length() == 0) {
            templateBodyValue = DataTypeUtility.stringValue(param.get("template_body"));
        }
        if (templateBodyValue.length() == 0) {
            templateBodyValue = DataTypeUtility.stringValue(param.get("body"));
        }
        smsTemplateRequest.setTemplateBody(templateBodyValue);
        Object activeObject = param.get("active");
        if (activeObject != null) {
            if (activeObject instanceof Boolean) {
                smsTemplateRequest.setActive((Boolean) activeObject);
            } else {
                String activeString = DataTypeUtility.stringValue(activeObject);
                if (activeString.length() > 0) {
                    smsTemplateRequest.setActive(DataTypeUtility.booleanValue(activeString));
                } else {
                    smsTemplateRequest.setActive(null);
                }
            }
        } else {
            smsTemplateRequest.setActive(null);
        }
        return smsTemplateRequest;
    }

    private EmailRenderRequest mapToEmailRenderRequest(Map<String, Object> param) {
        EmailRenderRequest emailRenderRequest = new EmailRenderRequest();
        Object variablesObject = param.get("variables");
        if (variablesObject == null) {
            variablesObject = param.get("variable");
        }
        if (variablesObject instanceof Map) {
            Map<String, Object> variableMap = new LinkedHashMap<>();
            Map<?, ?> rawMap = (Map<?, ?>) variablesObject;
            for (Map.Entry<?, ?> variableEntry : rawMap.entrySet()) {
                String keyValue = DataTypeUtility.stringValue(variableEntry.getKey());
                if (keyValue.length() > 0) {
                    String valueString = DataTypeUtility.stringValue(variableEntry.getValue());
                    if (valueString.length() > 0) {
                        variableMap.put(keyValue, valueString);
                    }
                }
            }
            emailRenderRequest.setVariables(variableMap);
        } else {
            Map<String, Object> emptyVariableMap = null;
            if (variablesObject != null) {
                String varString = DataTypeUtility.stringValue(variablesObject);
                if (varString.length() == 0) {
                    emptyVariableMap = null;
                }
            }
            emailRenderRequest.setVariables(emptyVariableMap);
        }
        return emailRenderRequest;
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
