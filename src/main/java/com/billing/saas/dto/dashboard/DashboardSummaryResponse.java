package com.billing.saas.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class DashboardSummaryResponse {
    private long totalInvoices;
    private long totalProducts;
    private BigDecimal totalRevenue;
    private BigDecimal outstandingBalance;
}
