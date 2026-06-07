package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.analytics.AnalyticsSummaryResponse;
import com.billing.dto.analytics.CustomerDueResponse;
import com.billing.dto.analytics.LowStockProductResponse;
import com.billing.dto.analytics.OwnerAnalyticsResponse;
import com.billing.dto.analytics.SalesByCategoryResponse;
import com.billing.dto.analytics.SalesChartPointResponse;
import com.billing.dto.analytics.TopSellingProductResponse;
import com.billing.security.RequirePermission;
import com.billing.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    @RequirePermission(menu = "ANALYTICS", action = "VIEW")
    public ResponseEntity<ApiResponse<AnalyticsSummaryResponse>> summary(Authentication authentication,
                                                                         @RequestParam(required = false) LocalDate startDate,
                                                                         @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success("Analytics summary fetched successfully",
                analyticsService.summary(authentication.getName(), startDate, endDate)));
    }

    @GetMapping("/owner-overview")
    @RequirePermission(menu = "ANALYTICS", action = "VIEW")
    public ResponseEntity<ApiResponse<OwnerAnalyticsResponse>> ownerOverview(Authentication authentication,
                                                                             @RequestParam(required = false) LocalDate startDate,
                                                                             @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success("Owner analytics fetched successfully",
                analyticsService.ownerOverview(authentication.getName(), startDate, endDate)));
    }

    @GetMapping("/day-wise-sales")
    @RequirePermission(menu = "ANALYTICS", action = "VIEW")
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
    @RequirePermission(menu = "ANALYTICS", action = "VIEW")
    public ResponseEntity<ApiResponse<List<SalesChartPointResponse>>> monthWiseSales(
            Authentication authentication,
            @RequestParam(required = false) Integer year
    ) {
        int targetYear = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(ApiResponse.success("Month wise sales fetched successfully",
                analyticsService.monthWiseSales(authentication.getName(), targetYear)));
    }

    @GetMapping("/top-products")
    @RequirePermission(menu = "ANALYTICS", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<TopSellingProductResponse>>> topProducts(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Top selling products fetched successfully",
                analyticsService.topSellingProducts(authentication.getName(), page, size)));
    }

    @GetMapping("/sales-by-category")
    @RequirePermission(menu = "ANALYTICS", action = "VIEW")
    public ResponseEntity<ApiResponse<List<SalesByCategoryResponse>>> salesByCategory(
            Authentication authentication,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success("Sales by category fetched successfully",
                analyticsService.salesByCategory(authentication.getName(), startDate, endDate, limit)));
    }

    @GetMapping("/low-stock-products")
    @RequirePermission(menu = "ANALYTICS", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<LowStockProductResponse>>> lowStockProducts(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Low stock products fetched successfully",
                analyticsService.lowStockProducts(authentication.getName(), page, size)));
    }

    @GetMapping("/customer-due-list")
    @RequirePermission(menu = "ANALYTICS", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<CustomerDueResponse>>> customerDueList(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Customer due list fetched successfully",
                analyticsService.customerDueList(authentication.getName(), page, size)));
    }
}
