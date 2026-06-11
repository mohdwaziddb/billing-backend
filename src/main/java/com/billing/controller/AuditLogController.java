package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.audit.AuditLogResponse;
import com.billing.dto.audit.AuditUserOptionResponse;
import com.billing.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<AuditUserOptionResponse>>> users(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Audit log users fetched successfully", auditLogService.users(authentication.getName())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> list(Authentication authentication,
                                                                           @RequestParam(required = false) String moduleName,
                                                                           @RequestParam(required = false) Long entityId,
                                                                           @RequestParam(required = false) Long userId,
                                                                           @RequestParam(required = false) String actionType,
                                                                           @RequestParam(required = false) String startDate,
                                                                           @RequestParam(required = false) String endDate,
                                                                           @RequestParam(required = false) String search,
                                                                           @RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Audit logs fetched successfully",
                auditLogService.page(authentication.getName(), moduleName, entityId, userId, actionType, startDate, endDate, search, page, size)));
    }
}
