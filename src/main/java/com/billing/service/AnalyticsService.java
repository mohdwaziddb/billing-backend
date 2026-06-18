package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.analytics.AnalyticsSummaryResponse;
import com.billing.dto.analytics.CustomerDueResponse;
import com.billing.dto.analytics.LowStockProductResponse;
import com.billing.dto.analytics.MetricPointResponse;
import com.billing.dto.analytics.OwnerAnalyticsResponse;
import com.billing.dto.analytics.SalesByCategoryResponse;
import com.billing.dto.analytics.SalesChartPointResponse;
import com.billing.dto.analytics.SalesTrendStatus;
import com.billing.dto.analytics.TopSellingProductResponse;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Expense;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.entity.Payment;
import com.billing.entity.Product;
import com.billing.entity.ProductCategory;
import com.billing.entity.User;
import com.billing.repository.CustomerRepository;
import com.billing.repository.ExpenseRepository;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentRepository;
import com.billing.repository.ProductCategoryRepository;
import com.billing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AccessControlService accessControlService;
    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(String email, LocalDate startDate, LocalDate endDate) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.isSuperAdmin(user) ? null : accessControlService.requireCompany(user);
        List<Invoice> invoices = invoicesFor(company);
        List<Payment> payments = paymentsFor(company);
        List<Customer> customers = customersFor(company);

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth lastMonth = thisMonth.minusMonths(1);

        List<Invoice> filteredInvoices = invoices.stream()
                .filter(invoice -> isWithinRange(invoice.getInvoiceDate(), startDate, endDate))
                .toList();
        List<Payment> filteredPayments = payments.stream()
                .filter(payment -> isWithinRange(payment.getPaymentDate(), startDate, endDate))
                .toList();

        BigDecimal todaySales = sumInvoiceSalesForDate(invoices, today);
        BigDecimal yesterdaySales = sumInvoiceSalesForDate(invoices, yesterday);
        BigDecimal thisMonthSales = sumInvoiceSalesForMonth(invoices, thisMonth);
        BigDecimal lastMonthSales = sumInvoiceSalesForMonth(invoices, lastMonth);
        BigDecimal outstanding = calculateOutstandingAsOf(customers, invoices, payments, endDate);
        long lowStockCount = productsFor(company).stream()
                .filter(Product::isActive)
                .filter(product -> product.getStockQty() <= product.getMinStockQty())
                .count();
        long dueCustomers = customers.stream().filter(customer -> scale(customer.getCurrentBalance()).compareTo(BigDecimal.ZERO) > 0).count();
        long newCustomers = customers.stream()
                .filter(customer -> customer.getCreatedAt() != null && isWithinRange(customer.getCreatedAt().toLocalDate(), startDate, endDate))
                .count();

        BigDecimal totalSales = filteredInvoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCollection = filteredPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return AnalyticsSummaryResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .todaySales(scale(todaySales))
                .yesterdaySales(scale(yesterdaySales))
                .thisMonthSales(scale(thisMonthSales))
                .lastMonthSales(scale(lastMonthSales))
                .totalSales(scale(totalSales))
                .totalCollection(scale(totalCollection))
                .totalOutstandingBalance(scale(outstanding))
                .newCustomers(newCustomers)
                .totalInvoices(filteredInvoices.size())
                .lowStockProducts(lowStockCount)
                .dueCustomers(dueCustomers)
                .salesTrendPercentage(calculateTrendPercentage(thisMonthSales, lastMonthSales))
                .trendStatus(resolveTrendStatus(thisMonthSales, lastMonthSales))
                .build();
    }

    @Transactional(readOnly = true)
    public List<SalesChartPointResponse> dayWiseSales(String email, int year, int month) {
        List<Invoice> invoices = invoicesForCurrentUser(email);
        YearMonth selectedMonth = YearMonth.of(year, month);
        List<SalesChartPointResponse> points = new ArrayList<>();

        for (int day = 1; day <= selectedMonth.lengthOfMonth(); day++) {
            LocalDate date = selectedMonth.atDay(day);
            BigDecimal amount = invoices.stream()
                    .filter(invoice -> invoice.getInvoiceDate().equals(date))
                    .map(Invoice::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            points.add(SalesChartPointResponse.builder()
                    .label(String.valueOf(day))
                    .index(day)
                    .salesAmount(scale(amount))
                    .build());
        }

        return points;
    }

    @Transactional(readOnly = true)
    public List<SalesChartPointResponse> monthWiseSales(String email, int year) {
        List<Invoice> invoices = invoicesForCurrentUser(email);
        List<SalesChartPointResponse> points = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            YearMonth targetMonth = YearMonth.of(year, month);
            BigDecimal amount = sumInvoiceSalesForMonth(invoices, targetMonth);
            points.add(SalesChartPointResponse.builder()
                    .label(targetMonth.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                    .index(month)
                    .salesAmount(scale(amount))
                    .build());
        }

        return points;
    }

    @Transactional(readOnly = true)
    public PageResponse<TopSellingProductResponse> topSellingProducts(String email, LocalDate startDate, LocalDate endDate, String search, int page, int size) {
        Pageable pageable = pageRequest(page, size);
        String normalizedSearch = blankToNull(search);
        List<Invoice> invoices = invoicesForCurrentUser(email).stream()
                .filter(invoice -> isWithinRange(invoice.getInvoiceDate(), startDate, endDate))
                .toList();
        Map<Long, ProductSalesAggregate> aggregates = new HashMap<>();

        for (Invoice invoice : invoices) {
            for (InvoiceItem item : invoice.getItems()) {
                aggregates.compute(item.getProduct().getId(), (productId, current) -> {
                    ProductSalesAggregate next = current == null
                            ? new ProductSalesAggregate(item.getProduct(), 0, BigDecimal.ZERO)
                            : current;
                    next.totalQty += item.getQty();
                    next.totalSales = scale(next.totalSales.add(item.getLineTotal()));
                    return next;
                });
            }
        }

        List<TopSellingProductResponse> allRecords = aggregates.values().stream()
                .filter(aggregate -> normalizedSearch == null
                        || containsIgnoreCase(aggregate.product.getName(), normalizedSearch)
                        || containsIgnoreCase(aggregate.product.getSku(), normalizedSearch))
                .sorted(Comparator.comparing(ProductSalesAggregate::getTotalSales, Comparator.reverseOrder())
                        .thenComparing(ProductSalesAggregate::getTotalQty, Comparator.reverseOrder()))
                .map(aggregate -> TopSellingProductResponse.builder()
                        .productId(aggregate.product.getId())
                        .productName(aggregate.product.getName())
                        .sku(aggregate.product.getSku())
                        .totalQtySold(aggregate.totalQty)
                        .totalSalesAmount(scale(aggregate.totalSales))
                        .currentStockQty(aggregate.product.getStockQty())
                        .build())
                .toList();
        return page(allRecords, pageable);
    }

    @Transactional(readOnly = true)
    public List<SalesByCategoryResponse> salesByCategory(String email, LocalDate startDate, LocalDate endDate, int limit) {
        return salesByCategoryRecords(email, startDate, endDate, null).stream()
                .limit(Math.max(1, Math.min(limit, 7)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesByCategoryResponse> salesByCategoryPage(String email, LocalDate startDate, LocalDate endDate, String search, int page, int size) {
        return page(salesByCategoryRecords(email, startDate, endDate, search), pageRequest(page, size));
    }

    private List<SalesByCategoryResponse> salesByCategoryRecords(String email, LocalDate startDate, LocalDate endDate, String search) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.isSuperAdmin(user) ? null : accessControlService.requireCompany(user);
        String normalizedSearch = blankToNull(search);
        List<ProductCategory> categories = categoriesFor(company);
        List<Invoice> invoices = invoicesFor(company).stream()
                .filter(invoice -> isWithinRange(invoice.getInvoiceDate(), startDate, endDate))
                .toList();
        Map<Long, CategorySalesAggregate> aggregates = new HashMap<>();

        for (ProductCategory category : categories) {
            aggregates.put(category.getId(), new CategorySalesAggregate(category, BigDecimal.ZERO));
        }

        for (Invoice invoice : invoices) {
            for (InvoiceItem item : invoice.getItems()) {
                ProductCategory category = item.getProduct().getProductCategory();
                if (category == null || !aggregates.containsKey(category.getId())) {
                    continue;
                }
                aggregates.compute(category.getId(), (categoryId, current) -> {
                    CategorySalesAggregate next = current == null
                            ? new CategorySalesAggregate(category, BigDecimal.ZERO)
                            : current;
                    next.totalSales = scale(next.totalSales.add(item.getLineTotal()));
                    return next;
                });
            }
        }

        BigDecimal totalSales = aggregates.values().stream()
                .map(CategorySalesAggregate::getTotalSales)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return aggregates.values().stream()
                .filter(aggregate -> normalizedSearch == null || containsIgnoreCase(aggregate.category.getCategoryName(), normalizedSearch))
                .sorted(Comparator.comparing(CategorySalesAggregate::getTotalSales, Comparator.reverseOrder())
                        .thenComparing(aggregate -> aggregate.category.getCategoryName()))
                .map(aggregate -> SalesByCategoryResponse.builder()
                        .categoryId(aggregate.category.getId())
                        .categoryName(aggregate.category.getCategoryName())
                        .totalAmount(scale(aggregate.totalSales))
                        .percentage(calculateSharePercentage(aggregate.totalSales, totalSales))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<LowStockProductResponse> lowStockProducts(String email, int page, int size) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.isSuperAdmin(user) ? null : accessControlService.requireCompany(user);
        Pageable pageable = pageRequest(page, size);
        List<LowStockProductResponse> allRecords = productsFor(company).stream()
                .filter(Product::isActive)
                .filter(product -> product.getStockQty() <= product.getMinStockQty())
                .sorted(Comparator.comparing(Product::getStockQty).thenComparing(Product::getName))
                .map(product -> LowStockProductResponse.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .sku(product.getSku())
                        .stockQty(product.getStockQty())
                        .minStockQty(product.getMinStockQty())
                        .active(product.isActive())
                        .build())
                .toList();
        return page(allRecords, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerDueResponse> customerDueList(String email, String search, int page, int size) {
        User user = accessControlService.getCurrentUser(email);
        if (!accessControlService.isSuperAdmin(user)) {
            Company company = accessControlService.requireCompany(user);
            return PageResponse.from(customerRepository.findOutstandingPageByCompanyWithFilters(company, blankToNull(search), null, pageRequest(page, size))
                    .map(customer -> CustomerDueResponse.builder()
                            .customerId(customer.getId())
                            .customerName(customer.getName())
                            .mobile(customer.getMobile())
                            .email(customer.getEmail())
                            .currentBalance(scale(customer.getCurrentBalance()))
                            .build()));
        }
        String normalizedSearch = blankToNull(search);
        List<CustomerDueResponse> records = customersFor(null).stream()
                .filter(customer -> scale(customer.getCurrentBalance()).compareTo(BigDecimal.ZERO) > 0)
                .filter(customer -> normalizedSearch == null
                        || containsIgnoreCase(customer.getName(), normalizedSearch)
                        || containsIgnoreCase(customer.getMobile(), normalizedSearch)
                        || containsIgnoreCase(customer.getEmail(), normalizedSearch))
                .sorted(Comparator.comparing(Customer::getCurrentBalance, Comparator.reverseOrder()))
                .map(customer -> CustomerDueResponse.builder()
                        .customerId(customer.getId())
                        .customerName(customer.getName())
                        .mobile(customer.getMobile())
                        .email(customer.getEmail())
                        .currentBalance(scale(customer.getCurrentBalance()))
                        .build())
                .toList();
        return page(records, pageRequest(page, size));
    }

    @Transactional(readOnly = true)
    public OwnerAnalyticsResponse ownerOverview(String email, LocalDate startDate, LocalDate endDate) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.isSuperAdmin(user) ? null : accessControlService.requireCompany(user);
        List<Invoice> invoices = invoicesFor(company);
        List<Payment> payments = paymentsFor(company);
        List<Customer> customers = customersFor(company);
        List<Expense> expenses = expensesFor(company);

        LocalDate safeEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate safeStart = startDate != null ? startDate : safeEnd.minusDays(29);

        List<Invoice> filteredInvoices = invoices.stream()
                .filter(invoice -> isWithinRange(invoice.getInvoiceDate(), safeStart, safeEnd))
                .toList();
        List<Payment> filteredPayments = payments.stream()
                .filter(payment -> isWithinRange(payment.getPaymentDate(), safeStart, safeEnd))
                .toList();
        List<Expense> filteredExpenses = expenses.stream()
                .filter(expense -> isWithinRange(expense.getExpenseDate(), safeStart, safeEnd))
                .toList();

        BigDecimal totalSales = filteredInvoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCollection = filteredPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = filteredExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingAmount = calculateOutstandingAsOf(customers, invoices, payments, safeEnd);
        long newCustomers = customers.stream()
                .filter(customer -> customer.getCreatedAt() != null && isWithinRange(customer.getCreatedAt().toLocalDate(), safeStart, safeEnd))
                .count();

        List<LocalDate> periodStarts = buildPeriodStarts(safeStart, safeEnd);
        boolean useDailyBuckets = java.time.temporal.ChronoUnit.DAYS.between(safeStart, safeEnd) <= 45;
        List<MetricPointResponse> salesTrend = new ArrayList<>();
        List<MetricPointResponse> collectionTrend = new ArrayList<>();
        List<MetricPointResponse> expenseTrend = new ArrayList<>();
        List<MetricPointResponse> netProfitTrend = new ArrayList<>();
        List<MetricPointResponse> outstandingTrend = new ArrayList<>();
        List<MetricPointResponse> customerGrowthTrend = new ArrayList<>();

        BigDecimal openingOutstanding = calculateOutstandingBefore(invoices, payments, safeStart);
        BigDecimal runningOutstanding = openingOutstanding;
        long runningCustomers = 0L;
        int index = 1;

        for (LocalDate periodStart : periodStarts) {
            LocalDate periodEnd = resolvePeriodEnd(periodStart, safeEnd, useDailyBuckets);
            BigDecimal periodSales = filteredInvoices.stream()
                    .filter(invoice -> isWithinRange(invoice.getInvoiceDate(), periodStart, periodEnd))
                    .map(Invoice::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal periodCollection = filteredPayments.stream()
                    .filter(payment -> isWithinRange(payment.getPaymentDate(), periodStart, periodEnd))
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal periodExpense = filteredExpenses.stream()
                    .filter(expense -> isWithinRange(expense.getExpenseDate(), periodStart, periodEnd))
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long periodNewCustomers = customers.stream()
                    .filter(customer -> customer.getCreatedAt() != null && isWithinRange(customer.getCreatedAt().toLocalDate(), periodStart, periodEnd))
                    .count();

            runningOutstanding = scale(runningOutstanding.add(periodSales).subtract(periodCollection));
            runningCustomers += periodNewCustomers;

            String label = buildLabel(periodStart, useDailyBuckets);
            salesTrend.add(point(label, index, periodSales));
            collectionTrend.add(point(label, index, periodCollection));
            expenseTrend.add(point(label, index, periodExpense));
            netProfitTrend.add(point(label, index, periodSales.subtract(periodExpense)));
            outstandingTrend.add(point(label, index, runningOutstanding));
            customerGrowthTrend.add(point(label, index, BigDecimal.valueOf(runningCustomers)));
            index++;
        }

        List<MetricPointResponse> monthlyRevenue = buildMonthlyRevenueTrend(invoices, safeEnd);
        List<MetricPointResponse> expenseByCategory = buildExpenseByCategory(filteredExpenses);

        return OwnerAnalyticsResponse.builder()
                .startDate(safeStart)
                .endDate(safeEnd)
                .totalSales(scale(totalSales))
                .totalCollection(scale(totalCollection))
                .totalExpense(scale(totalExpense))
                .netRevenue(scale(totalSales.subtract(totalExpense)))
                .outstandingAmount(scale(outstandingAmount))
                .newCustomers(newCustomers)
                .totalInvoices(filteredInvoices.size())
                .salesTrend(salesTrend)
                .collectionTrend(collectionTrend)
                .expenseTrend(expenseTrend)
                .netProfitTrend(netProfitTrend)
                .outstandingTrend(outstandingTrend)
                .customerGrowthTrend(customerGrowthTrend)
                .monthlyRevenue(monthlyRevenue)
                .expenseByCategory(expenseByCategory)
                .build();
    }

    private List<MetricPointResponse> buildExpenseByCategory(List<Expense> expenses) {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (Expense expense : expenses) {
            String category = expense.getCategory() != null ? expense.getCategory().getCategoryName() : "Other";
            totals.merge(category, scale(expense.getAmount()), BigDecimal::add);
        }
        List<Map.Entry<String, BigDecimal>> sorted = totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .limit(6)
                .toList();
        List<MetricPointResponse> points = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, BigDecimal> entry = sorted.get(i);
            points.add(point(entry.getKey(), i + 1, entry.getValue()));
        }
        return points;
    }

    private List<MetricPointResponse> buildMonthlyRevenueTrend(List<Invoice> invoices, LocalDate endDate) {
        List<MetricPointResponse> points = new ArrayList<>();
        YearMonth endMonth = YearMonth.from(endDate);
        for (int i = 11; i >= 0; i--) {
            YearMonth month = endMonth.minusMonths(i);
            BigDecimal amount = sumInvoiceSalesForMonth(invoices, month);
            points.add(point(month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.getYear(), 12 - i, amount));
        }
        return points;
    }

    private BigDecimal calculateOutstandingBefore(List<Invoice> invoices, List<Payment> payments, LocalDate startDate) {
        BigDecimal historicalSales = invoices.stream()
                .filter(invoice -> invoice.getInvoiceDate().isBefore(startDate))
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal historicalCollections = payments.stream()
                .filter(payment -> payment.getPaymentDate().isBefore(startDate))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return scale(historicalSales.subtract(historicalCollections));
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

    private List<LocalDate> buildPeriodStarts(LocalDate startDate, LocalDate endDate) {
        long daySpan = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        List<LocalDate> periods = new ArrayList<>();
        if (daySpan <= 45) {
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                periods.add(current);
                current = current.plusDays(1);
            }
            return periods;
        }

        YearMonth current = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);
        while (!current.isAfter(end)) {
            periods.add(current.atDay(1));
            current = current.plusMonths(1);
        }
        return periods;
    }

    private LocalDate resolvePeriodEnd(LocalDate periodStart, LocalDate finalEndDate, boolean useDailyBuckets) {
        if (useDailyBuckets) {
            return periodStart;
        }
        LocalDate monthEnd = YearMonth.from(periodStart).atEndOfMonth();
        return monthEnd.isAfter(finalEndDate) ? finalEndDate : monthEnd;
    }

    private String buildLabel(LocalDate periodStart, boolean useDailyBuckets) {
        if (useDailyBuckets) {
            return String.valueOf(periodStart.getDayOfMonth());
        }
        YearMonth month = YearMonth.from(periodStart);
        return month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    private MetricPointResponse point(String label, int index, BigDecimal value) {
        return MetricPointResponse.builder()
                .label(label)
                .index(index)
                .value(scale(value))
                .build();
    }

    private BigDecimal sumInvoiceSalesForDate(List<Invoice> invoices, LocalDate date) {
        return invoices.stream()
                .filter(invoice -> invoice.getInvoiceDate().equals(date))
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumInvoiceSalesForMonth(List<Invoice> invoices, YearMonth yearMonth) {
        return invoices.stream()
                .filter(invoice -> YearMonth.from(invoice.getInvoiceDate()).equals(yearMonth))
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    private SalesTrendStatus resolveTrendStatus(BigDecimal current, BigDecimal previous) {
        int comparison = scale(current).compareTo(scale(previous));
        if (comparison > 0) {
            return SalesTrendStatus.UP;
        }
        if (comparison < 0) {
            return SalesTrendStatus.DOWN;
        }
        return SalesTrendStatus.FLAT;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateSharePercentage(BigDecimal amount, BigDecimal total) {
        BigDecimal scaledTotal = scale(total);
        if (scaledTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return scale(amount).divide(scaledTotal, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase(Locale.ENGLISH).contains(search.toLowerCase(Locale.ENGLISH));
    }

    private List<Invoice> invoicesForCurrentUser(String email) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.isSuperAdmin(user) ? null : accessControlService.requireCompany(user);
        return invoicesFor(company);
    }

    private List<Invoice> invoicesFor(Company company) {
        return company == null ? invoiceRepository.findAllByOrderByInvoiceDateDescIdDesc() : invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company);
    }

    private List<Payment> paymentsFor(Company company) {
        return company == null ? paymentRepository.findAllByOrderByPaymentDateDescIdDesc() : paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company);
    }

    private List<Customer> customersFor(Company company) {
        return company == null ? customerRepository.findAll() : customerRepository.findByCompanyOrderByCreatedAtDesc(company);
    }

    private List<Product> productsFor(Company company) {
        return company == null ? productRepository.findAll() : productRepository.findByCompanyOrderByCreatedAtDesc(company);
    }

    private List<ProductCategory> categoriesFor(Company company) {
        return company == null
                ? productCategoryRepository.findAll().stream().filter(ProductCategory::isActive).toList()
                : productCategoryRepository.findAllByCompanyWithFilters(company, true, null);
    }

    private List<Expense> expensesFor(Company company) {
        return company == null ? expenseRepository.findAllByOrderByExpenseDateDescIdDesc() : expenseRepository.findByCompanyOrderByExpenseDateDescIdDesc(company);
    }

    private <T> PageResponse<T> page(List<T> allRecords, Pageable pageable) {
        int fromIndex = Math.min((int) pageable.getOffset(), allRecords.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), allRecords.size());
        return PageResponse.from(new PageImpl<>(allRecords.subList(fromIndex, toIndex), pageable, allRecords.size()));
    }

    private static class ProductSalesAggregate {
        private final Product product;
        private int totalQty;
        private BigDecimal totalSales;

        private ProductSalesAggregate(Product product, int totalQty, BigDecimal totalSales) {
            this.product = product;
            this.totalQty = totalQty;
            this.totalSales = totalSales;
        }

        public int getTotalQty() {
            return totalQty;
        }

        public BigDecimal getTotalSales() {
            return totalSales;
        }
    }

    private static class CategorySalesAggregate {
        private final ProductCategory category;
        private BigDecimal totalSales;

        private CategorySalesAggregate(ProductCategory category, BigDecimal totalSales) {
            this.category = category;
            this.totalSales = totalSales;
        }

        public BigDecimal getTotalSales() {
            return totalSales;
        }
    }
}
