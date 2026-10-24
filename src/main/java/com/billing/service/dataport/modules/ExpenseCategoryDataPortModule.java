package com.billing.service.dataport.modules;

import com.billing.dto.dataport.DataPortRow;
import com.billing.dto.dataport.ImportResult;
import com.billing.entity.Company;
import com.billing.entity.ExpenseCategory;
import com.billing.service.ExpenseCategoryService;
import com.billing.service.dataport.DataPortField;
import com.billing.service.dataport.DataPortImports;
import com.billing.service.dataport.DataPortLookups;
import com.billing.service.dataport.DataPortModule;
import com.billing.service.dataport.DataPortValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.billing.service.dataport.DataPortField.Type.BOOLEAN;
import static com.billing.service.dataport.DataPortField.Type.TEXT;

@Component
@RequiredArgsConstructor
public class ExpenseCategoryDataPortModule implements DataPortModule<ExpenseCategoryDataPortModule.Context> {

    private final DataPortLookups lookups;
    private final ExpenseCategoryService expenseCategoryService;

    record Context(Map<String, ExpenseCategory> categories) {
    }

    @Override
    public String key() {
        return "expense-categories";
    }

    @Override
    public String menuCode() {
        return "EXPENSE_CATEGORY_DATAPORT";
    }

    @Override
    public String title() {
        return "Expense Categories";
    }

    @Override
    public String description() {
        return "Create expense heads like Rent, Salaries or Electricity. Import these before Expenses.";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public List<DataPortField> fields() {
        return List.of(
                DataPortField.required("categoryName", "Category Name", TEXT, "Must be unique."),
                DataPortField.optional("description", "Description", TEXT, null),
                DataPortField.optional("active", "Active", BOOLEAN, "Blank = Yes."));
    }

    @Override
    public List<List<String>> sampleRows() {
        return List.of(
                List.of("Shop Rent", "Monthly shop / office rent", "Yes"),
                List.of("Electricity", "Electricity bills", "Yes"),
                List.of("Staff Salaries", "Monthly salaries and wages", "Yes"),
                List.of("Internet & Phone", "Broadband and mobile recharges", "Yes"),
                List.of("Transport & Freight", "Delivery and courier charges", "Yes"),
                List.of("Packaging Material", "Boxes, tape, carry bags", "Yes"),
                List.of("Office Supplies", "Printer ink, paper, stationery for office use", "Yes"),
                List.of("Repairs & Maintenance", "Shop repairs, AC service", "Yes"),
                List.of("Marketing", "Ads, banners, social media promotion", "Yes"),
                List.of("Bank Charges", "Bank fees and payment gateway charges", "Yes"),
                List.of("Tea & Refreshments", "Daily tea, water, snacks", "Yes"),
                List.of("Travel", "Business travel and fuel", "Yes"),
                List.of("Software Subscriptions", "Billing, accounting and other software", "Yes"),
                List.of("Professional Fees", "CA, legal and consultant fees", "Yes"),
                List.of("Miscellaneous", "Other small expenses", "Yes"),
                List.of("Insurance", "Shop and stock insurance", "No"));
    }

    @Override
    public Context buildContext(String email, Company company) {
        return new Context(lookups.expenseCategories(company));
    }

    @Override
    public void validate(List<DataPortRow> rows, Context context) {
        DataPortValues.markDuplicates(rows, "categoryName", row -> DataPortValues.key(row.value("categoryName")), "Category name is repeated in the file");
        for (DataPortRow row : rows) {
            if (!row.value("categoryName").isEmpty() && context.categories().containsKey(DataPortValues.key(row.value("categoryName")))) {
                row.error("categoryName", "Expense category already exists");
            }
        }
    }

    @Override
    public ImportResult importRows(String email, Company company, List<DataPortRow> validRows, Context context) {
        return DataPortImports.perRow(validRows, row -> {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("categoryName", row.value("categoryName"));
            param.put("description", DataPortValues.blankToNull(row.value("description")));
            param.put("active", DataPortValues.boolOrDefault(row.value("active"), true));
            expenseCategoryService.create(DataPortImports.sanitized(param), email);
        });
    }
}
