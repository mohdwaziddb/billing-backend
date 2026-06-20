package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.user.CompanyUserRequest;
import com.billing.dto.user.UserProfileResponse;
import com.billing.entity.enums.RoleName;
import com.billing.security.RequirePermission;
import com.billing.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", userService.getProfile(authentication.getName())));
    }

    @GetMapping("/active-referrers")
    @RequirePermission(menu = "CREATE_INVOICE", action = "ADD")
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> activeReferrers(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Active referral users fetched successfully",
                userService.activeReferralUsers(authentication.getName())));
    }

    @GetMapping
    @RequirePermission(menu = "USERS", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<UserProfileResponse>>> listCompanyUsers(Authentication authentication,
                                                                                          @RequestParam(defaultValue = "0") int page,
                                                                                          @RequestParam(defaultValue = "20") int size,
                                                                                          @RequestParam(required = false) String name,
                                                                                          @RequestParam(required = false) String username,
                                                                                          @RequestParam(required = false) String mobileNumber,
                                                                                          @RequestParam(required = false) String email,
                                                                                          @RequestParam(required = false) String search,
                                                                                          @RequestParam(required = false) RoleName role,
                                                                                          @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Users fetched successfully",
                userService.pageCompanyUsers(authentication.getName(), page, size, name, username, mobileNumber, email, search, role, active)));
    }

    @PostMapping
    @RequirePermission(menu = "USERS", action = "ADD")
    public ResponseEntity<ApiResponse<UserProfileResponse>> createCompanyUser(Authentication authentication,
                                                                              @Valid @RequestBody CompanyUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User created successfully",
                userService.createCompanyUser(authentication.getName(), request)));
    }

    @PutMapping("/{userId}")
    @RequirePermission(menu = "USERS", action = "EDIT")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateCompanyUser(Authentication authentication,
                                                                              @PathVariable Long userId,
                                                                              @Valid @RequestBody CompanyUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User updated successfully",
                userService.updateCompanyUser(authentication.getName(), userId, request)));
    }

    @DeleteMapping("/{userId}")
    @RequirePermission(menu = "USERS", action = "DELETE")
    public ResponseEntity<ApiResponse<Map<String, String>>> deactivateCompanyUser(Authentication authentication,
                                                                                 @PathVariable Long userId) {
        userService.deactivateCompanyUser(authentication.getName(), userId);
        return ResponseEntity.ok(ApiResponse.success("User deactivated successfully", Map.of("status", "ok")));
    }
}
