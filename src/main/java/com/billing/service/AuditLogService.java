package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.audit.AuditLogResponse;
import com.billing.entity.AuditLog;
import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String STATUS_CHANGE = "STATUS_CHANGE";

    private final AuditLogRepository auditLogRepository;
    private final AccessControlService accessControlService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void logCreate(String email, Company company, String moduleName, String entityName, Long entityId, Map<String, Object> newData) {
        save(email, company, moduleName, entityName, entityId, CREATE, null, newData, newData);
    }

    @Transactional
    public void logUpdate(String email, Company company, String moduleName, String entityName, Long entityId, Map<String, Object> oldData, Map<String, Object> newData) {
        Map<String, Object> changedFields = changedFields(oldData, newData);
        if (changedFields.isEmpty()) {
            return;
        }
        String action = isStatusOnly(changedFields) ? STATUS_CHANGE : UPDATE;
        save(email, company, moduleName, entityName, entityId, action, oldData, newData, changedFields);
    }

    @Transactional
    public void logDelete(String email, Company company, String moduleName, String entityName, Long entityId, Map<String, Object> oldData) {
        save(email, company, moduleName, entityName, entityId, DELETE, oldData, null, oldData);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> page(String email, String moduleName, Long entityId, int page, int size) {
        User user = accessControlService.getCurrentUser(email);
        if (user.getRole() != RoleName.OWNER && user.getRole() != RoleName.ADMIN) {
            throw new BadRequestException("You do not have permission to view audit logs");
        }
        Company company = accessControlService.requireCompany(user);
        return PageResponse.from(auditLogRepository.findByCompanyAndModuleNameAndEntityIdOrderByCreatedAtDescIdDesc(
                company,
                moduleName,
                entityId,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(this::toResponse));
    }

    private void save(String email,
                      Company company,
                      String moduleName,
                      String entityName,
                      Long entityId,
                      String actionType,
                      Map<String, Object> oldData,
                      Map<String, Object> newData,
                      Map<String, Object> changedFields) {
        User user = accessControlService.getCurrentUser(email);
        HttpServletRequest request = currentRequest();
        auditLogRepository.save(AuditLog.builder()
                .company(company)
                .moduleName(moduleName)
                .entityName(entityName)
                .entityId(entityId)
                .actionType(actionType)
                .oldData(toJson(oldData))
                .newData(toJson(newData))
                .changedFields(toJson(changedFields))
                .userId(user.getId())
                .userName(user.getFullName())
                .ipAddress(resolveIpAddress(request))
                .userAgent(request != null ? request.getHeader("User-Agent") : null)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public Map<String, Object> changedFields(Map<String, Object> oldData, Map<String, Object> newData) {
        Map<String, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : newData.entrySet()) {
            Object oldValue = oldData == null ? null : oldData.get(entry.getKey());
            Object newValue = entry.getValue();
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("old", oldValue);
                values.put("new", newValue);
                changed.put(entry.getKey(), values);
            }
        }
        return changed;
    }

    private boolean isStatusOnly(Map<String, Object> changedFields) {
        return changedFields.size() == 1 && (changedFields.containsKey("active") || changedFields.containsKey("status") || changedFields.containsKey("paymentStatus"));
    }

    private String toJson(Map<String, Object> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Unable to serialize audit data");
        }
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .moduleName(log.getModuleName())
                .entityName(log.getEntityName())
                .entityId(log.getEntityId())
                .actionType(log.getActionType())
                .oldData(log.getOldData())
                .newData(log.getNewData())
                .changedFields(log.getChangedFields())
                .userId(log.getUserId())
                .userName(log.getUserName())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
