package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.audit.AuditLogResponse;
import com.billing.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> list(Authentication authentication,
                                                                           @RequestParam String moduleName,
                                                                           @RequestParam Long entityId,
                                                                           @RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Audit logs fetched successfully", auditLogService.page(authentication.getName(), moduleName, entityId, page, size)));
    }
}
