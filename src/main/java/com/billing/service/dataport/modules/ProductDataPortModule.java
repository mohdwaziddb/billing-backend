package com.billing.service.dataport.modules;

import com.billing.dto.dataport.DataPortRow;
import com.billing.dto.dataport.ImportResult;
import com.billing.entity.Company;
import com.billing.entity.Product;
import com.billing.entity.ProductCategory;
import com.billing.entity.ProductSubCategory;
import com.billing.entity.TaxMaster;
import com.billing.service.ProductService;
import com.billing.service.dataport.DataPortField;
import com.billing.service.dataport.DataPortImports;
import com.billing.service.dataport.DataPortLookups;
import com.billing.service.dataport.DataPortModule;
import com.billing.service.dataport.DataPortValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.billing.service.dataport.DataPortField.Type.BOOLEAN;
import static com.billing.service.dataport.DataPortField.Type.DECIMAL;
import static com.billing.service.dataport.DataPortField.Type.NUMBER;
import static com.billing.service.dataport.DataPortField.Type.TEXT;

/** Product master. Save ProductService.create se (Add Product form wala hi rasta). */
@Component
@RequiredArgsConstructor
public class ProductDataPortModule implements DataPortModule<ProductDataPortModule.Context> {

    private final DataPortLookups lookups;
    private final ProductService productService;

    record Context(Map<String, ProductCategory> categories,
                   Map<String, ProductSubCategory> subCategories,
                   Map<String, Product> productsBySku,
                   Map<BigDecimal, TaxMaster> taxesByRate) {
    }

    @Override
    public String key() {
        return "products";
    }

    @Override
    public String menuCode() {
        return "PRODUCT_DATAPORT";
    }

    @Override
    public String title() {
        return "Products";
    }

