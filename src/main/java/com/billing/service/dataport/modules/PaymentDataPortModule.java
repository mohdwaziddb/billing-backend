package com.billing.service.dataport.modules;

import com.billing.dto.dataport.DataPortRow;
import com.billing.dto.dataport.ImportResult;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.PaymentModeMaster;
import com.billing.service.PaymentService;
import com.billing.service.dataport.DataPortField;
import com.billing.service.dataport.DataPortImports;
import com.billing.service.dataport.DataPortLookups;
import com.billing.service.dataport.DataPortModule;
import com.billing.service.dataport.DataPortValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.billing.service.dataport.DataPortField.Type.DATE;
import static com.billing.service.dataport.DataPortField.Type.DECIMAL;
import static com.billing.service.dataport.DataPortField.Type.TEXT;

/**
 * Payments received. PaymentService.create se (Payment Entry page wala rasta):
 * invoice diya ho to usi par, warna customer ke sabse purane open invoices par FIFO.
 */
@Component
@RequiredArgsConstructor
public class PaymentDataPortModule implements DataPortModule<PaymentDataPortModule.Context> {

    private final DataPortLookups lookups;
    private final PaymentService paymentService;

    record Context(Map<String, Customer> customersByMobile, Map<String, PaymentModeMaster> paymentModes, java.util.Set<String> existingPayments) {
    }

    @Override
    public String key() {
        return "payments";
    }

    @Override
    public String menuCode() {
        return "PAYMENT_DATAPORT";
    }

    @Override
    public String title() {
        return "Payments";
    }

