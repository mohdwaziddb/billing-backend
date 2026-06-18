package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyCreateRequest;
import com.billing.dto.platformadmin.PlatformAdminCompanyDetailsResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyOverviewResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyResponse;
import com.billing.dto.platformadmin.PlatformAdminDashboardResponse;
import com.billing.dto.platformadmin.PlatformAdminSettingsRequest;
import com.billing.dto.platformadmin.PlatformAdminSettingsResponse;
import com.billing.service.PlatformAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<PlatformAdminDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success("Platform dashboard fetched successfully", platformAdminService.dashboard()));
    }

    @GetMapping("/companies")
    public ResponseEntity<ApiResponse<PageResponse<PlatformAdminCompanyResponse>>> companies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Companies fetched successfully",
                platformAdminService.companies(page, size, search, active)));
    }

    @GetMapping("/companies/overview")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyOverviewResponse>> companyOverview(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Company overview fetched successfully",
                platformAdminService.companyOverview(search, active)));
    }

    @PostMapping("/companies")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyResponse>> createCompany(
            @Valid @RequestBody PlatformAdminCompanyCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company created successfully",
                platformAdminService.createCompany(request)));
    }

    @PostMapping("/companies/{companyId}/activate")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyResponse>> activateCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.success("Company activated successfully",
                platformAdminService.activateCompany(companyId)));
    }

    @PostMapping("/companies/{companyId}/deactivate")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyResponse>> deactivateCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.success("Company deactivated successfully",
                platformAdminService.deactivateCompany(companyId)));
    }

    @GetMapping("/companies/{companyId}")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyDetailsResponse>> companyDetails(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.success("Company details fetched successfully",
                platformAdminService.companyDetails(companyId)));
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<PlatformAdminSettingsResponse>> settings() {
        return ResponseEntity.ok(ApiResponse.success("Platform settings fetched successfully",
                platformAdminService.settings()));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<PlatformAdminSettingsResponse>> updateSettings(
            @RequestBody PlatformAdminSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Platform settings updated successfully",
                platformAdminService.updateSettings(request)));
    }
}
