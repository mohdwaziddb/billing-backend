package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.dashboard.DashboardDetailResponse;
import com.billing.dto.dashboard.DashboardSummaryResponse;
import com.billing.security.RequiresPermission;
import com.billing.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @RequiresPermission(menu = "DASHBOARD", action = "VIEW")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> summary(Authentication authentication,
                                                                         @RequestParam(required = false) LocalDate startDate,
                                                                         @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success("Dashboard summary fetched successfully",
                dashboardService.summary(authentication.getName(), startDate, endDate)));
    }

    @GetMapping("/details")
    @RequiresPermission(menu = "DASHBOARD", action = "VIEW")
    public ResponseEntity<ApiResponse<DashboardDetailResponse>> details(Authentication authentication,
                                                                        @RequestParam String card,
                                                                        @RequestParam(required = false) LocalDate startDate,
                                                                        @RequestParam(required = false) LocalDate endDate,
                                                                        @RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size,
                                                                        @RequestParam(defaultValue = "date") String sortBy,
                                                                        @RequestParam(defaultValue = "desc") String sortDirection,
                                                                        @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success("Dashboard details fetched successfully",
                dashboardService.details(authentication.getName(), card, startDate, endDate, page, size, sortBy, sortDirection, search)));
    }
}

