package com.billing.dto.gst;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class GstCustomerWiseRowResponse {
    private Long customerId;
    private String customerName;
    private String customerGstin;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal grandTotal;
    private long invoiceCount;
}
