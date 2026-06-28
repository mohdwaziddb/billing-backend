package com.billing.dto.product;

import com.billing.dto.inventory.ProductBatchSummaryResponse;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
    private Long taxMasterId;
    private String taxName;
    private String taxCode;
    private String taxType;
    private BigDecimal sellingPrice;
    private Integer stockQty;
    private BigDecimal inventoryValue;
    private Integer minStockQty;
    private BigDecimal taxPercent;
    private boolean taxable;
    private boolean active;
    private List<ProductBatchSummaryResponse> batches;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
