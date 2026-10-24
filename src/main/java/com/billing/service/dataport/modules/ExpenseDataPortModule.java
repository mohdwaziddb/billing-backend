package com.billing.service.dataport.modules;

import com.billing.dto.dataport.DataPortRow;
import com.billing.dto.dataport.ImportResult;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.ExpenseCategory;
import com.billing.entity.Invoice;
import com.billing.service.ExpenseService;
import com.billing.service.dataport.DataPortField;
import com.billing.service.dataport.DataPortImports;
import com.billing.service.dataport.DataPortLookups;
import com.billing.service.dataport.DataPortModule;
import com.billing.service.dataport.DataPortValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.billing.service.dataport.DataPortField.Type.DATE;
import static com.billing.service.dataport.DataPortField.Type.DECIMAL;
import static com.billing.service.dataport.DataPortField.Type.TEXT;

/** Expenses (ExpenseService.create — Add Expense form wala rasta). */
@Component
@RequiredArgsConstructor
public class ExpenseDataPortModule implements DataPortModule<ExpenseDataPortModule.Context> {

    static final String GENERAL = "General";
    static final String CUSTOMER = "Customer Related";
    static final String INVOICE = "Invoice Related";

    private final DataPortLookups lookups;
    private final ExpenseService expenseService;

    record Context(Map<String, ExpenseCategory> categories, Map<String, Customer> customersByMobile, java.util.Set<String> existingExpenses) {
    }

    @Override
    public String key() {
        return "expenses";
    }

    @Override
    public String menuCode() {
        return "EXPENSE_DATAPORT";
    }

    @Override
    public String title() {
        return "Expenses";
    }

    @Override
    public String description() {
        return "Record past expenses so Profit & Loss and cash flow show the full picture.";
    }

    @Override
    public int order() {
        return 80;
    }

    @Override
    public List<DataPortField> fields() {
        return List.of(
                DataPortField.required("expenseDate", "Expense Date", DATE, "Cannot be in the future."),
                DataPortField.required("category", "Category", TEXT, "Must be an active expense category."),
                DataPortField.required("amount", "Amount", DECIMAL, "Greater than 0."),
                DataPortField.choice("expenseType", "Expense Type", false, List.of(GENERAL, CUSTOMER, INVOICE), "Blank = General."),
                DataPortField.optional("customerMobile", "Customer Mobile", TEXT, "Required for Customer Related."),
                DataPortField.optional("invoiceNo", "Invoice No", TEXT, "Required for Invoice Related, e.g. INV/2627/00001."),
                DataPortField.optional("description", "Description", TEXT, null));
    }

    @Override
    public List<List<String>> sampleRows() {
        return List.of(
                List.of("01-04-2026", "Shop Rent", "25000", GENERAL, "", "", "April rent"),
                List.of("05-04-2026", "Electricity", "4350", GENERAL, "", "", "March bill"),
                List.of("07-04-2026", "Staff Salaries", "36000", GENERAL, "", "", "Salaries for March"),
                List.of("08-04-2026", "Internet & Phone", "1199", GENERAL, "", "", "Broadband"),
                List.of("10-04-2026", "Transport & Freight", "850", CUSTOMER, "9810000001", "", "Delivery to Sharma Stationers"),
                List.of("11-04-2026", "Packaging Material", "1240", GENERAL, "", "", "Carry bags and tape"),
                List.of("12-04-2026", "Office Supplies", "560", GENERAL, "", "", "Printer paper"),
                List.of("14-04-2026", "Repairs & Maintenance", "1800", GENERAL, "", "", "AC service"),
                List.of("15-04-2026", "Marketing", "3000", GENERAL, "", "", "Instagram ads"),
                List.of("16-04-2026", "Bank Charges", "118", GENERAL, "", "", "Account maintenance fee"),
                List.of("17-04-2026", "Tea & Refreshments", "420", GENERAL, "", "", ""),
                List.of("18-04-2026", "Travel", "1650", CUSTOMER, "9810000005", "", "Visit to Mehta Traders"),
                List.of("20-04-2026", "Software Subscriptions", "999", GENERAL, "", "", "Billing software"),
                List.of("22-04-2026", "Professional Fees", "5000", GENERAL, "", "", "CA fees for GST filing"),
                List.of("24-04-2026", "Miscellaneous", "275", GENERAL, "", "", ""),
                List.of("25-04-2026", "Transport & Freight", "620", CUSTOMER, "9810000009", "", "Courier to Ahmedabad"),
                List.of("28-04-2026", "Electricity", "3980", GENERAL, "", "", "April bill"),
                List.of("30-04-2026", "Tea & Refreshments", "380", GENERAL, "", "", ""));
    }

