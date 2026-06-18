package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.auth.PlatformAdminAuthResponse;
import com.billing.dto.auth.PlatformAdminLoginRequest;
import com.billing.service.PlatformAdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin")
@RequiredArgsConstructor
public class PlatformAdminAuthController {

    private final PlatformAdminAuthService platformAdminAuthService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<PlatformAdminAuthResponse>> login(@Valid @RequestBody PlatformAdminLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Platform admin login successful", platformAdminAuthService.login(request)));
    }
}