    @Override
    public String description() {
        return "Create products with category, SKU, HSN and GST rate. Stock is added separately through Opening Stock.";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public List<DataPortField> fields() {
        return List.of(
                DataPortField.required("productName", "Product Name", TEXT, null),
                DataPortField.required("category", "Category", TEXT, "Must already exist and be active (import Product Categories first)."),
                DataPortField.required("subCategory", "Sub Category", TEXT, "Must exist under the category."),
                DataPortField.required("sku", "SKU", TEXT, "Unique product code. Opening Stock and Invoices use this SKU."),
                DataPortField.optional("brand", "Brand", TEXT, null),
                DataPortField.optional("hsnCode", "HSN Code", TEXT, "4 to 8 digits."),
                DataPortField.optional("minStockQty", "Min Stock Qty", NUMBER, "Low-stock alert level. Blank = 0."),
                DataPortField.required("taxPercent", "GST %", DECIMAL, "Must match an active tax in Tax Master (e.g. 0, 5, 12, 18, 28)."),
                DataPortField.optional("active", "Active", BOOLEAN, "Blank = Yes."));
    }

    @Override
    public List<List<String>> sampleRows() {
        return List.of(
                List.of("Classmate Notebook 172 Pages", "Stationery", "Notebooks", "DP-NB-001", "Classmate", "4820", "20", "12", "Yes"),
                List.of("A4 Spiral Notebook 200 Pages", "Stationery", "Notebooks", "DP-NB-002", "Navneet", "4820", "15", "12", "Yes"),
                List.of("Reynolds Ball Pen Blue (Pack of 10)", "Stationery", "Pens", "DP-PEN-001", "Reynolds", "9608", "25", "18", "Yes"),
                List.of("Pilot V7 Gel Pen", "Stationery", "Pens", "DP-PEN-002", "Pilot", "9608", "20", "18", "Yes"),
                List.of("Box File Foolscap", "Stationery", "Files & Folders", "DP-FILE-001", "Solo", "4820", "10", "12", "Yes"),
                List.of("Camlin Water Colours 12 Shades", "Stationery", "Art Supplies", "DP-ART-001", "Camlin", "3213", "8", "12", "Yes"),
                List.of("USB-C Fast Charger 20W", "Electronics", "Mobile Accessories", "DP-MOB-001", "boAt", "8504", "10", "18", "Yes"),
                List.of("Tempered Glass Screen Guard", "Electronics", "Mobile Accessories", "DP-MOB-002", "Spigen", "7007", "15", "18", "Yes"),
                List.of("Wireless Optical Mouse", "Electronics", "Computer Accessories", "DP-CMP-001", "Logitech", "8471", "8", "18", "Yes"),
                List.of("USB Wired Keyboard", "Electronics", "Computer Accessories", "DP-CMP-002", "Dell", "8471", "5", "18", "Yes"),
                List.of("Bluetooth Earbuds", "Electronics", "Audio", "DP-AUD-001", "boAt", "8518", "5", "18", "Yes"),
                List.of("Salted Cashews 250g", "Grocery", "Snacks", "DP-SNK-001", "Happilo", "0801", "10", "5", "Yes"),
                List.of("Green Tea 100 Bags", "Grocery", "Beverages", "DP-BEV-001", "Lipton", "0902", "10", "5", "Yes"),
                List.of("Basmati Rice 5kg", "Grocery", "Staples", "DP-STP-001", "India Gate", "1006", "6", "5", "Yes"),
                List.of("Non-stick Fry Pan 24cm", "Home & Kitchen", "Cookware", "DP-CKW-001", "Prestige", "7323", "4", "18", "Yes"),
                List.of("Airtight Container Set (3 pcs)", "Home & Kitchen", "Storage", "DP-STO-001", "Cello", "3924", "6", "18", "Yes"),
                List.of("Floor Cleaner 1L", "Home & Kitchen", "Cleaning", "DP-CLN-001", "Lizol", "3402", "12", "18", "Yes"),
                List.of("Aloe Vera Face Wash 150ml", "Personal Care", "Skin Care", "DP-SKN-001", "Himalaya", "3304", "10", "18", "Yes"),
                List.of("Herbal Shampoo 340ml", "Personal Care", "Hair Care", "DP-HAI-001", "Dabur", "3305", "8", "18", "Yes"),
                List.of("Screwdriver Set (6 pcs)", "Hardware", "Tools", "DP-TOL-001", "Taparia", "8205", "3", "18", "Yes"));
    }

    @Override
    public Context buildContext(String email, Company company) {
        return new Context(lookups.productCategories(company), lookups.productSubCategories(company),
                lookups.productsBySku(company), lookups.activeTaxesByRate(company));
    }

    @Override
    public void validate(List<DataPortRow> rows, Context context) {
        DataPortValues.markDuplicates(rows, "sku", row -> DataPortValues.key(row.value("sku")), "SKU is repeated in the file");
        for (DataPortRow row : rows) {
            ProductCategory category = context.categories().get(DataPortValues.key(row.value("category")));
            if (!row.value("category").isEmpty()) {
                if (category == null) {
                    row.error("category", "Category not found — import or create it first");
                } else if (!category.isActive()) {
                    row.error("category", "Category is inactive");
                }
            }
            if (category != null && category.isActive() && !row.value("subCategory").isEmpty()) {
                ProductSubCategory sub = context.subCategories().get(DataPortLookups.subKey(category.getId(), row.value("subCategory")));
                if (sub == null) {
                    row.error("subCategory", "Sub category not found under " + category.getCategoryName());
                } else if (!sub.isActive()) {
                    row.error("subCategory", "Sub category is inactive");
                } else {
                    row.getResolved().put("categoryId", category.getId());
                    row.getResolved().put("subCategoryId", sub.getId());
                }
            }
            if (!row.value("sku").isEmpty() && context.productsBySku().containsKey(DataPortValues.key(row.value("sku")))) {
                row.error("sku", "SKU already exists");
            }
            String hsn = row.value("hsnCode");
            if (!hsn.isEmpty() && !hsn.matches("\\d{4,8}")) {
                row.error("hsnCode", "HSN code must be 4 to 8 digits");
            }
            Integer minStock = DataPortValues.integer(row.value("minStockQty"));
            if (minStock != null && minStock < 0) {
                row.error("minStockQty", "Min stock qty must be 0 or more");
            }
            BigDecimal rate = DataPortValues.decimal(row.value("taxPercent"));
            if (rate != null) {
                TaxMaster tax = context.taxesByRate().get(rate.stripTrailingZeros());
                if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
                    row.error("taxPercent", "GST % must be between 0 and 100");
                } else if (tax == null) {
                    row.error("taxPercent", "No active tax with " + rate.stripTrailingZeros().toPlainString() + "% in Tax Master");
                } else {
                    row.getResolved().put("taxMasterId", tax.getId());
                    row.getResolved().put("taxable", rate.signum() > 0);
                }
            }
        }
    }

    @Override
    public ImportResult importRows(String email, Company company, List<DataPortRow> validRows, Context context) {
        return DataPortImports.perRow(validRows, row -> {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("name", row.value("productName"));
            param.put("categoryId", row.resolvedValue("categoryId"));
            param.put("subCategoryId", row.resolvedValue("subCategoryId"));
            param.put("sku", row.value("sku"));
            param.put("brand", DataPortValues.blankToNull(row.value("brand")));
            param.put("hsnCode", DataPortValues.blankToNull(row.value("hsnCode")));
            param.put("minStockQty", row.value("minStockQty").isEmpty() ? 0 : DataPortValues.integer(row.value("minStockQty")));
            param.put("taxMasterId", row.resolvedValue("taxMasterId"));
            param.put("taxable", row.resolvedValue("taxable"));
            param.put("active", DataPortValues.boolOrDefault(row.value("active"), true));
            productService.create(DataPortImports.sanitized(param), email);
        });
    }
}
