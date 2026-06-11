package com.billing.dto.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class HierarchyMetricsResponse {
    private long totalCustomers;
    private long totalInvoices;
    private long totalPayments;
    private long totalProducts;
    private long totalUsers;
    private BigDecimal totalRevenue;
    private BigDecimal totalCollection;
    private BigDecimal outstandingAmount;
    private BigDecimal averageInvoiceValue;
    private LocalDateTime lastActivityDate;
}
