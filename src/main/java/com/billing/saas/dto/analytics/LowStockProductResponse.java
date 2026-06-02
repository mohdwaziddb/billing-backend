package com.billing.saas.dto.analytics;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LowStockProductResponse {
    private Long productId;
    private String productName;
    private String sku;
    private Integer stockQty;
    private Integer minStockQty;
    private boolean active;
}