    @Override
    public Context buildContext(String email, Company company) {
        return new Context(lookups.expenseCategories(company), lookups.customersByMobile(), lookups.existingExpenses(company));
    }

    @Override
    public void validate(List<DataPortRow> rows, Context context) {
        for (DataPortRow row : rows) {
            if (!row.value("category").isEmpty()) {
                ExpenseCategory category = context.categories().get(DataPortValues.key(row.value("category")));
                if (category == null) {
                    row.error("category", "Expense category not found — import Expense Categories first");
                } else if (!category.isActive()) {
                    row.error("category", "Expense category is inactive");
                } else {
                    row.getResolved().put("categoryId", category.getId());
                }
            }
            BigDecimal amount = DataPortValues.decimal(row.value("amount"));
            if (amount != null && amount.signum() <= 0) {
                row.error("amount", "Amount must be greater than 0");
            }
            LocalDate date = DataPortValues.date(row.value("expenseDate"));
            if (date != null && date.isAfter(LocalDate.now())) {
                row.error("expenseDate", "Expense date cannot be in the future");
            }
            // Wahi file dobara upload: same date + category + amount + description pehle se hai.
            Long categoryId = row.resolvedValue("categoryId");
            if (date != null && categoryId != null && amount != null
                    && context.existingExpenses().contains(DataPortLookups.expenseKey(date, categoryId, amount, row.value("description")))) {
                row.error("amount", "This expense is already recorded (same date, category, amount and description)");
            }

            String type = row.value("expenseType").isEmpty() ? GENERAL : row.value("expenseType");
            row.getValues().put("expenseType", type);
            Customer customer = null;
            if (!row.value("customerMobile").isEmpty()) {
                customer = context.customersByMobile().get(DataPortLookups.normalizeMobile(row.value("customerMobile")));
                if (customer == null) {
                    row.error("customerMobile", "No customer with this mobile number");
                }
            }
            if (CUSTOMER.equals(type)) {
                if (row.value("customerMobile").isEmpty()) {
                    row.error("customerMobile", "Customer mobile is required for Customer Related expense");
                } else if (customer != null) {
                    row.getResolved().put("customerId", customer.getId());
                }
            } else if (INVOICE.equals(type)) {
                if (row.value("invoiceNo").isEmpty()) {
                    row.error("invoiceNo", "Invoice No is required for Invoice Related expense");
                } else {
                    Optional<Invoice> invoice = lookups.invoiceByNumber(row.value("invoiceNo"));
                    if (invoice.isEmpty()) {
                        row.error("invoiceNo", "Invoice not found");
                    } else if (customer != null && !customer.getId().equals(invoice.get().getCustomer().getId())) {
                        row.error("customerMobile", "This invoice belongs to a different customer");
                    } else {
                        row.getResolved().put("invoiceId", invoice.get().getId());
                    }
                }
            } else if (!row.value("customerMobile").isEmpty() || !row.value("invoiceNo").isEmpty()) {
                row.error("expenseType", "Customer / invoice given — choose Customer Related or Invoice Related");
            }
        }
    }

    @Override
    public ImportResult importRows(String email, Company company, List<DataPortRow> validRows, Context context) {
        return DataPortImports.perRow(validRows, row -> {
            String type = row.value("expenseType");
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("expenseType", CUSTOMER.equals(type) ? "CUSTOMER_RELATED" : INVOICE.equals(type) ? "INVOICE_RELATED" : "GENERAL");
            param.put("categoryId", row.resolvedValue("categoryId"));
            param.put("customerId", row.resolvedValue("customerId"));
            param.put("invoiceId", row.resolvedValue("invoiceId"));
            param.put("amount", DataPortValues.money(row.value("amount")));
            param.put("expenseDate", DataPortValues.date(row.value("expenseDate")).toString());
            param.put("description", DataPortValues.blankToNull(row.value("description")));
            expenseService.create(DataPortImports.sanitized(param), email);
        });
    }
}
