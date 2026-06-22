package com.billing.dto.productsubcategory;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProductSubCategoryResponse {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String subCategoryName;
    private String description;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
