package com.billing.service.dataport;

import com.billing.dto.dataport.ProductDataPortRow;
import com.billing.entity.ProductCategory;
import com.billing.entity.ProductSubCategory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProductImportValidator implements ImportValidator<ProductDataPortRow, ProductDataPortDefinition.ProductDataPortContext> {

    private static final Set<String> TRUE_VALUES = Set.of("yes", "true", "1");
    private static final Set<String> FALSE_VALUES = Set.of("no", "false", "0");

    @Override
    public List<ProductDataPortRow> validate(List<ProductDataPortRow> rows, ProductDataPortDefinition.ProductDataPortContext context) {
        Map<String, Long> uploadSkuCounts = rows.stream()
                .map(row -> normalizeKey(row.getSku()))
                .filter(value -> !value.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        return rows.stream()
                .map(row -> validateRow(row, context, uploadSkuCounts))
                .toList();
    }

    private ProductDataPortRow validateRow(ProductDataPortRow row,
                                           ProductDataPortDefinition.ProductDataPortContext context,
                                           Map<String, Long> uploadSkuCounts) {
        Map<String, String> errors = new LinkedHashMap<>();

        row.setProductName(trim(row.getProductName()));
        row.setProductCategory(trim(row.getProductCategory()));
        row.setProductSubCategory(trim(row.getProductSubCategory()));
        row.setSku(trim(row.getSku()));
        row.setActive(trim(row.getActive()));
        row.setBrand(blankToNull(row.getBrand()));
        row.setHsnCode(blankToNull(row.getHsnCode()));
        row.setMinimumStockQty(trim(row.getMinimumStockQty()));
        row.setTaxPercent(trim(row.getTaxPercent()));
        row.setProductCategoryId(null);
        row.setProductSubCategoryId(null);
        row.setActiveValue(null);

        require(row.getProductName(), "productName", "Product name is required", errors);
        require(row.getProductCategory(), "productCategory", "Product category is required", errors);
        require(row.getProductSubCategory(), "productSubCategory", "Product sub category is required", errors);
        require(row.getSku(), "sku", "SKU is required", errors);
        require(row.getTaxPercent(), "taxPercent", "Tax percent is required", errors);

        if (!row.getProductCategory().isBlank()) {
            ProductCategory category = context.categoriesByName().get(normalizeKey(row.getProductCategory()));
            if (category == null) {
                errors.put("productCategory", "Product category was not found in your company");
            } else {
                row.setProductCategoryId(category.getId());
            }
        }

        if (row.getProductCategoryId() != null && !row.getProductSubCategory().isBlank()) {
            ProductSubCategory subCategory = context.subCategoriesByCategoryAndName().get(ProductDataPortDefinition.buildSubCategoryKey(row.getProductCategoryId(), row.getProductSubCategory()));
            if (subCategory == null) {
                errors.put("productSubCategory", "Product sub category was not found under the selected category in your company");
            } else {
                row.setProductSubCategoryId(subCategory.getId());
            }
        }

        String normalizedSku = normalizeKey(row.getSku());
        if (!normalizedSku.isBlank() && context.existingSkus().contains(normalizedSku)) {
            errors.put("sku", "SKU already exists in your company");
        } else if (!normalizedSku.isBlank() && uploadSkuCounts.getOrDefault(normalizedSku, 0L) > 1) {
            errors.put("sku", "SKU is duplicated in the uploaded file");
        }

        BigDecimal taxPercent = parseDecimal(row.getTaxPercent(), "taxPercent", "Tax percent must be a valid number", errors);
        Integer minimumStockQty = parseInteger(defaultIfBlank(row.getMinimumStockQty(), "0"), "minimumStockQty", "Minimum stock qty must be a whole number", errors);

        if (taxPercent != null && taxPercent.compareTo(BigDecimal.ZERO) < 0) {
            errors.put("taxPercent", "Tax percent must be 0 or more");
        }
        if (minimumStockQty != null && minimumStockQty < 0) {
            errors.put("minimumStockQty", "Minimum stock qty must be 0 or more");
        }

        Boolean activeValue = parseActive(row.getActive(), errors);
        row.setActiveValue(activeValue);
        row.setValidationErrors(errors);
        row.setValid(errors.isEmpty());
        return row;
    }

    private Boolean parseActive(String value, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(normalized)) {
            return true;
        }
        if (FALSE_VALUES.contains(normalized)) {
            return false;
        }
        errors.put("active", "Active must be Yes, No, True, False, 1, or 0");
        return null;
    }

    private BigDecimal parseDecimal(String value, String field, String message, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException ex) {
            errors.put(field, message);
            return null;
        }
    }

    private Integer parseInteger(String value, String field, String message, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            errors.put(field, message);
            return null;
        }
    }

    private void require(String value, String field, String message, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            errors.put(field, message);
        }
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
