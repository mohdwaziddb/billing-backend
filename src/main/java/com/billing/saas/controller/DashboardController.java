package com.billing.saas.controller;

import com.billing.saas.dto.ApiResponse;
import com.billing.saas.dto.dashboard.DashboardSummaryResponse;
import com.billing.saas.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> summary(Authentication authentication,
                                                                         @RequestParam(required = false) LocalDate startDate,
                                                                         @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary fetched successfully",
                dashboardService.summary(authentication.getName(), startDate, endDate)));
    }
}
