package com.billing.saas.dto.invoice;

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
    private BigDecimal taxPercent;
    private BigDecimal lineTotal;
}
