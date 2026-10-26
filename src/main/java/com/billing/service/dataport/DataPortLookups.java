package com.billing.service.dataport;

import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.ExpenseCategory;
import com.billing.entity.Invoice;
import com.billing.entity.PaymentModeMaster;
import com.billing.entity.Product;
import com.billing.entity.ProductCategory;
import com.billing.entity.ProductSubCategory;
import com.billing.entity.StateMaster;
import com.billing.entity.TaxMaster;
import com.billing.entity.enums.PaymentModeType;
import com.billing.entity.enums.TaxType;
import com.billing.repository.CustomerRepository;
import com.billing.repository.ExpenseCategoryRepository;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentModeMasterRepository;
import com.billing.repository.ProductCategoryRepository;
import com.billing.repository.ProductRepository;
import com.billing.repository.ProductSubCategoryRepository;
import com.billing.repository.StateMasterRepository;
import com.billing.repository.TaxMasterRepository;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DataPort modules ke common lookups (naam/SKU/mobile/invoice no -> record).
 * Ek preview me ek hi baar load hote hain (context), har row par DB query nahi.
 */
@Component
@RequiredArgsConstructor
public class DataPortLookups {

    private final ProductCategoryRepository productCategoryRepository;
    private final ProductSubCategoryRepository productSubCategoryRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final StateMasterRepository stateMasterRepository;
    private final TaxMasterRepository taxMasterRepository;
    private final PaymentModeMasterRepository paymentModeMasterRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final InvoiceRepository invoiceRepository;
    private final com.billing.repository.PurchaseRepository purchaseRepository;
    private final com.billing.repository.PaymentRepository paymentRepository;
    private final com.billing.repository.ExpenseRepository expenseRepository;
    private final com.billing.repository.ProductBatchRepository productBatchRepository;

    /** IN (...) list me ek query me itne invoice numbers tak. */
    private static final int INVOICE_LOOKUP_BATCH = 500;

