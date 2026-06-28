package com.billing.dto.invoice;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class InvoiceItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Integer qty;
    private BigDecimal price;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;
    private Long taxMasterId;
    private String taxName;
    private BigDecimal taxRate;
    private String hsnCode;
    private BigDecimal taxableAmount;
    private BigDecimal cgstRate;
    private BigDecimal cgstAmount;
    private BigDecimal sgstRate;
    private BigDecimal sgstAmount;
    private BigDecimal igstRate;
    private BigDecimal igstAmount;
    private BigDecimal taxPercent;
    private BigDecimal netAmount;
    private BigDecimal grandAmount;
    private BigDecimal lineTotal;
}
