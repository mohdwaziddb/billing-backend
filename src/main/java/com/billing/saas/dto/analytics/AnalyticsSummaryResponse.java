package com.billing.saas.dto.analytics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AnalyticsSummaryResponse {
    private BigDecimal todaySales;
    private BigDecimal yesterdaySales;
    private BigDecimal thisMonthSales;
    private BigDecimal lastMonthSales;
    private BigDecimal totalOutstandingBalance;
    private long lowStockProducts;
    private long dueCustomers;
    private BigDecimal salesTrendPercentage;
    private SalesTrendStatus trendStatus;
}
