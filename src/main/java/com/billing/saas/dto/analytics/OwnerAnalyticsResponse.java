package com.billing.saas.dto.analytics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class OwnerAnalyticsResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal totalSales;
    private BigDecimal totalCollection;
    private BigDecimal outstandingAmount;
    private long newCustomers;
    private long totalInvoices;
    private List<MetricPointResponse> salesTrend;
    private List<MetricPointResponse> collectionTrend;
    private List<MetricPointResponse> outstandingTrend;
    private List<MetricPointResponse> customerGrowthTrend;
    private List<MetricPointResponse> monthlyRevenue;
}
