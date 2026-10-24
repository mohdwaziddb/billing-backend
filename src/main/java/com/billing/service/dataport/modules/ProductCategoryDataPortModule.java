package com.billing.service.dataport.modules;

import com.billing.dto.dataport.DataPortRow;
import com.billing.dto.dataport.ImportResult;
import com.billing.entity.Company;
import com.billing.entity.ProductCategory;
import com.billing.entity.ProductSubCategory;
import com.billing.service.ProductCategoryService;
import com.billing.service.ProductSubCategoryService;
import com.billing.service.dataport.DataPortField;
import com.billing.service.dataport.DataPortImports;
import com.billing.service.dataport.DataPortLookups;
import com.billing.service.dataport.DataPortModule;
import com.billing.service.dataport.DataPortValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.billing.service.dataport.DataPortField.Type.BOOLEAN;
import static com.billing.service.dataport.DataPortField.Type.TEXT;

/**
 * Category + Sub Category ek hi sheet me: Category naya ho to ban jata hai,
 * Sub Category us category ke andar banti hai. Sirf Owner (service rule).
 */
@Component
@RequiredArgsConstructor
public class ProductCategoryDataPortModule implements DataPortModule<ProductCategoryDataPortModule.Context> {

    private final DataPortLookups lookups;
    private final ProductCategoryService productCategoryService;
    private final ProductSubCategoryService productSubCategoryService;

    record Context(Map<String, ProductCategory> categories, Map<String, ProductSubCategory> subCategories) {
    }

    @Override
    public String key() {
        return "product-categories";
    }

    @Override
    public String menuCode() {
        return "CATEGORY_DATAPORT";
    }

    @Override
    public String title() {
        return "Product Categories";
    }

    @Override
    public String description() {
        return "Create product categories and their sub categories in bulk. Import this first — products need them.";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public List<DataPortField> fields() {
        return List.of(
                DataPortField.required("category", "Category", TEXT, "New categories are created; existing ones are reused for sub categories."),
                DataPortField.optional("subCategory", "Sub Category", TEXT, "Leave blank to create only the category."),
                DataPortField.optional("description", "Description", TEXT, "Saved on the sub category (or on the category when sub category is blank)."),
                DataPortField.optional("active", "Active", BOOLEAN, "Blank = Yes."));
    }

    @Override
    public List<String> notes() {
        return List.of(
                "One row = one sub category. Repeat the category name on every row of that category.",
                "Only the company Owner can create categories.");
    }

    @Override
    public List<List<String>> sampleRows() {
        return List.of(
                List.of("Stationery", "Notebooks", "Ruled and plain notebooks", "Yes"),
                List.of("Stationery", "Pens", "Ball, gel and ink pens", "Yes"),
                List.of("Stationery", "Files & Folders", "Box files, clip files, folders", "Yes"),
                List.of("Stationery", "Art Supplies", "Colours, brushes, sketch books", "Yes"),
                List.of("Electronics", "Mobile Accessories", "Chargers, cables, screen guards", "Yes"),
                List.of("Electronics", "Computer Accessories", "Mouse, keyboard, pen drives", "Yes"),
                List.of("Electronics", "Audio", "Earbuds, headphones, speakers", "Yes"),
                List.of("Grocery", "Snacks", "Dry fruits, namkeen, biscuits", "Yes"),
                List.of("Grocery", "Beverages", "Tea, coffee, juices", "Yes"),
                List.of("Grocery", "Staples", "Rice, atta, dal, oil", "Yes"),
                List.of("Home & Kitchen", "Cookware", "Pans, kadai, pressure cookers", "Yes"),
                List.of("Home & Kitchen", "Storage", "Containers and jars", "Yes"),
                List.of("Home & Kitchen", "Cleaning", "Floor and surface cleaners", "Yes"),
                List.of("Personal Care", "Skin Care", "Face wash, creams, lotions", "Yes"),
                List.of("Personal Care", "Hair Care", "Shampoo, oil, conditioner", "Yes"),
                List.of("Personal Care", "Oral Care", "Toothpaste and brushes", "Yes"),
                List.of("Hardware", "Tools", "Hand tools and tool kits", "Yes"),
                List.of("Hardware", "Electricals", "Switches, wires, bulbs", "No"));
    }

    @Override
    public Context buildContext(String email, Company company) {
        return new Context(lookups.productCategories(company), lookups.productSubCategories(company));
    }

    @Override
    public void validate(List<DataPortRow> rows, Context context) {
        DataPortValues.markDuplicates(rows, "subCategory",
                row -> row.value("subCategory").isEmpty() ? null : DataPortValues.key(row.value("category")) + "::" + DataPortValues.key(row.value("subCategory")),
                "This category + sub category is repeated in the file");
        DataPortValues.markDuplicates(rows, "category",
                row -> row.value("subCategory").isEmpty() ? DataPortValues.key(row.value("category")) : null,
                "This category-only row is repeated in the file");

        for (DataPortRow row : rows) {
            String categoryName = row.value("category");
            if (categoryName.isEmpty()) {
                continue;
            }
            if (categoryName.length() > 255) {
                row.error("category", "Category name must be 255 characters or less");
            }
            ProductCategory existing = context.categories().get(DataPortValues.key(categoryName));
            String subName = row.value("subCategory");
            if (subName.isEmpty()) {
                if (existing != null) {
                    row.error("category", "Category already exists");
                }
                continue;
            }
            if (subName.length() > 255) {
                row.error("subCategory", "Sub category name must be 255 characters or less");
            }
            if (existing != null) {
                if (!existing.isActive()) {
                    row.error("category", "Category is inactive — activate it in Setup first");
                } else if (context.subCategories().containsKey(DataPortLookups.subKey(existing.getId(), subName))) {
                    row.error("subCategory", "Sub category already exists under this category");
                }
            }
        }
    }

    @Override
    public ImportResult importRows(String email, Company company, List<DataPortRow> validRows, Context context) {
        // Import ke dauraan bani categories (naam -> id), taaki agli rows usi me jaayein.
        Map<String, Long> categoryIds = new HashMap<>();
        context.categories().forEach((name, category) -> categoryIds.put(name, category.getId()));

        return DataPortImports.perRow(validRows, row -> {
            boolean active = DataPortValues.boolOrDefault(row.value("active"), true);
            String categoryKey = DataPortValues.key(row.value("category"));
            String subName = row.value("subCategory");
            Long categoryId = categoryIds.get(categoryKey);
            if (categoryId == null) {
                Map<String, Object> param = new LinkedHashMap<>();
                param.put("categoryName", row.value("category"));
                if (subName.isEmpty()) {
                    param.put("description", DataPortValues.blankToNull(row.value("description")));
                    param.put("active", active);
                }
                categoryId = productCategoryService.create(DataPortImports.sanitized(param), email).getId();
                categoryIds.put(categoryKey, categoryId);
            }
            if (subName.isEmpty()) {
                return;
            }
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("categoryId", categoryId);
            param.put("subCategoryName", subName);
            param.put("description", DataPortValues.blankToNull(row.value("description")));
            param.put("active", active);
            productSubCategoryService.create(DataPortImports.sanitized(param), email);
        });
    }
}
