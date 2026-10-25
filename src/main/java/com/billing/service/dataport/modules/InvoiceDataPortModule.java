package com.billing.service.dataport.modules;

import com.billing.dto.dataport.DataPortRow;
import com.billing.dto.dataport.ImportResult;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.PaymentModeMaster;
import com.billing.entity.Product;
import com.billing.service.InvoiceService;
import com.billing.service.dataport.DataPortField;
import com.billing.service.dataport.DataPortImports;
import com.billing.service.dataport.DataPortLookups;
import com.billing.service.dataport.DataPortModule;
import com.billing.service.dataport.DataPortValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.billing.service.dataport.DataPortField.Type.DATE;
import static com.billing.service.dataport.DataPortField.Type.DECIMAL;
import static com.billing.service.dataport.DataPortField.Type.NUMBER;
import static com.billing.service.dataport.DataPortField.Type.TEXT;

/**
 * Invoices: ek row = ek item; same Invoice Ref wali rows = ek invoice.
 * InvoiceService.create se banta hai (Create Invoice page wala rasta):
 * invoice number, GST, stock FIFO allocation, customer balance, initial payment.
 */
@Component
@RequiredArgsConstructor
public class InvoiceDataPortModule implements DataPortModule<InvoiceDataPortModule.Context> {

    private static final List<String> HEADER_FIELDS = List.of("invoiceDate", "customerMobile", "invoiceDiscount", "paidAmount", "paymentMode", "referenceNo");

    private final DataPortLookups lookups;
    private final InvoiceService invoiceService;

    record Context(Map<String, Customer> customersByMobile,
                   Map<String, Product> productsBySku,
                   Map<Long, BigDecimal> taxRates,
                   Map<Long, Integer> stock,
                   Map<String, PaymentModeMaster> paymentModes,
                   Map<String, List<Map.Entry<BigDecimal, String>>> existingInvoices) {
    }

    @Override
    public String key() {
        return "invoices";
    }

    @Override
    public String menuCode() {
        return "INVOICE_DATAPORT";
    }

    @Override
    public String title() {
        return "Invoices";
    }

