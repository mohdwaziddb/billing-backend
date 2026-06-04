package com.billing.saas.dto.customer;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class CustomerSummaryMetrics {
    private BigDecimal totalPurchaseAmount;
    private BigDecimal totalPaidAmount;
    private BigDecimal totalDiscountGiven;
    private BigDecimal outstandingBalance;
    private LocalDate lastPurchaseDate;
    private boolean hasPurchaseHistory;
}
