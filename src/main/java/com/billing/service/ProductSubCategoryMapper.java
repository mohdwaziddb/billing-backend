package com.billing.service;

import com.billing.dto.productsubcategory.ProductSubCategoryResponse;
import com.billing.entity.ProductSubCategory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProductSubCategoryMapper {

    public ProductSubCategoryResponse toResponse(ProductSubCategory subCategory, AuditNameResolver auditNameResolver) {
        return ProductSubCategoryResponse.builder()
                .id(subCategory.getId())
                .categoryId(subCategory.getProductCategory() != null ? subCategory.getProductCategory().getId() : null)
                .categoryName(subCategory.getProductCategory() != null ? subCategory.getProductCategory().getCategoryName() : null)
                .subCategoryName(subCategory.getSubCategoryName())
                .description(subCategory.getDescription())
                .active(subCategory.isActive())
                .createdAt(subCategory.getCreatedAt())
                .updatedAt(subCategory.getUpdatedAt())
                .createdBy(auditNameResolver.displayName(subCategory.getCreatedBy()))
                .updatedBy(auditNameResolver.displayName(subCategory.getUpdatedBy()))
                .build();
    }

    public Map<String, Object> snapshot(ProductSubCategory subCategory) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("categoryId", subCategory.getProductCategory() != null ? subCategory.getProductCategory().getId() : null);
        data.put("categoryName", subCategory.getProductCategory() != null ? subCategory.getProductCategory().getCategoryName() : null);
        data.put("subCategoryName", subCategory.getSubCategoryName());
        data.put("description", subCategory.getDescription());
        data.put("active", subCategory.isActive());
        return data;
    }
}
