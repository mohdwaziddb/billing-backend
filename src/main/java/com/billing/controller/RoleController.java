package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.repository.RoleMasterRepository;
import com.billing.security.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private static final List<String> COMPANY_ROLE_CODES = List.of("OWNER", "ADMIN", "USER");

    private final RoleMasterRepository roleMasterRepository;

    @GetMapping
    @RequiresPermission(menu = "ROLE_PERMISSIONS", action = "VIEW")
    public ResponseEntity<ApiResponse<List<String>>> roles() {
        return ResponseEntity.ok(ApiResponse.success("Roles fetched successfully",
                roleMasterRepository.findAll().stream()
                        .map(role -> role.getRoleCode())
                        .filter(COMPANY_ROLE_CODES::contains)
                        .toList()));
    }
}
