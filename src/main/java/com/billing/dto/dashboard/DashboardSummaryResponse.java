package com.billing.dto.dashboard;

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
    private long totalCustomers;
    private long newCustomers;
    private long existingCustomers;
    private long totalInvoices;
    private long totalProducts;
    private BigDecimal totalRevenue;
    private BigDecimal outstandingBalance;
    private BigDecimal totalSalesTrendPercentage;
    private BigDecimal collectionTrendPercentage;
    private BigDecimal outstandingTrendPercentage;
    private BigDecimal totalCustomersTrendPercentage;
    private List<DashboardTopCustomerResponse> topCustomers;
}
