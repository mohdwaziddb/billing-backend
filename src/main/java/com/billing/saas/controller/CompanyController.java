package com.billing.saas.controller;

import com.billing.saas.dto.ApiResponse;
import com.billing.saas.dto.company.CompanySettingsRequest;
import com.billing.saas.dto.user.CompanySummary;
import com.billing.saas.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CompanySummary>> settings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Company settings fetched successfully",
                companyService.getSettings(authentication.getName())));
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<CompanySummary>> update(Authentication authentication,
                                                              @Valid @RequestBody CompanySettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company settings updated successfully",
                companyService.updateSettings(authentication.getName(), request)));
    }
}
