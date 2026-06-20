package com.billing.dto.referral;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class SalesReferralReportResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private long totalReferredInvoices;
    private BigDecimal totalReferredRevenue;
    private BigDecimal thisMonthReferredRevenue;
    private SalesReferralUserSummaryResponse topPerformer;
    private List<SalesReferralUserSummaryResponse> users;
    private List<SalesReferralUserSummaryResponse> topContributors;
    private List<SalesReferralInvoiceResponse> referredInvoices;
    private List<SalesReferralInvoiceResponse> thisMonthInvoices;
}