    @Override
    public String description() {
        return "Bring in past sales. Each invoice gets the next invoice number, GST is calculated from the product's tax, and stock is reduced.";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public String groupField() {
        return "invoiceRef";
    }

    @Override
    public List<DataPortField> fields() {
        return List.of(
                DataPortField.required("invoiceRef", "Invoice Ref", TEXT, "Your own reference. Rows with the same ref become one invoice. The real invoice number is generated."),
                DataPortField.required("invoiceDate", "Invoice Date", DATE, "Same for every row of an invoice. Cannot be in the future."),
                DataPortField.required("customerMobile", "Customer Mobile", TEXT, "Must match an active customer."),
                DataPortField.required("sku", "SKU", TEXT, "Must match an active product with enough stock."),
                DataPortField.required("qty", "Qty", NUMBER, "1 or more."),
                DataPortField.required("rate", "Rate", DECIMAL, "Selling price per unit before GST."),
                DataPortField.optional("itemDiscount", "Item Discount", DECIMAL, "Amount (₹) off this line. Blank = 0."),
                DataPortField.optional("invoiceDiscount", "Invoice Discount", DECIMAL, "Amount (₹) off the whole invoice. Same on every row."),
                DataPortField.optional("paidAmount", "Paid Amount", DECIMAL, "Collected at billing time. Same on every row. Blank = 0 (unpaid)."),
                DataPortField.optional("paymentMode", "Payment Mode", TEXT, "Required when Paid Amount > 0. Offline modes only (e.g. Cash)."),
                DataPortField.optional("referenceNo", "Reference No", TEXT, "Required if the payment mode needs a reference."));
    }

    @Override
    public List<String> notes() {
        return List.of(
                "Import Customers, Products and Opening Stock first.",
                "An invoice is saved only when all of its rows are valid.",
                "GST (CGST/SGST or IGST) is calculated automatically from the product tax and customer state.");
    }

    @Override
    public List<List<String>> sampleRows() {
        return List.of(
                List.of("S-001", "06-04-2026", "9810000001", "DP-NB-001", "50", "55", "", "", "3000", "Cash", ""),
                List.of("S-001", "06-04-2026", "9810000001", "DP-PEN-001", "20", "100", "100", "", "3000", "Cash", ""),
                List.of("S-002", "07-04-2026", "9810000002", "DP-SKN-001", "2", "149", "", "", "500", "Cash", ""),
                List.of("S-002", "07-04-2026", "9810000002", "DP-HAI-001", "1", "260", "", "", "500", "Cash", ""),
                List.of("S-003", "08-04-2026", "9810000003", "DP-MOB-001", "10", "999", "", "500", "10000", "Cash", ""),
                List.of("S-003", "08-04-2026", "9810000003", "DP-MOB-002", "20", "199", "", "500", "10000", "Cash", ""),
                List.of("S-003", "08-04-2026", "9810000003", "DP-CMP-001", "5", "699", "", "500", "10000", "Cash", ""),
                List.of("S-004", "09-04-2026", "9810000005", "DP-NB-002", "30", "90", "", "", "", "", ""),
                List.of("S-004", "09-04-2026", "9810000005", "DP-FILE-001", "20", "70", "", "", "", "", ""),
                List.of("S-004", "09-04-2026", "9810000005", "DP-ART-001", "10", "130", "", "", "", "", ""),
                List.of("S-005", "10-04-2026", "9810000007", "DP-SNK-001", "10", "299", "", "", "5000", "Cash", ""),
                List.of("S-005", "10-04-2026", "9810000007", "DP-BEV-001", "10", "349", "", "", "5000", "Cash", ""),
                List.of("S-005", "10-04-2026", "9810000007", "DP-STP-001", "5", "699", "", "", "5000", "Cash", ""),
                List.of("S-006", "12-04-2026", "9810000011", "DP-CMP-002", "5", "599", "", "", "", "", ""),
                List.of("S-006", "12-04-2026", "9810000011", "DP-AUD-001", "4", "1799", "200", "", "", "", ""),
                List.of("S-007", "14-04-2026", "9810000013", "DP-CKW-001", "4", "1199", "", "", "2000", "Cash", ""),
                List.of("S-007", "14-04-2026", "9810000013", "DP-STO-001", "6", "399", "", "", "2000", "Cash", ""),
                List.of("S-008", "15-04-2026", "9810000015", "DP-TOL-001", "3", "650", "", "", "1000", "Cash", ""),
                List.of("S-008", "15-04-2026", "9810000015", "DP-CLN-001", "10", "199", "", "", "1000", "Cash", ""),
                List.of("S-009", "16-04-2026", "9810000006", "DP-PEN-002", "5", "50", "", "", "", "", ""));
    }

    @Override
    public Context buildContext(String email, Company company) {
        Map<String, Product> products = lookups.productsBySku(company);
        return new Context(lookups.customersByMobile(), products, lookups.productTaxRates(company),
                lookups.currentStock(company, products.values()), lookups.offlinePaymentModes(), lookups.existingInvoices(company));
    }

    @Override
    public void validate(List<DataPortRow> rows, Context context) {
        DataPortValues.markDuplicates(rows, "sku",
                row -> DataPortValues.key(row.value("invoiceRef")) + "::" + DataPortValues.key(row.value("sku")),
                "Same SKU is repeated inside this invoice — combine the quantity in one row");
        GroupRules.sameHeaderValues(rows, groupField(), HEADER_FIELDS);

        for (DataPortRow row : rows) {
            validateLine(row, context);
        }

        // Stock poori file ke hisaab se: pehle invoice ne jo le liya wo agle ko nahi milega.
        Map<Long, Integer> remaining = new HashMap<>(context.stock());
        for (DataPortRow row : rows) {
            Long productId = row.resolvedValue("productId");
            Integer qty = DataPortValues.integer(row.value("qty"));
            if (productId == null || qty == null || qty < 1) {
                continue;
            }
            int available = remaining.getOrDefault(productId, 0);
            if (qty > available) {
                row.error("qty", "Only " + available + " in stock for this SKU (after earlier rows of this file)");
            } else {
                remaining.put(productId, available - qty);
            }
        }

        for (List<DataPortRow> group : GroupRules.groups(rows, groupField()).values()) {
            validateTotals(group, context);
        }
    }

    private void validateLine(DataPortRow row, Context context) {
        String mobile = row.value("customerMobile");
        if (!mobile.isEmpty()) {
            Customer customer = context.customersByMobile().get(DataPortLookups.normalizeMobile(mobile));
            if (customer == null) {
                row.error("customerMobile", "No customer with this mobile — import Customers first");
            } else if (!customer.isActive()) {
                row.error("customerMobile", "Customer is inactive");
            } else {
                row.getResolved().put("customerId", customer.getId());
            }
        }
        if (!row.value("sku").isEmpty()) {
            Product product = context.productsBySku().get(DataPortValues.key(row.value("sku")));
            if (product == null) {
                row.error("sku", "No product with this SKU");
            } else if (!product.isActive()) {
                row.error("sku", "Product is inactive");
            } else {
                row.getResolved().put("productId", product.getId());
            }
        }
        Integer qty = DataPortValues.integer(row.value("qty"));
        if (qty != null && qty < 1) {
            row.error("qty", "Qty must be 1 or more");
        }
        BigDecimal rate = DataPortValues.decimal(row.value("rate"));
        if (rate != null && rate.signum() < 0) {
            row.error("rate", "Rate must be 0 or more");
        }
        BigDecimal itemDiscount = DataPortValues.decimal(row.value("itemDiscount"));
        if (itemDiscount != null && itemDiscount.signum() < 0) {
            row.error("itemDiscount", "Discount must be 0 or more");
        } else if (itemDiscount != null && qty != null && rate != null
                && itemDiscount.compareTo(rate.multiply(BigDecimal.valueOf(qty))) > 0) {
            row.error("itemDiscount", "Discount cannot be more than the line amount");
        }
        LocalDate date = DataPortValues.date(row.value("invoiceDate"));
        if (date != null && date.isAfter(LocalDate.now())) {
            row.error("invoiceDate", "Invoice date cannot be in the future");
        }
        BigDecimal invoiceDiscount = DataPortValues.decimal(row.value("invoiceDiscount"));
        if (invoiceDiscount != null && invoiceDiscount.signum() < 0) {
            row.error("invoiceDiscount", "Discount must be 0 or more");
        }
        BigDecimal paid = DataPortValues.decimal(row.value("paidAmount"));
        if (paid != null && paid.signum() < 0) {
            row.error("paidAmount", "Paid amount must be 0 or more");
        }
        String modeName = row.value("paymentMode");
        if (paid != null && paid.signum() > 0 && modeName.isEmpty()) {
            row.error("paymentMode", "Payment mode is required when Paid Amount is more than 0");
        }
        if (!modeName.isEmpty()) {
            PaymentModeMaster mode = context.paymentModes().get(DataPortValues.key(modeName));
            if (mode == null) {
                row.error("paymentMode", "Unknown or inactive offline payment mode (online payments are collected through the gateway)");
            } else {
                row.getResolved().put("paymentModeCode", mode.getModeCode());
                if (mode.isRequiresReference() && paid != null && paid.signum() > 0 && row.value("referenceNo").isEmpty()) {
                    row.error("referenceNo", "Reference No is required for " + mode.getModeName());
                }
            }
        }
    }

    /**
     * Invoice total ka andaza (service jaisa: invoice discount GST se pehle,
     * lines par proportion me) — paid > total, discount > amount, aur
     * "ye invoice pehle hi import ho chuka" check ke liye. Exact total service banati hai.
     */
    private void validateTotals(List<DataPortRow> group, Context context) {
        BigDecimal taxableTotal = BigDecimal.ZERO;
        BigDecimal grossTotal = BigDecimal.ZERO;
        for (DataPortRow row : group) {
            Long productId = row.resolvedValue("productId");
            Integer qty = DataPortValues.integer(row.value("qty"));
            BigDecimal rate = DataPortValues.decimal(row.value("rate"));
            // Line me hi galti ho to total ka andaza bematlab (ulta "-₹30" jaisa message aata tha).
            if (productId == null || qty == null || rate == null || row.getValidationErrors().containsKey("itemDiscount")) {
                return;
            }
            BigDecimal discount = DataPortValues.decimal(row.value("itemDiscount"));
            BigDecimal taxable = rate.multiply(BigDecimal.valueOf(qty)).subtract(discount == null ? BigDecimal.ZERO : discount);
            BigDecimal taxRate = context.taxRates().getOrDefault(productId, BigDecimal.ZERO);
            taxableTotal = taxableTotal.add(taxable);
            grossTotal = grossTotal.add(taxable.add(taxable.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)));
        }
        DataPortRow first = group.get(0);
        BigDecimal invoiceDiscount = DataPortValues.decimal(first.value("invoiceDiscount"));
        BigDecimal discount = invoiceDiscount == null ? BigDecimal.ZERO : invoiceDiscount;
        BigDecimal taxableAmount = taxableTotal;
        if (discount.compareTo(taxableAmount) > 0) {
            group.forEach(row -> row.error("invoiceDiscount", "Invoice discount is more than the invoice amount (₹" + taxableAmount.setScale(2, RoundingMode.HALF_UP) + " before GST)"));
            return;
        }
        BigDecimal net = taxableAmount.signum() == 0 ? BigDecimal.ZERO
                : grossTotal.multiply(taxableAmount.subtract(discount)).divide(taxableAmount, 2, RoundingMode.HALF_UP);
        BigDecimal paid = DataPortValues.decimal(first.value("paidAmount"));
        if (paid != null && paid.compareTo(net.add(BigDecimal.ONE)) > 0) {
            group.forEach(row -> row.error("paidAmount", "Paid amount is more than the invoice total (about ₹" + net + ")"));
        }

        // Wahi file dobara: same customer + date + total ka invoice pehle se hai to dobara na bane.
        Long customerId = first.resolvedValue("customerId");
        LocalDate date = DataPortValues.date(first.value("invoiceDate"));
        if (customerId != null && date != null) {
            context.existingInvoices().getOrDefault(DataPortLookups.invoiceKey(customerId, date), List.of()).stream()
                    .filter(existing -> existing.getKey().subtract(net).abs().compareTo(BigDecimal.ONE) <= 0)
                    .findFirst()
                    .ifPresent(existing -> group.forEach(row -> row.error("invoiceRef",
                            "Looks already imported as " + existing.getValue() + " (same customer, date and total ₹" + existing.getKey() + ")")));
        }
    }

