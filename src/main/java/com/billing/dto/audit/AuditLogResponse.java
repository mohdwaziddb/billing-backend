package com.billing.dto.audit;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AuditLogResponse {
    private Long id;
    private String moduleName;
    private String entityName;
    private Long entityId;
    private String actionType;
    private String oldData;
    private String newData;
    private String changedFields;
    private Long userId;
    private String userName;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}
