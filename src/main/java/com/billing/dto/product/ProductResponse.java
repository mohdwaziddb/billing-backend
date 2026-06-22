package com.billing.dto.product;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ProductResponse {
    private Long id;
    private String name;
    private Long categoryId;
    private String categoryName;
    private String category;
    private Long subCategoryId;
    private String subCategoryName;
    private String subCategory;
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
