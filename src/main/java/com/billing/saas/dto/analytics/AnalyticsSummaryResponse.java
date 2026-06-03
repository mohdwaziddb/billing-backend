package com.billing.saas.dto.analytics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class AnalyticsSummaryResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal todaySales;
    private BigDecimal yesterdaySales;
    private BigDecimal thisMonthSales;
    private BigDecimal lastMonthSales;
    private BigDecimal totalSales;
    private BigDecimal totalCollection;
    private BigDecimal totalOutstandingBalance;
    private long newCustomers;
    private long totalInvoices;
    private long lowStockProducts;
    private long dueCustomers;
    private BigDecimal salesTrendPercentage;
    private SalesTrendStatus trendStatus;
}
