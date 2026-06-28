package com.billing.service.dataport;

import com.billing.dto.dataport.ProductDataPortReferenceData;
import com.billing.dto.dataport.ProductDataPortRow;
import com.billing.entity.Company;
import com.billing.entity.ProductCategory;
import com.billing.entity.ProductSubCategory;
import com.billing.repository.ProductCategoryRepository;
import com.billing.repository.ProductRepository;
import com.billing.repository.ProductSubCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductDataPortDefinition implements DataPortDefinition<ProductDataPortRow, ProductDataPortDefinition.ProductDataPortContext> {

    public static final String MODULE_KEY = "products";

    private static final List<String> COLUMNS = List.of(
            "Product Name*",
            "Product Category*",
            "Product Sub Category*",
            "SKU*",
            "Active",
            "Brand",
            "HSN Code",
            "Minimum Stock Qty",
            "Tax Percent*"
    );

    private final ProductCategoryRepository productCategoryRepository;
    private final ProductSubCategoryRepository productSubCategoryRepository;
    private final ProductRepository productRepository;
    private final ProductImportValidator productImportValidator;
    private final ProductImportProcessor productImportProcessor;

    @Override
    public String getModuleKey() {
        return MODULE_KEY;
    }

    @Override
    public String getSampleFileName() {
        return "product-dataport-sample.xlsx";
    }

    @Override
    public String getSheetName() {
        return "Products";
    }

    @Override
    public List<String> getColumns() {
        return COLUMNS;
    }

    @Override
    public List<List<String>> getSampleRows() {
        return List.of(List.of(
                "Premium Notebook",
                "Stationery",
                "Office Supplies",
                "NOTE-001",
                "Yes",
                "Acme",
                "4820",
                "5",
                "18.00"
        ));
    }

    @Override
    public ProductDataPortRow mapRow(int rowNumber, Map<String, String> rowValues) {
        ProductDataPortRow row = new ProductDataPortRow();
        row.setRowNumber(rowNumber);
        row.setProductName(rowValues.getOrDefault(COLUMNS.get(0), ""));
        row.setProductCategory(rowValues.getOrDefault(COLUMNS.get(1), ""));
        row.setProductSubCategory(rowValues.getOrDefault(COLUMNS.get(2), ""));
        row.setSku(rowValues.getOrDefault(COLUMNS.get(3), ""));
        row.setActive(rowValues.getOrDefault(COLUMNS.get(4), ""));
        row.setBrand(rowValues.getOrDefault(COLUMNS.get(5), ""));
        row.setHsnCode(rowValues.getOrDefault(COLUMNS.get(6), ""));
        row.setMinimumStockQty(rowValues.getOrDefault(COLUMNS.get(7), ""));
        row.setTaxPercent(rowValues.getOrDefault(COLUMNS.get(8), ""));
        return row;
    }

    @Override
    public ProductDataPortContext buildContext(String email, Company company) {
        Map<String, ProductCategory> categoriesByName = productCategoryRepository.findByCompanyAndActiveTrueOrderByCategoryNameAsc(company).stream()
                .collect(Collectors.toMap(
                        category -> normalizeKey(category.getCategoryName()),
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<String, ProductSubCategory> subCategoriesByCategoryAndName = productSubCategoryRepository.findAllByCompanyWithFilters(company, null, true, null).stream()
                .collect(Collectors.toMap(
                        subCategory -> buildSubCategoryKey(subCategory.getProductCategory().getId(), subCategory.getSubCategoryName()),
                        Function.identity(),
                        (left, right) -> left
                ));
        Set<String> existingSkus = productRepository.findNormalizedSkusByCompany(company);
        return new ProductDataPortContext(categoriesByName, subCategoriesByCategoryAndName, existingSkus);
    }

    @Override
    public ImportValidator<ProductDataPortRow, ProductDataPortContext> getValidator() {
        return productImportValidator;
    }

    @Override
    public ImportProcessor<ProductDataPortRow, ProductDataPortContext> getImportProcessor() {
        return productImportProcessor;
    }

    @Override
    public Object buildReferenceData(ProductDataPortContext context) {
        return ProductDataPortReferenceData.builder()
                .categories(context.categoriesByName().values().stream()
                        .map(category -> ProductDataPortReferenceData.CategoryOption.builder()
                                .id(category.getId())
                                .name(category.getCategoryName())
                                .build())
                        .toList())
                .subCategories(context.subCategoriesByCategoryAndName().values().stream()
                        .map(subCategory -> ProductDataPortReferenceData.SubCategoryOption.builder()
                                .id(subCategory.getId())
                                .categoryId(subCategory.getProductCategory().getId())
                                .categoryName(subCategory.getProductCategory().getCategoryName())
                                .name(subCategory.getSubCategoryName())
                                .build())
                        .toList())
                .existingSkus(context.existingSkus().stream().sorted().toList())
                .build();
    }

    static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static String buildSubCategoryKey(Long categoryId, String subCategoryName) {
        return (categoryId == null ? "" : categoryId) + "::" + normalizeKey(subCategoryName);
    }

    public record ProductDataPortContext(Map<String, ProductCategory> categoriesByName,
                                         Map<String, ProductSubCategory> subCategoriesByCategoryAndName,
                                         Set<String> existingSkus) {
    }
}
