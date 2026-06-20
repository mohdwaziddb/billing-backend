package com.billing.dto.referral;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class SalesReferralInvoiceResponse {
    private Long invoiceId;
    private String invoiceNo;
    private String customerName;
    private String referByUserName;
    private String referByUserMobileNumber;
    private LocalDate invoiceDate;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private BigDecimal outstandingAmount;
}
