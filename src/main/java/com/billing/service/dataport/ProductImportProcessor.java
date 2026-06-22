package com.billing.service.dataport;

import com.billing.dto.dataport.ProductDataPortRow;
import com.billing.entity.Company;
import com.billing.entity.Product;
import com.billing.entity.ProductCategory;
import com.billing.entity.ProductSubCategory;
import com.billing.repository.ProductRepository;
import com.billing.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProductImportProcessor implements ImportProcessor<ProductDataPortRow, ProductDataPortDefinition.ProductDataPortContext> {

    private final AuditLogService auditLogService;
    private final ProductRepository productRepository;

    @Override
    @Transactional
    public int process(String email,
                       Company company,
                       List<ProductDataPortRow> validRows,
                       ProductDataPortDefinition.ProductDataPortContext context) {
        List<Product> products = validRows.stream()
                .map(row -> toEntity(company, row, context))
                .toList();

        List<Product> savedProducts = productRepository.saveAll(products);
        for (Product savedProduct : savedProducts) {
            auditLogService.logCreate(email, company, "Product", "Product", savedProduct.getId(), snapshot(savedProduct));
        }
        return savedProducts.size();
    }

    private Product toEntity(Company company,
                             ProductDataPortRow row,
                             ProductDataPortDefinition.ProductDataPortContext context) {
        ProductCategory category = context.categoriesByName().get(ProductDataPortDefinition.normalizeKey(row.getProductCategory()));
        ProductSubCategory subCategory = context.subCategoriesByCategoryAndName().get(ProductDataPortDefinition.buildSubCategoryKey(
                row.getProductCategoryId() != null ? row.getProductCategoryId() : (category != null ? category.getId() : null),
                row.getProductSubCategory()
        ));
        return Product.builder()
                .company(company)
                .name(row.getProductName())
                .productCategory(category)
                .productSubCategory(subCategory)
                .brand(row.getBrand())
                .sku(row.getSku())
                .hsnCode(row.getHsnCode())
                .purchasePrice(scaleDecimal(row.getPurchasePrice()))
                .sellingPrice(scaleDecimal(row.getSellingPrice()))
                .stockQty(parseIntegerOrDefault(row.getOpeningStockQty()))
                .minStockQty(parseIntegerOrDefault(row.getMinimumStockQty()))
                .taxPercent(scalePercent(row.getTaxPercent()))
                .active(Boolean.TRUE.equals(row.getActiveValue()))
                .build();
    }

    private BigDecimal scaleDecimal(String value) {
        return new BigDecimal(value.replace(",", "").trim()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scalePercent(String value) {
        return scaleDecimal(value);
    }

    private Integer parseIntegerOrDefault(String value) {
        return value == null || value.isBlank() ? 0 : Integer.valueOf(value.trim());
    }

    private Map<String, Object> snapshot(Product product) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", product.getName());
        data.put("category", product.getProductCategory() != null ? product.getProductCategory().getCategoryName() : null);
        data.put("subCategory", product.getProductSubCategory() != null ? product.getProductSubCategory().getSubCategoryName() : null);
        data.put("brand", product.getBrand());
        data.put("sku", product.getSku());
        data.put("purchasePrice", product.getPurchasePrice());
        data.put("sellingPrice", product.getSellingPrice());
        data.put("stockQty", product.getStockQty());
        data.put("minStockQty", product.getMinStockQty());
        data.put("taxPercent", product.getTaxPercent());
        data.put("active", product.isActive());
        return data;
    }
}
