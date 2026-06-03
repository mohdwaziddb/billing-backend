package com.billing.saas.controller;

import com.billing.saas.dto.ApiResponse;
import com.billing.saas.dto.analytics.AnalyticsSummaryResponse;
import com.billing.saas.dto.analytics.CustomerDueResponse;
import com.billing.saas.dto.analytics.LowStockProductResponse;
import com.billing.saas.dto.analytics.OwnerAnalyticsResponse;
import com.billing.saas.dto.analytics.SalesChartPointResponse;
import com.billing.saas.dto.analytics.TopSellingProductResponse;
import com.billing.saas.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<AnalyticsSummaryResponse>> summary(Authentication authentication,
                                                                         @RequestParam(required = false) LocalDate startDate,
                                                                         @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success("Analytics summary fetched successfully",
                analyticsService.summary(authentication.getName(), startDate, endDate)));
    }

    @GetMapping("/owner-overview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<OwnerAnalyticsResponse>> ownerOverview(Authentication authentication,
                                                                             @RequestParam(required = false) LocalDate startDate,
                                                                             @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success("Owner analytics fetched successfully",
                analyticsService.ownerOverview(authentication.getName(), startDate, endDate)));
    }

    @GetMapping("/day-wise-sales")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<SalesChartPointResponse>>> dayWiseSales(
            Authentication authentication,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        LocalDate now = LocalDate.now();
        int targetYear = year != null ? year : now.getYear();
        int targetMonth = month != null ? month : now.getMonthValue();
        return ResponseEntity.ok(ApiResponse.success("Day wise sales fetched successfully",
                analyticsService.dayWiseSales(authentication.getName(), targetYear, targetMonth)));
    }

    @GetMapping("/month-wise-sales")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<SalesChartPointResponse>>> monthWiseSales(
            Authentication authentication,
            @RequestParam(required = false) Integer year
    ) {
        int targetYear = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success("Month wise sales fetched successfully",
                analyticsService.monthWiseSales(authentication.getName(), targetYear)));
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<TopSellingProductResponse>>> topProducts(
            Authentication authentication,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success("Top selling products fetched successfully",
                analyticsService.topSellingProducts(authentication.getName(), limit)));
    }

    @GetMapping("/low-stock-products")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<LowStockProductResponse>>> lowStockProducts(
            Authentication authentication,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success("Low stock products fetched successfully",
                analyticsService.lowStockProducts(authentication.getName(), limit)));
    }

    @GetMapping("/customer-due-list")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<CustomerDueResponse>>> customerDueList(
            Authentication authentication,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success("Customer due list fetched successfully",
                analyticsService.customerDueList(authentication.getName(), limit)));
    }
}