    @Override
    public String description() {
        return "Record payments received from customers. Without an invoice number the amount is adjusted against the oldest unpaid invoices first.";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public List<DataPortField> fields() {
        return List.of(
                DataPortField.required("customerMobile", "Customer Mobile", TEXT, "Customer must have outstanding balance."),
                DataPortField.optional("invoiceNo", "Invoice No", TEXT, "Optional, e.g. INV/2627/00001. Must belong to the customer."),
                DataPortField.required("paymentDate", "Payment Date", DATE, "Cannot be in the future."),
                DataPortField.required("amount", "Amount", DECIMAL, "Cannot be more than the outstanding / invoice balance."),
                DataPortField.required("paymentMode", "Payment Mode", TEXT, "Offline modes only (e.g. Cash). Online payments come through the gateway."),
                DataPortField.optional("referenceNo", "Reference No", TEXT, "Required if the payment mode needs a reference."),
                DataPortField.optional("remarks", "Remarks", TEXT, null));
    }

    @Override
    public List<String> notes() {
        return List.of("Import Invoices first — a payment needs outstanding balance to settle.");
    }

    @Override
    public List<List<String>> sampleRows() {
        return List.of(
                List.of("9810000001", "", "10-04-2026", "1000", "Cash", "", "Part payment"),
                List.of("9810000001", "", "20-04-2026", "1000", "Cash", "", "Part payment"),
                List.of("9810000002", "", "12-04-2026", "150", "Cash", "", ""),
                List.of("9810000003", "", "15-04-2026", "5000", "Cash", "", ""),
                List.of("9810000003", "", "25-04-2026", "3000", "Cash", "", ""),
                List.of("9810000005", "", "16-04-2026", "3000", "Cash", "", ""),
                List.of("9810000005", "", "28-04-2026", "2000", "Cash", "", "Balance next month"),
                List.of("9810000007", "", "18-04-2026", "2500", "Cash", "", ""),
                List.of("9810000011", "", "19-04-2026", "5000", "Cash", "", ""),
                List.of("9810000011", "", "29-04-2026", "4000", "Cash", "", ""),
                List.of("9810000013", "", "20-04-2026", "3000", "Cash", "", ""),
                List.of("9810000013", "", "30-04-2026", "1500", "Cash", "", ""),
                List.of("9810000015", "", "21-04-2026", "2000", "Cash", "", ""),
                List.of("9810000015", "", "30-04-2026", "1000", "Cash", "", ""),
                List.of("9810000006", "", "22-04-2026", "200", "Cash", "", ""));
    }

    @Override
    public Context buildContext(String email, Company company) {
        return new Context(lookups.customersByMobile(), lookups.offlinePaymentModes(), lookups.existingPayments(company));
    }

    @Override
    public void validate(List<DataPortRow> rows, Context context) {
        // Poori file ke payments milake outstanding se zyada na hon.
        Map<Long, BigDecimal> customerLeft = new HashMap<>();
        Map<Long, BigDecimal> invoiceLeft = new HashMap<>();
        for (DataPortRow row : rows) {
            Customer customer = null;
            if (!row.value("customerMobile").isEmpty()) {
                customer = context.customersByMobile().get(DataPortLookups.normalizeMobile(row.value("customerMobile")));
                if (customer == null) {
                    row.error("customerMobile", "No customer with this mobile number");
                } else if (!customer.isActive()) {
                    row.error("customerMobile", "Customer is inactive — activate the customer first");
                    customer = null;
                } else {
                    row.getResolved().put("customerId", customer.getId());
                }
            }
            Invoice invoice = null;
            if (!row.value("invoiceNo").isEmpty()) {
                Optional<Invoice> found = lookups.invoiceByNumber(row.value("invoiceNo"));
                if (found.isEmpty()) {
                    row.error("invoiceNo", "Invoice not found");
                } else if (customer != null && !customer.getId().equals(found.get().getCustomer().getId())) {
                    row.error("invoiceNo", "This invoice belongs to a different customer");
                } else {
                    invoice = found.get();
                    row.getResolved().put("invoiceId", invoice.getId());
                }
            }
            String modeName = row.value("paymentMode");
            if (!modeName.isEmpty()) {
                PaymentModeMaster mode = context.paymentModes().get(DataPortValues.key(modeName));
                if (mode == null) {
                    row.error("paymentMode", "Unknown or inactive offline payment mode (online payments come through the gateway)");
                } else {
                    row.getResolved().put("paymentModeCode", mode.getModeCode());
                    if (mode.isRequiresReference() && row.value("referenceNo").isEmpty()) {
                        row.error("referenceNo", "Reference No is required for " + mode.getModeName());
                    }
                }
            }
            LocalDate date = DataPortValues.date(row.value("paymentDate"));
            if (date != null && date.isAfter(LocalDate.now())) {
                row.error("paymentDate", "Payment date cannot be in the future");
            }
            BigDecimal amount = DataPortValues.decimal(row.value("amount"));
            if (amount == null || customer == null) {
                continue;
            }
            if (amount.signum() <= 0) {
                row.error("amount", "Amount must be greater than 0");
                continue;
            }
            // Wahi file dobara upload: same customer + date + amount + mode pehle se recorded hai.
            String modeCode = row.resolvedValue("paymentModeCode");
            if (date != null && modeCode != null
                    && context.existingPayments().contains(DataPortLookups.paymentKey(customer.getId(), date, amount, modeCode))) {
                row.error("amount", "This payment is already recorded (same customer, date, amount and mode)");
                continue;
            }
            BigDecimal outstanding = customerLeft.containsKey(customer.getId())
                    ? customerLeft.get(customer.getId())
                    : (customer.getCurrentBalance() == null ? BigDecimal.ZERO : customer.getCurrentBalance());
            if (amount.compareTo(outstanding) > 0) {
                row.error("amount", "More than the customer's outstanding balance (₹"
                        + outstanding.setScale(2, java.math.RoundingMode.HALF_UP) + " left"
                        + (customerLeft.containsKey(customer.getId()) ? " after earlier rows of this file)" : ")"));
                continue;
            }
            if (invoice != null) {
                BigDecimal invoiceBalance = invoiceLeft.getOrDefault(invoice.getId(), balanceOf(invoice));
                if (amount.compareTo(invoiceBalance) > 0) {
                    row.error("amount", "More than the invoice balance (₹" + invoiceBalance.setScale(2, java.math.RoundingMode.HALF_UP) + " left)");
                    continue;
                }
                invoiceLeft.put(invoice.getId(), invoiceBalance.subtract(amount));
            }
            customerLeft.put(customer.getId(), outstanding.subtract(amount));
        }
    }

    private BigDecimal balanceOf(Invoice invoice) {
        return invoice.getBalanceAmount() == null ? BigDecimal.ZERO : invoice.getBalanceAmount();
    }

    @Override
    public ImportResult importRows(String email, Company company, List<DataPortRow> validRows, Context context) {
        return DataPortImports.perRow(validRows, row -> {
            Map<String, Object> param = new LinkedHashMap<>();
            param.put("customerId", row.resolvedValue("customerId"));
            param.put("invoiceId", row.resolvedValue("invoiceId"));
            param.put("amount", DataPortValues.money(row.value("amount")));
            param.put("paymentDate", DataPortValues.date(row.value("paymentDate")).toString());
            param.put("mode", row.resolvedValue("paymentModeCode"));
            param.put("referenceNo", DataPortValues.blankToNull(row.value("referenceNo")));
            param.put("remarks", DataPortValues.blankToNull(row.value("remarks")));
            paymentService.create(DataPortImports.sanitized(param), email);
        });
    }
}
