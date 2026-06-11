package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.report.HierarchyNodeResponse;
import com.billing.security.RequirePermission;
import com.billing.service.ManagementHierarchyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/management-hierarchy")
@RequiredArgsConstructor
public class ManagementHierarchyController {

    private final ManagementHierarchyService managementHierarchyService;

    @GetMapping("/roots")
    @RequirePermission(menu = "MANAGEMENT_HIERARCHY", action = "VIEW")
    public ResponseEntity<ApiResponse<List<HierarchyNodeResponse>>> roots(Authentication authentication,
                                                                          @RequestParam(required = false) String role,
                                                                          @RequestParam(required = false) String status,
                                                                          @RequestParam(required = false) String search,
                                                                          @RequestParam(required = false) String startDate,
                                                                          @RequestParam(required = false) String endDate,
                                                                          @RequestParam(required = false) String manager) {
        return ResponseEntity.ok(ApiResponse.success("Hierarchy roots fetched successfully",
                managementHierarchyService.roots(authentication.getName(), role, status, search, startDate, endDate, manager)));
    }

    @GetMapping("/{parentId}/children")
    @RequirePermission(menu = "MANAGEMENT_HIERARCHY", action = "VIEW")
    public ResponseEntity<ApiResponse<List<HierarchyNodeResponse>>> children(Authentication authentication,
                                                                             @PathVariable Long parentId,
                                                                             @RequestParam(required = false) String role,
                                                                             @RequestParam(required = false) String status,
                                                                             @RequestParam(required = false) String search,
                                                                             @RequestParam(required = false) String startDate,
                                                                             @RequestParam(required = false) String endDate,
                                                                             @RequestParam(required = false) String manager) {
        return ResponseEntity.ok(ApiResponse.success("Hierarchy children fetched successfully",
                managementHierarchyService.children(authentication.getName(), parentId, role, status, search, startDate, endDate, manager)));
    }
}
