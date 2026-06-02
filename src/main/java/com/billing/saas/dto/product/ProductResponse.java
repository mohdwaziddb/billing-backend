package com.billing.saas.dto.product;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private String category;
    private String brand;
    private String sku;
    private String hsnCode;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;
    private Integer stockQty;
    private Integer minStockQty;
    private BigDecimal taxPercent;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
