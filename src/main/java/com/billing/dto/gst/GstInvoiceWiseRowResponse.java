package com.billing.dto.gst;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class GstInvoiceWiseRowResponse {
    private Long invoiceId;
    private String invoiceNo;
    private LocalDate invoiceDate;
    private Long customerId;
    private String customerName;
    private String customerState;
    private Long customerStateId;
    private String customerGstin;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal grandTotal;
}
