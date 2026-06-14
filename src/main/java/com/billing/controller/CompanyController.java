package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.company.CompanySettingsRequest;
import com.billing.dto.company.CompanyThemeRequest;
import com.billing.dto.company.CompanyThemeResponse;
import com.billing.dto.user.CompanySummary;
import com.billing.security.RequirePermission;
import com.billing.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @RequirePermission(menu = "ABOUT_COMPANY", action = "VIEW")
    public ResponseEntity<ApiResponse<CompanySummary>> settings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Company settings fetched successfully",
                companyService.getSettings(authentication.getName())));
    }

    @PutMapping
    @RequirePermission(menu = "ABOUT_COMPANY", action = "EDIT")
    public ResponseEntity<ApiResponse<CompanySummary>> update(Authentication authentication,
                                                              @Valid @RequestBody CompanySettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company settings updated successfully",
                companyService.updateSettings(authentication.getName(), request)));
    }

    @PutMapping("/logo")
    @RequirePermission(menu = "ABOUT_COMPANY", action = "EDIT")
    public ResponseEntity<ApiResponse<CompanySummary>> uploadLogo(Authentication authentication,
                                                                  @RequestParam("logo") MultipartFile logo) {
        return ResponseEntity.ok(ApiResponse.success("Company logo uploaded successfully",
                companyService.uploadLogo(authentication.getName(), logo)));
    }

    @DeleteMapping("/logo")
    @RequirePermission(menu = "ABOUT_COMPANY", action = "EDIT")
    public ResponseEntity<ApiResponse<CompanySummary>> deleteLogo(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Company logo removed successfully",
                companyService.deleteLogo(authentication.getName())));
    }

    @GetMapping("/theme")
    public ResponseEntity<ApiResponse<CompanyThemeResponse>> theme(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Company theme fetched successfully",
                companyService.theme(authentication.getName())));
    }

    @PutMapping("/theme")
    @RequirePermission(menu = "THEME_SETTINGS", action = "EDIT")
    public ResponseEntity<ApiResponse<CompanyThemeResponse>> updateTheme(Authentication authentication,
                                                                        @Valid @RequestBody CompanyThemeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company theme updated successfully",
                companyService.updateTheme(authentication.getName(), request)));
    }

    @PostMapping("/theme/reset")
    @RequirePermission(menu = "THEME_SETTINGS", action = "EDIT")
    public ResponseEntity<ApiResponse<CompanyThemeResponse>> resetTheme(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Company theme reset successfully",
                companyService.resetTheme(authentication.getName())));
    }
}
