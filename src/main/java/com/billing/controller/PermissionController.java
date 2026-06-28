package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.permission.PermissionMatrixRequest;
import com.billing.dto.permission.PermissionMatrixResponse;
import com.billing.security.RequiresPermission;
import com.billing.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PermissionMatrixResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Permissions fetched successfully", permissionService.effectivePermissions(authentication.getName())));
    }

    @GetMapping("/my-menus")
    public ResponseEntity<ApiResponse<PermissionMatrixResponse>> myMenus(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Menus fetched successfully", permissionService.effectivePermissions(authentication.getName())));
    }

    @GetMapping("/role-matrix")
    @RequiresPermission(menu = "ROLE_PERMISSIONS", action = "VIEW")
    public ResponseEntity<ApiResponse<PermissionMatrixResponse>> roleMatrix(Authentication authentication,
                                                                            @RequestParam String roleCode) {
        return ResponseEntity.ok(ApiResponse.success("Role permissions fetched successfully", permissionService.roleMatrix(authentication.getName(), roleCode)));
    }

    @PostMapping("/role-matrix")
    @RequiresPermission(menu = "ROLE_PERMISSIONS", action = "EDIT")
    public ResponseEntity<ApiResponse<PermissionMatrixResponse>> saveRoleMatrix(Authentication authentication,
                                                                                @Valid @RequestBody PermissionMatrixRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Role permissions saved successfully", permissionService.saveRoleMatrix(authentication.getName(), request)));
    }

    @GetMapping("/user-matrix")
    @RequiresPermission(menu = "ROLE_PERMISSIONS", action = "VIEW")
    public ResponseEntity<ApiResponse<PermissionMatrixResponse>> userMatrix(Authentication authentication,
                                                                            @RequestParam Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User permissions fetched successfully", permissionService.userMatrix(authentication.getName(), userId)));
    }

    @PostMapping("/user-matrix")
    @RequiresPermission(menu = "ROLE_PERMISSIONS", action = "EDIT")
    public ResponseEntity<ApiResponse<PermissionMatrixResponse>> saveUserMatrix(Authentication authentication,
                                                                                @Valid @RequestBody PermissionMatrixRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User permissions saved successfully", permissionService.saveUserMatrix(authentication.getName(), request)));
    }
}
