package com.billing.dto.gst;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class GstTaxWiseRowResponse {
    private Long taxMasterId;
    private String taxName;
    private String taxCode;
    private String taxType;
    private BigDecimal taxRate;
    private BigDecimal taxableAmount;
    private BigDecimal cgstAmount;
    private BigDecimal sgstAmount;
    private BigDecimal igstAmount;
    private BigDecimal totalTaxAmount;
    private BigDecimal grandAmount;
    private long lineCount;
}
