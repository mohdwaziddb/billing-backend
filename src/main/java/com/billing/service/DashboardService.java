package com.billing.service;

import com.billing.dto.dashboard.DashboardDetailResponse;
import com.billing.dto.dashboard.DashboardSummaryResponse;
import com.billing.dto.dashboard.DashboardTopCustomerResponse;
import com.billing.entity.Company;
import com.billing.entity.Expense;
import com.billing.entity.Payment;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.entity.User;
import com.billing.repository.CustomerRepository;
import com.billing.repository.ExpenseRepository;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentRepository;
import com.billing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final AccessControlService accessControlService;
    private final ExpenseRepository expenseRepository;
    private final RevenueCalculationService revenueCalculationService;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(String email, LocalDate startDate, LocalDate endDate) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        List<Invoice> allInvoices = invoicesFor(company);
        List<Payment> allPayments = paymentsFor(company);
        List<Customer> allCustomers = customersFor(company);
        LocalDate safeStart = startDate;
        LocalDate safeEnd = endDate;
        BigDecimal totalExpense = expensesFor(company).stream()
                .filter(expense -> isWithinRange(expense.getExpenseDate(), safeStart, safeEnd))
                .map(com.billing.entity.Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Invoice> filteredInvoices = allInvoices.stream()
                .filter(invoice -> isWithinRange(invoice.getInvoiceDate(), safeStart, safeEnd))
                .toList();
        List<Payment> filteredPayments = allPayments.stream()
                .filter(payment -> isWithinRange(payment.getPaymentDate(), safeStart, safeEnd))
                .toList();
        LocalDate previousStart = previousRangeStart(safeStart, safeEnd);
        LocalDate previousEnd = previousRangeEnd(safeStart);
        List<Invoice> previousInvoices = allInvoices.stream()
                .filter(invoice -> isWithinRange(invoice.getInvoiceDate(), previousStart, previousEnd))
                .toList();
        List<Payment> previousPayments = allPayments.stream()
                .filter(payment -> isWithinRange(payment.getPaymentDate(), previousStart, previousEnd))
                .toList();
        Map<Long, LocalDate> firstPurchaseDates = firstPurchaseDates(allInvoices);
        List<Long> periodCustomerIds = filteredInvoices.stream()
                .map(invoice -> invoice.getCustomer().getId())
                .distinct()
                .toList();
        List<Long> previousCustomerIds = previousInvoices.stream()
                .map(invoice -> invoice.getCustomer().getId())
                .distinct()
                .toList();
        long newCustomers = periodCustomerIds.stream()
                .filter(customerId -> isWithinRange(firstPurchaseDates.get(customerId), safeStart, safeEnd))
                .count();
        long existingCustomers = periodCustomerIds.stream()
                .filter(customerId -> isBeforeStart(firstPurchaseDates.get(customerId), safeStart))
                .count();

        BigDecimal totalSales = filteredInvoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousSales = previousInvoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollection = filteredPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousCollection = previousPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGst = filteredInvoices.stream()
                .map(Invoice::getTaxAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cgstCollected = filteredInvoices.stream()
                .map(Invoice::getCgstTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sgstCollected = filteredInvoices.stream()
                .map(Invoice::getSgstTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal igstCollected = filteredInvoices.stream()
                .map(Invoice::getIgstTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = calculateOutstandingAsOf(allCustomers, allInvoices, allPayments, safeEnd);
        BigDecimal previousOutstanding = calculateOutstandingAsOf(allCustomers, allInvoices, allPayments, previousEnd);
        long soldProducts = filteredInvoices.stream()
                .flatMap(invoice -> invoice.getItems().stream())
                .map(item -> item.getProduct().getId())
                .distinct()
                .count();

        Map<Long, DashboardCustomerAggregate> customerAggregates = new HashMap<>();
        for (Invoice invoice : filteredInvoices) {
            Customer customer = invoice.getCustomer();
            DashboardCustomerAggregate aggregate = customerAggregates.computeIfAbsent(customer.getId(), id -> new DashboardCustomerAggregate(customer));
            aggregate.totalPurchaseAmount = scale(aggregate.totalPurchaseAmount.add(invoice.getTotalAmount()));
            if (aggregate.lastPurchaseDate == null || invoice.getInvoiceDate().isAfter(aggregate.lastPurchaseDate)) {
                aggregate.lastPurchaseDate = invoice.getInvoiceDate();
            }
        }
        for (Payment payment : filteredPayments) {
            DashboardCustomerAggregate aggregate = customerAggregates.computeIfAbsent(payment.getCustomer().getId(), id -> new DashboardCustomerAggregate(payment.getCustomer()));
            aggregate.totalPaidAmount = scale(aggregate.totalPaidAmount.add(payment.getAmount()));
        }

        List<DashboardTopCustomerResponse> topCustomers = customerAggregates.values().stream()
                .sorted(Comparator.comparing(DashboardCustomerAggregate::getTotalPurchaseAmount, Comparator.reverseOrder()))
                .limit(7)
                .map(aggregate -> DashboardTopCustomerResponse.builder()
                        .customerId(aggregate.customer.getId())
                        .customerName(aggregate.customer.getName())
                        .mobile(aggregate.customer.getMobile())
                        .totalPurchaseAmount(scale(aggregate.totalPurchaseAmount))
                        .totalPaidAmount(scale(aggregate.totalPaidAmount))
                        .outstandingBalance(scale(aggregate.customer.getCurrentBalance()))
                        .lastPurchaseDate(aggregate.lastPurchaseDate)
                        .build())
                .toList();

        return DashboardSummaryResponse.builder()
                .startDate(safeStart)
                .endDate(safeEnd)
                .totalSales(scale(totalSales))
                .totalCollection(scale(totalCollection))
                .outstandingAmount(scale(outstanding))
                .totalCustomers(periodCustomerIds.size())
                .newCustomers(newCustomers)
                .existingCustomers(existingCustomers)
                .totalInvoices(filteredInvoices.size())
                .totalProducts(soldProducts)
                .totalRevenue(scale(totalCollection))
                .totalExpense(scale(totalExpense))
                .netRevenue(revenueCalculationService.netRevenue(totalCollection, totalExpense))
                .outstandingBalance(scale(outstanding))
                .totalGst(scale(totalGst))
                .cgstCollected(scale(cgstCollected))
                .sgstCollected(scale(sgstCollected))
                .igstCollected(scale(igstCollected))
                .totalSalesTrendPercentage(calculateTrendPercentage(totalSales, previousSales))
                .collectionTrendPercentage(calculateTrendPercentage(totalCollection, previousCollection))
                .outstandingTrendPercentage(calculateTrendPercentage(outstanding, previousOutstanding))
                .totalCustomersTrendPercentage(calculateTrendPercentage(BigDecimal.valueOf(periodCustomerIds.size()), BigDecimal.valueOf(previousCustomerIds.size())))
                .topCustomers(topCustomers)
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardDetailResponse details(String email,
                                           String card,
                                           LocalDate startDate,
                                           LocalDate endDate,
                                           int page,
                                           int size,
                                           String sortBy,
                                           String sortDirection,
                                           String search) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        List<Invoice> allInvoices = invoicesFor(company);
        List<Payment> allPayments = paymentsFor(company);
        List<Customer> allCustomers = customersFor(company);
        List<Expense> allExpenses = expensesFor(company);
        Map<Long, Customer> customersById = allCustomers.stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<Long, LocalDate> firstPurchaseDates = firstPurchaseDates(allInvoices);
        String normalizedCard = normalizeCard(card);

        List<Invoice> filteredInvoices = allInvoices.stream()
                .filter(invoice -> isWithinRange(invoice.getInvoiceDate(), startDate, endDate))
                .toList();
        List<Payment> filteredPayments = allPayments.stream()
                .filter(payment -> isWithinRange(payment.getPaymentDate(), startDate, endDate))
                .toList();
        List<Expense> filteredExpenses = allExpenses.stream()
                .filter(expense -> isWithinRange(expense.getExpenseDate(), startDate, endDate))
                .toList();

        List<Map<String, Object>> rows = switch (normalizedCard) {
            case "totalSales" -> totalSalesRows(filteredInvoices);
            case "collections" -> collectionRows(filteredPayments);
            case "outstanding" -> outstandingRows(allInvoices, allPayments, endDate);
            case "totalExpense" -> expenseRows(filteredExpenses);
            case "netRevenue" -> netRevenueRows(filteredPayments, filteredExpenses);
            case "newCustomers" -> customerRows(filteredInvoices, customersById, firstPurchaseDates, startDate, endDate, true);
            case "existingCustomers" -> customerRows(filteredInvoices, customersById, firstPurchaseDates, startDate, endDate, false);
            case "invoices" -> invoiceRows(filteredInvoices);
            case "products" -> productSummary(filteredInvoices);
            case "customers" -> totalCustomerRows(filteredInvoices, customersById);
            default -> throw new IllegalArgumentException("Unsupported dashboard card: " + card);
        };

        List<Map<String, Object>> searchedRows = applySearch(rows, search);
        List<Map<String, Object>> sortedRows = applySort(searchedRows, sortBy, sortDirection);
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        int fromIndex = Math.min(safePage * safeSize, sortedRows.size());
        int toIndex = Math.min(fromIndex + safeSize, sortedRows.size());

        return DashboardDetailResponse.builder()
                .card(normalizedCard)
                .page(safePage)
                .size(safeSize)
                .totalElements(sortedRows.size())
                .totalPages((int) Math.ceil((double) sortedRows.size() / safeSize))
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .search(search)
                .rows(sortedRows.subList(fromIndex, toIndex))
                .productSummary(List.of())
                .build();
    }

    private boolean isWithinRange(LocalDate value, LocalDate startDate, LocalDate endDate) {
        if (value == null) {
            return false;
        }
        if (startDate != null && value.isBefore(startDate)) {
            return false;
        }
        return endDate == null || !value.isAfter(endDate);
    }

    private List<Invoice> invoicesFor(Company company) {
        return invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company);
    }

    private List<Payment> paymentsFor(Company company) {
        return paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company);
    }

    private List<Customer> customersFor(Company company) {
        return customerRepository.findByCompanyOrderByCreatedAtDesc(company);
    }

    private List<Expense> expensesFor(Company company) {
        return expenseRepository.findByCompanyOrderByExpenseDateDescIdDesc(company);
    }

    private boolean isBeforeStart(LocalDate value, LocalDate startDate) {
        return value != null && startDate != null && value.isBefore(startDate);
    }

    private Map<Long, LocalDate> firstPurchaseDates(List<Invoice> invoices) {
        Map<Long, LocalDate> firstPurchaseDates = new HashMap<>();
        for (Invoice invoice : invoices) {
            Customer customer = invoice.getCustomer();
            if (customer == null || invoice.getInvoiceDate() == null) {
                continue;
            }
            firstPurchaseDates.merge(customer.getId(), invoice.getInvoiceDate(),
                    (current, incoming) -> incoming.isBefore(current) ? incoming : current);
        }
        return firstPurchaseDates;
    }

    private String normalizeCard(String card) {
        if (card == null) {
            return "";
        }
        return switch (card.trim()) {
            case "total-sales", "totalSales" -> "totalSales";
            case "collections" -> "collections";
            case "outstanding" -> "outstanding";
            case "total-expense", "totalExpense", "expenses" -> "totalExpense";
            case "net-revenue", "netRevenue", "netProfit", "net-profit" -> "netRevenue";
            case "new-customers", "newCustomers" -> "newCustomers";
            case "existing-customers", "existingCustomers" -> "existingCustomers";
            case "invoices" -> "invoices";
            case "products" -> "products";
            case "customers", "totalCustomers", "total-customers" -> "customers";
            default -> card.trim();
        };
    }

    private LocalDate previousRangeStart(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return null;
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        return startDate.minusDays(days);
    }

    private LocalDate previousRangeEnd(LocalDate startDate) {
        return startDate == null ? null : startDate.minusDays(1);
    }

    private List<Map<String, Object>> totalSalesRows(List<Invoice> invoices) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Invoice invoice : invoices) {
            for (InvoiceItem item : invoice.getItems()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("invoiceNo", invoice.getInvoiceNo());
                row.put("customerName", invoice.getCustomer().getName());
                row.put("productName", item.getProduct().getName());
                row.put("quantity", item.getQty());
                row.put("totalAmount", scale(item.getLineTotal()));
                row.put("invoiceDate", invoice.getInvoiceDate());
                row.put("date", invoice.getInvoiceDate());
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> collectionRows(List<Payment> payments) {
        return payments.stream().map(payment -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customerName", payment.getCustomer().getName());
            row.put("invoiceNo", payment.getInvoice() == null ? "--" : payment.getInvoice().getInvoiceNo());
            row.put("collectedAmount", scale(payment.getAmount()));
            row.put("paymentDate", payment.getPaymentDate());
            row.put("paymentMethod", payment.getMode());
            row.put("date", payment.getPaymentDate());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> expenseRows(List<Expense> expenses) {
        return expenses.stream().map(expense -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("expenseType", expense.getExpenseType());
            row.put("categoryName", expense.getCategory().getCategoryName());
            row.put("customerName", expense.getCustomer() == null ? null : expense.getCustomer().getName());
            row.put("invoiceNo", expense.getInvoice() == null ? null : expense.getInvoice().getInvoiceNo());
            row.put("amount", scale(expense.getAmount()));
            row.put("expenseDate", expense.getExpenseDate());
            row.put("description", expense.getDescription());
            row.put("date", expense.getExpenseDate());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> netRevenueRows(List<Payment> payments, List<Expense> expenses) {
        Map<LocalDate, BigDecimal> collectionByDate = payments.stream()
                .filter(payment -> payment.getPaymentDate() != null)
                .collect(Collectors.groupingBy(Payment::getPaymentDate,
                        Collectors.mapping(Payment::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        Map<LocalDate, BigDecimal> expenseByDate = expenses.stream()
                .filter(expense -> expense.getExpenseDate() != null)
                .collect(Collectors.groupingBy(Expense::getExpenseDate,
                        Collectors.mapping(Expense::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        return java.util.stream.Stream.concat(collectionByDate.keySet().stream(), expenseByDate.keySet().stream())
                .distinct()
                .map(date -> {
                    BigDecimal collection = scale(collectionByDate.getOrDefault(date, BigDecimal.ZERO));
                    BigDecimal expense = scale(expenseByDate.getOrDefault(date, BigDecimal.ZERO));
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("date", date);
                    row.put("totalRevenue", collection);
                    row.put("totalExpense", expense);
                    row.put("netRevenue", revenueCalculationService.netRevenue(collection, expense));
                    return row;
                }).toList();
    }

    private List<Map<String, Object>> outstandingRows(List<Invoice> invoices, List<Payment> payments, LocalDate endDate) {
        Map<Long, BigDecimal> paymentsByInvoiceIdUntilEnd = payments.stream()
                .filter(payment -> payment.getInvoice() != null)
                .filter(payment -> endDate == null || (payment.getPaymentDate() != null && !payment.getPaymentDate().isAfter(endDate)))
                .collect(Collectors.groupingBy(payment -> payment.getInvoice().getId(),
                        Collectors.mapping(Payment::getAmount, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
        boolean useCurrentBalance = endDate == null || !endDate.isBefore(LocalDate.now());

        return invoices.stream()
                .filter(invoice -> endDate == null || (invoice.getInvoiceDate() != null && !invoice.getInvoiceDate().isAfter(endDate)))
                .map(invoice -> {
                    BigDecimal paidAmount = useCurrentBalance
                            ? scale(invoice.getPaidAmount())
                            : scale(paymentsByInvoiceIdUntilEnd.getOrDefault(invoice.getId(), BigDecimal.ZERO));
                    BigDecimal outstandingAmount = useCurrentBalance
                            ? scale(invoice.getBalanceAmount())
                            : scale(invoice.getTotalAmount().subtract(paidAmount));

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("customerName", invoice.getCustomer().getName());
                    row.put("invoiceNo", invoice.getInvoiceNo());
                    row.put("invoiceAmount", scale(invoice.getTotalAmount()));
                    row.put("paidAmount", paidAmount);
                    row.put("outstandingAmount", outstandingAmount);
                    row.put("dueDate", invoice.getInvoiceDate());
                    row.put("date", invoice.getInvoiceDate());
                    return row;
                })
                .filter(row -> ((BigDecimal) row.get("outstandingAmount")).compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    private List<Map<String, Object>> customerRows(List<Invoice> invoices,
                                                   Map<Long, Customer> customersById,
                                                   Map<Long, LocalDate> firstPurchaseDates,
                                                   LocalDate startDate,
                                                   LocalDate endDate,
                                                   boolean newCustomerRows) {
        Map<Long, DashboardCustomerAggregate> aggregates = new HashMap<>();
        for (Invoice invoice : invoices) {
            Customer customer = invoice.getCustomer();
            LocalDate firstPurchaseDate = firstPurchaseDates.get(customer.getId());
            boolean matches = newCustomerRows
                    ? isWithinRange(firstPurchaseDate, startDate, endDate)
                    : isBeforeStart(firstPurchaseDate, startDate);
            if (!matches) {
                continue;
            }
            DashboardCustomerAggregate aggregate = aggregates.computeIfAbsent(customer.getId(), id -> new DashboardCustomerAggregate(customer));
            aggregate.invoiceCount++;
            aggregate.totalPurchaseAmount = scale(aggregate.totalPurchaseAmount.add(invoice.getTotalAmount()));
            if (aggregate.lastPurchaseDate == null || invoice.getInvoiceDate().isAfter(aggregate.lastPurchaseDate)) {
                aggregate.lastPurchaseDate = invoice.getInvoiceDate();
            }
        }

        return aggregates.values().stream().map(aggregate -> {
            Customer customer = customersById.getOrDefault(aggregate.customer.getId(), aggregate.customer);
            LocalDate firstPurchaseDate = firstPurchaseDates.get(customer.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customerName", customer.getName());
            if (newCustomerRows) {
                row.put("firstPurchaseDate", firstPurchaseDate);
            } else {
                row.put("customerCreatedDate", toLocalDate(customer.getCreatedAt()));
                row.put("lastPurchaseDate", aggregate.lastPurchaseDate);
                row.put("outstandingAmount", scale(customer.getCurrentBalance()));
            }
            row.put("invoiceCount", aggregate.invoiceCount);
            row.put("totalPurchaseAmount", scale(aggregate.totalPurchaseAmount));
            row.put("date", newCustomerRows ? firstPurchaseDate : aggregate.lastPurchaseDate);
            return row;
        }).toList();
    }

    private List<Map<String, Object>> invoiceRows(List<Invoice> invoices) {
        return invoices.stream().map(invoice -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("invoiceNo", invoice.getInvoiceNo());
            row.put("customerName", invoice.getCustomer().getName());
            row.put("invoiceDate", invoice.getInvoiceDate());
            row.put("invoiceAmount", scale(invoice.getTotalAmount()));
            row.put("status", invoice.getPaymentStatus());
            row.put("date", invoice.getInvoiceDate());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> totalCustomerRows(List<Invoice> invoices, Map<Long, Customer> customersById) {
        Map<Long, DashboardCustomerAggregate> aggregates = new HashMap<>();
        for (Invoice invoice : invoices) {
            Customer customer = invoice.getCustomer();
            DashboardCustomerAggregate aggregate = aggregates.computeIfAbsent(customer.getId(), id -> new DashboardCustomerAggregate(customer));
            aggregate.invoiceCount++;
            aggregate.totalPurchaseAmount = scale(aggregate.totalPurchaseAmount.add(invoice.getTotalAmount()));
            if (aggregate.lastPurchaseDate == null || invoice.getInvoiceDate().isAfter(aggregate.lastPurchaseDate)) {
                aggregate.lastPurchaseDate = invoice.getInvoiceDate();
            }
        }

        return aggregates.values().stream().map(aggregate -> {
            Customer customer = customersById.getOrDefault(aggregate.customer.getId(), aggregate.customer);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("customerName", customer.getName());
            row.put("mobile", customer.getMobile());
            row.put("invoiceCount", aggregate.invoiceCount);
            row.put("totalPurchaseAmount", scale(aggregate.totalPurchaseAmount));
            row.put("outstandingAmount", scale(customer.getCurrentBalance()));
            row.put("lastPurchaseDate", aggregate.lastPurchaseDate);
            row.put("date", aggregate.lastPurchaseDate);
            return row;
        }).toList();
    }

    private List<Map<String, Object>> productSummary(List<Invoice> invoices) {
        Map<Long, ProductSalesAggregate> aggregates = new HashMap<>();
        for (Invoice invoice : invoices) {
            for (InvoiceItem item : invoice.getItems()) {
                ProductSalesAggregate aggregate = aggregates.computeIfAbsent(item.getProduct().getId(),
                        id -> new ProductSalesAggregate(item.getProduct().getName()));
                aggregate.quantitySold += item.getQty();
                aggregate.totalRevenue = scale(aggregate.totalRevenue.add(item.getLineTotal()));
                aggregate.invoiceIds.put(invoice.getId(), true);
            }
        }
        return aggregates.values().stream()
                .sorted(Comparator.comparing(ProductSalesAggregate::getTotalRevenue, Comparator.reverseOrder()))
                .map(aggregate -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productName", aggregate.productName);
                    row.put("quantitySold", aggregate.quantitySold);
                    row.put("totalRevenue", scale(aggregate.totalRevenue));
                    row.put("numberOfInvoices", aggregate.invoiceIds.size());
                    return row;
                }).toList();
    }

    private List<Map<String, Object>> applySearch(List<Map<String, Object>> rows, String search) {
        if (search == null || search.trim().isEmpty()) {
            return rows;
        }
        String normalizedSearch = search.trim().toLowerCase();
        return rows.stream()
                .filter(row -> row.values().stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .map(String::toLowerCase)
                        .anyMatch(value -> value.contains(normalizedSearch)))
                .toList();
    }

    private List<Map<String, Object>> applySort(List<Map<String, Object>> rows, String sortBy, String sortDirection) {
        String key = sortBy == null || sortBy.isBlank() ? "date" : sortBy;
        Comparator<Map<String, Object>> comparator = Comparator.comparing(row -> comparableValue(row.get(key)),
                Comparator.nullsLast(Comparator.naturalOrder()));
        if ("desc".equalsIgnoreCase(sortDirection)) {
            comparator = comparator.reversed();
        }
        List<Map<String, Object>> sorted = new ArrayList<>(rows);
        sorted.sort(comparator);
        return sorted;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Comparable comparableValue(Object value) {
        if (value instanceof Comparable comparable) {
            return comparable;
        }
        return value == null ? null : value.toString();
    }

    private LocalDate toLocalDate(LocalDateTime value) {
        return value == null ? null : value.toLocalDate();
    }

    private BigDecimal calculateOutstandingAsOf(List<Customer> customers, List<Invoice> invoices, List<Payment> payments, LocalDate endDate) {
        if (endDate == null || !endDate.isBefore(LocalDate.now())) {
            return customers.stream()
                    .map(Customer::getCurrentBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal salesUntilEnd = invoices.stream()
                .filter(invoice -> invoice.getInvoiceDate() != null && !invoice.getInvoiceDate().isAfter(endDate))
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collectionsUntilEnd = payments.stream()
                .filter(payment -> payment.getPaymentDate() != null && !payment.getPaymentDate().isAfter(endDate))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return scale(salesUntilEnd.subtract(collectionsUntilEnd));
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTrendPercentage(BigDecimal current, BigDecimal previous) {
        BigDecimal scaledCurrent = scale(current);
        BigDecimal scaledPrevious = scale(previous);
        if (scaledPrevious.compareTo(BigDecimal.ZERO) == 0) {
            if (scaledCurrent.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            return BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
        }
        return scaledCurrent.subtract(scaledPrevious)
                .divide(scaledPrevious, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static final class DashboardCustomerAggregate {
        private final Customer customer;
        private BigDecimal totalPurchaseAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal totalPaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private int invoiceCount;
        private LocalDate lastPurchaseDate;

        private DashboardCustomerAggregate(Customer customer) {
            this.customer = customer;
        }

        public BigDecimal getTotalPurchaseAmount() {
            return totalPurchaseAmount;
        }
    }

    private static final class ProductSalesAggregate {
        private final String productName;
        private final Map<Long, Boolean> invoiceIds = new HashMap<>();
        private int quantitySold;
        private BigDecimal totalRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private ProductSalesAggregate(String productName) {
            this.productName = productName;
        }

        public BigDecimal getTotalRevenue() {
            return totalRevenue;
        }
    }
}
