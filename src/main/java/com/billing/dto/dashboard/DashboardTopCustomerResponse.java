package com.billing.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class DashboardTopCustomerResponse {
    private Long customerId;
    private String customerName;
    private String mobile;
    private BigDecimal totalPurchaseAmount;
    private BigDecimal totalPaidAmount;
    private BigDecimal outstandingBalance;
    private LocalDate lastPurchaseDate;
}
