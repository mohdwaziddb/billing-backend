package com.billing.dto.purchase;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PurchaseItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Integer qty;
    private BigDecimal purchaseRate;
    private BigDecimal sellingRate;
    private BigDecimal lineTotal;
    private Long batchId;
    private String batchNo;
}