    /** Category naam (lowercase) -> category (active + inactive dono, rule module decide kare). */
    @Transactional(readOnly = true)
    public Map<String, ProductCategory> productCategories(Company company) {
        return productCategoryRepository.findAllByCompanyWithFilters(company, null, null).stream()
                .collect(Collectors.toMap(category -> DataPortValues.key(category.getCategoryName()), Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    /** "categoryId::subName" -> sub category. */
    @Transactional(readOnly = true)
    public Map<String, ProductSubCategory> productSubCategories(Company company) {
        return productSubCategoryRepository.findAllByCompanyWithFilters(company, null, null, null).stream()
                .collect(Collectors.toMap(sub -> subKey(sub.getProductCategory().getId(), sub.getSubCategoryName()), Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    public static String subKey(Long categoryId, String subCategoryName) {
        return (categoryId == null ? "" : categoryId) + "::" + DataPortValues.key(subCategoryName);
    }

    /** SKU (lowercase) -> product. */
    @Transactional(readOnly = true)
    public Map<String, Product> productsBySku(Company company) {
        return productRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .filter(product -> product.getSku() != null)
                .collect(Collectors.toMap(product -> DataPortValues.key(product.getSku()), Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    /** Product id -> GST rate (taxMaster lazy hai, isliye transaction ke andar padho). */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> productTaxRates(Company company) {
        Map<Long, BigDecimal> rates = new LinkedHashMap<>();
        for (Product product : productRepository.findByCompanyOrderByCreatedAtDesc(company)) {
            BigDecimal rate = product.isTaxable() && product.getTaxMaster() != null && product.getTaxMaster().getRate() != null
                    ? product.getTaxMaster().getRate()
                    : BigDecimal.ZERO;
            rates.put(product.getId(), rate);
        }
        return rates;
    }

    /**
     * Product id -> abhi ka stock (saare batches ka remaining qty). Ek GROUP BY query
     * (pehle har product par alag query — har revalidate par N queries).
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> currentStock(Company company, java.util.Collection<Product> products) {
        Map<Long, Integer> remainingByProduct = new java.util.HashMap<>();
        for (Object[] stockRow : productBatchRepository.sumRemainingQtyGroupByProduct()) {
            remainingByProduct.put(DataTypeUtility.longValue(stockRow[0]), DataTypeUtility.integerValue(stockRow[1]));
        }
        Map<Long, Integer> stock = new LinkedHashMap<>();
        for (Product product : products) {
            stock.put(product.getId(), remainingByProduct.getOrDefault(product.getId(), 0));
        }
        return stock;
    }

    // ---- Duplicate guards: wahi file dobara upload ho to transaction dobara na bane. ----

    public static String purchaseLineKey(java.time.LocalDate date, String supplier, Long productId, Integer qty, BigDecimal rate) {
        return date + "|" + DataPortValues.key(supplier) + "|" + productId + "|" + qty + "|" + scale2(rate);
    }

    public static String paymentKey(Long customerId, java.time.LocalDate date, BigDecimal amount, String modeCode) {
        return customerId + "|" + date + "|" + scale2(amount) + "|" + DataPortValues.key(modeCode);
    }

    public static String expenseKey(java.time.LocalDate date, Long categoryId, BigDecimal amount, String description) {
        return date + "|" + categoryId + "|" + scale2(amount) + "|" + DataPortValues.key(description);
    }

    public static String invoiceKey(Long customerId, java.time.LocalDate date) {
        return customerId + "|" + date;
    }

    private static String scale2(BigDecimal value) {
        return value == null ? "" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** Purchase line key -> purchase no (active purchases). Ek projection query (pehle har purchase ke items lazy = N+1). */
    @Transactional(readOnly = true)
    public Map<String, String> existingPurchaseLines(Company company) {
        Map<String, String> lines = new LinkedHashMap<>();
        for (Object[] lineRow : purchaseRepository.findActivePurchaseLineKeys()) {
            java.time.LocalDate purchaseDate = (java.time.LocalDate) lineRow[0];
            String supplierName = lineRow[1] == null ? null : DataTypeUtility.stringValue(lineRow[1]);
            Long productId = lineRow[2] == null ? null : DataTypeUtility.longValue(lineRow[2]);
            Integer qty = lineRow[3] == null ? null : DataTypeUtility.integerValue(lineRow[3]);
            BigDecimal purchaseRate = DataTypeUtility.bigDecimalObjectValue(lineRow[4]);
            String purchaseNo = lineRow[5] == null ? null : DataTypeUtility.stringValue(lineRow[5]);
            lines.putIfAbsent(purchaseLineKey(purchaseDate, supplierName, productId, qty, purchaseRate), purchaseNo);
        }
        return lines;
    }

    /**
     * Customer|date -> [grand total, invoice no] (deleted chhod ke). Sirf 4 columns ki
     * projection — pehle har preview/revalidate par poori invoices table items+product+tax
     * graph samet memory me aati thi.
     */
    @Transactional(readOnly = true)
    public Map<String, List<Map.Entry<BigDecimal, String>>> existingInvoices(Company company) {
        Map<String, List<Map.Entry<BigDecimal, String>>> invoices = new LinkedHashMap<>();
        for (Object[] invoiceRow : invoiceRepository.findActiveDuplicateGuardRows()) {
            Long customerId = DataTypeUtility.longValue(invoiceRow[0]);
            java.time.LocalDate invoiceDate = (java.time.LocalDate) invoiceRow[1];
            BigDecimal grandTotal = DataTypeUtility.bigDecimalObjectValue(invoiceRow[2]);
            String invoiceNo = invoiceRow[3] == null ? null : DataTypeUtility.stringValue(invoiceRow[3]);
            invoices.computeIfAbsent(invoiceKey(customerId, invoiceDate), ignored -> new java.util.ArrayList<>())
                    .add(Map.entry(grandTotal == null ? BigDecimal.ZERO : grandTotal, invoiceNo));
        }
        return invoices;
    }

    @Transactional(readOnly = true)
    public java.util.Set<String> existingPayments(Company company) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (var payment : paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company)) {
            if (!payment.isDeleted() && payment.getCustomer() != null) {
                keys.add(paymentKey(payment.getCustomer().getId(), payment.getPaymentDate(), payment.getAmount(), payment.getMode()));
            }
        }
        return keys;
    }

    @Transactional(readOnly = true)
    public java.util.Set<String> existingExpenses(Company company) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (var expense : expenseRepository.findByCompanyOrderByExpenseDateDescIdDesc(company)) {
            if (expense.getCategory() != null) {
                keys.add(expenseKey(expense.getExpenseDate(), expense.getCategory().getId(), expense.getAmount(), expense.getDescription()));
            }
        }
        return keys;
    }

    /** 10-digit mobile -> customer. */
    @Transactional(readOnly = true)
    public Map<String, Customer> customersByMobile() {
        return customerRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(customer -> customer.getMobile() != null)
                .collect(Collectors.toMap(customer -> normalizeMobile(customer.getMobile()), Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public Map<String, Customer> customersByEmail() {
        return customerRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(customer -> customer.getEmail() != null && !customer.getEmail().isBlank())
                .collect(Collectors.toMap(customer -> DataPortValues.key(customer.getEmail()), Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    /** State naam ya code (lowercase) -> active state. */
    @Transactional(readOnly = true)
    public Map<String, StateMaster> activeStates() {
        Map<String, StateMaster> states = new LinkedHashMap<>();
        for (StateMaster state : stateMasterRepository.findByActiveTrueOrderByCountryNameAscStateNameAsc()) {
            states.putIfAbsent(DataPortValues.key(state.getStateName()), state);
            if (state.getStateCode() != null) {
                states.putIfAbsent(DataPortValues.key(state.getStateCode()), state);
            }
        }
        return states;
    }

    /** Rate -> active tax (GST pehle). Product import yahi tax lagata hai. */
    @Transactional(readOnly = true)
    public Map<BigDecimal, TaxMaster> activeTaxesByRate(Company company) {
        Map<BigDecimal, TaxMaster> byRate = new LinkedHashMap<>();
        taxMasterRepository.findByCompanyAndDeletedFalseOrderByTaxNameAsc(company).stream()
                .filter(TaxMaster::isActive)
                .filter(tax -> tax.getRate() != null)
                .sorted(Comparator.comparing((TaxMaster tax) -> tax.getTaxType() == TaxType.GST ? 0 : 1)
                        .thenComparing(tax -> tax.isDefaultTax() ? 0 : 1))
                .forEach(tax -> byRate.putIfAbsent(tax.getRate().stripTrailingZeros(), tax));
        return byRate;
    }

    /** Offline active modes, naam ya code (lowercase) -> mode. Online sirf gateway se. */
    @Transactional(readOnly = true)
    public Map<String, PaymentModeMaster> offlinePaymentModes() {
        Map<String, PaymentModeMaster> modes = new LinkedHashMap<>();
        for (PaymentModeMaster mode : paymentModeMasterRepository.findAll()) {
            if (!mode.isActive() || mode.effectiveModeType() == PaymentModeType.ONLINE) {
                continue;
            }
            if (mode.getModeCode() != null) {
                modes.putIfAbsent(DataPortValues.key(mode.getModeCode()), mode);
            }
            if (mode.getModeName() != null) {
                modes.putIfAbsent(DataPortValues.key(mode.getModeName()), mode);
            }
        }
        return modes;
    }

    @Transactional(readOnly = true)
    public Map<String, ExpenseCategory> expenseCategories(Company company) {
        return expenseCategoryRepository.findByCompanyOrderByCategoryNameAsc(company).stream()
                .collect(Collectors.toMap(category -> DataPortValues.key(category.getCategoryName()), Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public Optional<Invoice> invoiceByNumber(String invoiceNo) {
        if (invoiceNo == null || invoiceNo.isBlank()) {
            return Optional.empty();
        }
        List<Invoice> matches = invoiceRepository.findActiveByInvoiceNo(invoiceNo.trim());
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    /**
     * File ki saari rows ke invoice numbers ek hi query me (pehle har row par
     * invoiceByNumber = 5000 rows tak 5000 queries). Result invoiceByNumber jaisa:
     * exactly ek active match ho tabhi invoice, warna empty.
     */
    @Transactional(readOnly = true)
    public Function<String, Optional<Invoice>> invoicesByNumber(List<com.billing.dto.dataport.DataPortRow> rows, String fieldKey) {
        java.util.Set<String> invoiceNos = new java.util.LinkedHashSet<>();
        for (com.billing.dto.dataport.DataPortRow row : rows) {
            String invoiceNo = row.value(fieldKey);
            if (!invoiceNo.isBlank()) {
                invoiceNos.add(invoiceNo.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        Map<String, List<Invoice>> matchesByNumber = new java.util.HashMap<>();
        List<String> pendingNos = new java.util.ArrayList<>(invoiceNos);
        for (int start = 0; start < pendingNos.size(); start += INVOICE_LOOKUP_BATCH) {
            List<String> batchNos = pendingNos.subList(start, Math.min(start + INVOICE_LOOKUP_BATCH, pendingNos.size()));
            for (Invoice invoice : invoiceRepository.findActiveByInvoiceNoLowerIn(batchNos)) {
                String matchKey = invoice.getInvoiceNo().trim().toLowerCase(java.util.Locale.ROOT);
                matchesByNumber.computeIfAbsent(matchKey, ignored -> new java.util.ArrayList<>()).add(invoice);
            }
        }
        return invoiceNo -> {
            if (invoiceNo == null || invoiceNo.isBlank()) {
                return Optional.empty();
            }
            List<Invoice> matches = matchesByNumber.getOrDefault(invoiceNo.trim().toLowerCase(java.util.Locale.ROOT), List.of());
            return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
        };
    }

    /** "+91 98765-43210" -> "9876543210" (frontend customerValidation jaisa). */
    public static String normalizeMobile(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        return digits.length() == 12 && digits.startsWith("91") ? digits.substring(2) : digits;
    }
}