    @Override
    public ImportResult importRows(String email, Company company, List<DataPortRow> validRows, Context context) {
        return DataPortImports.perGroup(validRows, groupField(), group -> {
            DataPortRow first = group.get(0);
            BigDecimal paid = DataPortValues.money(first.value("paidAmount"));
            boolean hasPayment = paid != null && paid.signum() > 0;
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("customerId", first.resolvedValue("customerId"));
            param.put("invoiceDate", DataPortValues.date(first.value("invoiceDate")).toString());
            BigDecimal invoiceDiscount = DataPortValues.money(first.value("invoiceDiscount"));
            param.put("discountAmount", invoiceDiscount == null ? BigDecimal.ZERO : invoiceDiscount);
            param.put("paidAmount", hasPayment ? paid : BigDecimal.ZERO);
            if (hasPayment) {
                param.put("paymentMode", first.resolvedValue("paymentModeCode"));
                param.put("referenceNo", DataPortValues.blankToNull(first.value("referenceNo")));
            }
            List<Map<String, Object>> items = new ArrayList<>();
            for (DataPortRow row : group) {
                int qty = DataPortValues.integer(row.value("qty"));
                BigDecimal rate = DataPortValues.money(row.value("rate"));
                BigDecimal discount = DataPortValues.money(row.value("itemDiscount"));
                discount = discount == null ? BigDecimal.ZERO : discount;
                BigDecimal lineBase = rate.multiply(BigDecimal.valueOf(qty));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("productId", row.resolvedValue("productId"));
                item.put("qty", qty);
                item.put("price", rate);
                // Create Invoice page jaisa hi: FIXED discount + uska % (2 decimal).
                item.put("discountType", "FIXED");
                item.put("discountValue", discount);
                item.put("discountPercent", lineBase.signum() <= 0 ? BigDecimal.ZERO
                        : discount.multiply(BigDecimal.valueOf(100)).divide(lineBase, 2, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100)));
                items.add(item);
            }
            param.put("items", items);
            invoiceService.createImported(DataPortImports.sanitized(param), email);
        });
    }
}
