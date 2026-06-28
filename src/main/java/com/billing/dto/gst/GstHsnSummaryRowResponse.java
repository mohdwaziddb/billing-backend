package com.billing.dto.gst;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class GstHsnSummaryRowResponse {
    private String hsnCode;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal grandAmount;
    private long totalQuantity;
    private long lineCount;
}
