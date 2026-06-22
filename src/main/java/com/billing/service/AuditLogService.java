package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.audit.AuditLogResponse;
import com.billing.dto.audit.AuditUserOptionResponse;
import com.billing.entity.AuditLog;
import com.billing.entity.Company;
import com.billing.entity.User;
import com.billing.repository.AuditLogRepository;
import com.billing.exception.BadRequestException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    private final PermissionService permissionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void logCreate(String email, Company company, String moduleName, String entityName, Long entityId, Map<String, Object> newData) {
        save(email, company, moduleName, entityName, entityId, CREATE, null, newData, newData);
    }

    @Transactional
    public void logCreateAsActor(String actorName, Company company, String moduleName, String entityName, Long entityId, Map<String, Object> newData) {
        saveWithActor(null, actorName, company, moduleName, entityName, entityId, CREATE, null, newData, newData);
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
    public void logUpdateAsActor(String actorName, Company company, String moduleName, String entityName, Long entityId, Map<String, Object> oldData, Map<String, Object> newData) {
        Map<String, Object> changedFields = changedFields(oldData, newData);
        if (changedFields.isEmpty()) {
            return;
        }
        String action = isStatusOnly(changedFields) ? STATUS_CHANGE : UPDATE;
        saveWithActor(null, actorName, company, moduleName, entityName, entityId, action, oldData, newData, changedFields);
    }

    @Transactional
    public void logDelete(String email, Company company, String moduleName, String entityName, Long entityId, Map<String, Object> oldData) {
        save(email, company, moduleName, entityName, entityId, DELETE, oldData, null, oldData);
    }

    @Transactional
    public void logEvent(String email, Company company, String moduleName, String entityName, Long entityId, String actionType, Map<String, Object> data) {
        save(email, company, moduleName, entityName, entityId, actionType, null, data, data);
    }

    @Transactional
    public void logEventAsActor(String actorName, Company company, String moduleName, String entityName, Long entityId, String actionType, Map<String, Object> data) {
        saveWithActor(null, actorName, company, moduleName, entityName, entityId, actionType, null, data, data);
    }

    @Transactional
    public void logCustomUpdate(String email, Company company, String moduleName, String entityName, Long entityId, String actionType, Map<String, Object> oldData, Map<String, Object> newData) {
        Map<String, Object> changedFields = changedFields(oldData, newData);
        if (changedFields.isEmpty()) {
            return;
        }
        save(email, company, moduleName, entityName, entityId, actionType, oldData, newData, changedFields);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> page(String email,
                                               String moduleName,
                                               Long entityId,
                                               Long userId,
                                               String actionType,
                                               String startDate,
                                               String endDate,
                                               String search,
                                               int page,
                                               int size) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        requireRowLogPermission(email, moduleName);
        return PageResponse.from(auditLogRepository.findAll(
                auditFilter(company, moduleName, entityId, userId, actionType, startDate, endDate, search),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))
        ).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public List<AuditUserOptionResponse> users(String email) {
        User user = accessControlService.getCurrentUser(email);
        if (!hasLogPermission(email, "USERS")) {
            throw new AccessDeniedException("You do not have permission to view audit log users");
        }
        List<Object[]> rows = auditLogRepository.findDistinctUsersByCompany(accessControlService.requireCompany(user));
        return rows.stream()
                .map(row -> AuditUserOptionResponse.builder()
                        .id((Long) row[0])
                        .name(row[1] == null ? "User #" + row[0] : String.valueOf(row[1]))
                        .build())
                .toList();
    }

    private Specification<AuditLog> auditFilter(Company company,
                                                String moduleName,
                                                Long entityId,
                                                Long userId,
                                                String actionType,
                                                String startDate,
                                                String endDate,
                                                String search) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (company != null) {
                predicates.add(builder.equal(root.get("company"), company));
            }
            if (hasText(moduleName)) {
                predicates.add(builder.equal(builder.lower(root.get("moduleName")), moduleName.trim().toLowerCase()));
            }
            if (entityId != null) {
                predicates.add(builder.equal(root.get("entityId"), entityId));
            }
            if (userId != null) {
                predicates.add(builder.equal(root.get("userId"), userId));
            }
            if (hasText(actionType)) {
                predicates.add(builder.equal(builder.lower(root.get("actionType")), actionType.trim().toLowerCase()));
            }
            LocalDateTime from = parseDate(startDate, LocalTime.MIN);
            LocalDateTime to = parseDate(endDate, LocalTime.MAX);
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (hasText(search)) {
                String like = "%" + search.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("userName")), like),
                        builder.like(builder.lower(root.get("entityName")), like),
                        builder.like(builder.lower(root.get("oldData").as(String.class)), like),
                        builder.like(builder.lower(root.get("newData").as(String.class)), like),
                        builder.like(root.get("entityId").as(String.class), "%" + search.trim() + "%")
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
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
        saveWithActor(user.getId(), user.getFullName(), company, moduleName, entityName, entityId, actionType, oldData, newData, changedFields);
    }

    private void saveWithActor(Long userId,
                               String userName,
                               Company company,
                               String moduleName,
                               String entityName,
                               Long entityId,
                               String actionType,
                               Map<String, Object> oldData,
                               Map<String, Object> newData,
                               Map<String, Object> changedFields) {
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
                .userId(userId)
                .userName(userName)
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
                .recordName(resolveRecordName(log))
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

    private String resolveRecordName(AuditLog log) {
        Map<String, Object> newData = fromJson(log.getNewData());
        Map<String, Object> oldData = fromJson(log.getOldData());
        for (String key : List.of("product_name", "name", "customer_name", "customerName", "invoice_no", "invoiceNo", "payment_ref", "category_name", "categoryName", "subCategoryName", "sub_category_name", "expenseType", "template_name", "email", "full_name")) {
            Object value = newData.getOrDefault(key, oldData.get(key));
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return log.getEntityName() + " #" + log.getEntityId();
    }

    private Map<String, Object> fromJson(String value) {
        if (!hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private LocalDateTime parseDate(String value, LocalTime time) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atTime(time);
        } catch (RuntimeException ex) {
            throw new BadRequestException("Invalid audit log date filter");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void requireRowLogPermission(String email, String moduleName) {
        if (!hasText(moduleName)) {
            throw new BadRequestException("Module name is required to view logs");
        }
        String menuCode = switch (moduleName.trim().toLowerCase()) {
            case "product" -> "PRODUCTS";
            case "customer" -> "CUSTOMERS";
            case "invoice" -> "INVOICES";
            case "payment" -> "PAYMENTS";
            case "expense" -> "EXPENSES";
            case "expense category" -> "EXPENSE_CATEGORIES";
            case "product category" -> "PRODUCT_CATEGORY";
            case "product sub category" -> "PRODUCT_SUB_CATEGORIES";
            case "payment mode" -> "PAYMENT_MODES";
            case "user" -> "USERS";
            case "email template" -> "EMAIL_TEMPLATES";
            case "sms template" -> "SMS_TEMPLATES";
            default -> null;
        };
        if (menuCode == null || !hasLogPermission(email, menuCode)) {
            throw new AccessDeniedException("You do not have permission to view logs");
        }
    }

    private boolean hasLogPermission(String email, String menuCode) {
        return permissionService.has(email, menuCode, "LOGS") || permissionService.has(email, menuCode, "VIEW_LOGS");
    }
}
