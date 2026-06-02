package com.billing.saas.dto.analytics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class TopSellingProductResponse {
    private Long productId;
    private String productName;
    private String sku;
    private Integer totalQtySold;
    private BigDecimal totalSalesAmount;
    private Integer currentStockQty;
}
