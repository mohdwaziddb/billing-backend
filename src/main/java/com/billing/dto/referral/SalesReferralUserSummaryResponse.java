package com.billing.dto.referral;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class SalesReferralUserSummaryResponse {
    private Long userId;
    private String userName;
    private String username;
    private long totalInvoices;
    private BigDecimal totalRevenue;
    private BigDecimal paidRevenue;
    private BigDecimal outstandingRevenue;
    private BigDecimal averageInvoiceValue;
    private List<SalesReferralInvoiceResponse> invoices;
}
