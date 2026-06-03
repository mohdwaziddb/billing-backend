package com.billing.saas.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class DashboardSummaryResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalSales;
    private BigDecimal totalCollection;
    private BigDecimal outstandingAmount;
    private long newCustomers;
    private long totalInvoices;
    private long totalProducts;
    private BigDecimal totalRevenue;
    private BigDecimal outstandingBalance;
    private List<DashboardTopCustomerResponse> topCustomers;
}
