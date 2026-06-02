package com.billing.saas.controller;

import com.billing.saas.dto.ApiResponse;
import com.billing.saas.dto.auth.AuthResponse;
import com.billing.saas.dto.auth.LoginRequest;
import com.billing.saas.dto.auth.RefreshTokenRequest;
import com.billing.saas.dto.auth.RegisterCompanyRequest;
import com.billing.saas.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register-company")
    public ResponseEntity<ApiResponse<AuthResponse>> registerCompany(@Valid @RequestBody RegisterCompanyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company registered successfully", authService.registerCompany(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout(@RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", Map.of("status", "ok")));
    }
}
