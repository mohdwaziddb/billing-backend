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
import com.billing.entity.Expense;
import com.billing.entity.Product;
import com.billing.entity.ProductCategory;
import com.billing.entity.User;
import com.billing.repository.CustomerRepository;
import com.billing.repository.ExpenseRepository;
import com.billing.repository.InvoiceItemRepository;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentRepository;
import com.billing.repository.ProductBatchRepository;
import com.billing.repository.ProductCategoryRepository;
import com.billing.repository.ProductRepository;
import com.billing.util.DataTypeUtility;
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
    private final InvoiceItemRepository invoiceItemRepository;
    private final ProductBatchRepository productBatchRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final ExpenseRepository expenseRepository;
    private final RevenueCalculationService revenueCalculationService;
    private final InventoryService inventoryService;

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(String email, LocalDate startDate, LocalDate endDate) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        LocalDate today = com.billing.core.AppDateTime.istToday();
        // Perf: pehle endDate/aaj tak ki POORI invoices + payments history, saare customers aur
        // saare product batches entity bana kar memory me aate the. Ab sab SQL aggregate.
        // loadUntil: pehle itni history load hoti thi, "is mahine" ka jod usi tak (behaviour same).
        LocalDate loadUntil = endDate == null ? null : (endDate.isAfter(today) ? endDate : today);

        LocalDate yesterday = today.minusDays(1);
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth lastMonth = thisMonth.minusMonths(1);

        Object[] rangeSummary = firstRow(invoiceRepository.summarizeActiveInRange(startDate, endDate), 3);
        BigDecimal todaySales = salesInRange(today, today);
        BigDecimal yesterdaySales = salesInRange(yesterday, yesterday);
        BigDecimal thisMonthSales = salesInRange(thisMonth.atDay(1), earlierOf(thisMonth.atEndOfMonth(), loadUntil));
        BigDecimal lastMonthSales = salesInRange(lastMonth.atDay(1), lastMonth.atEndOfMonth());
        BigDecimal outstanding = calculateOutstandingAsOf(endDate);
        Map<Long, Integer> stockByProduct = currentStockByProduct();
        long lowStockCount = productsFor(company).stream()
                .filter(Product::isActive)
                .filter(product -> stockByProduct.getOrDefault(product.getId(), 0) <= DataTypeUtility.integerValue(product.getMinStockQty()))
                .count();
        long dueCustomers = customerRepository.countByCurrentBalanceGreaterThan(BigDecimal.ZERO);
        long newCustomers = customerCreatedDates().stream()
                .filter(createdDate -> isWithinRange(createdDate, startDate, endDate))
                .count();

        BigDecimal totalSales = amountOf(rangeSummary[0]);
        long totalInvoices = countOf(rangeSummary[1]);
        BigDecimal totalCollection = amountOf(paymentRepository.sumActiveAmountInRange(startDate, endDate));

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
                .totalInvoices(totalInvoices)
                .lowStockProducts(lowStockCount)
                .dueCustomers(dueCustomers)
                .salesTrendPercentage(calculateTrendPercentage(thisMonthSales, lastMonthSales))
                .trendStatus(resolveTrendStatus(thisMonthSales, lastMonthSales))
                .build();
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(Map<String, Object> param, String email) {
        LocalDate startDate = DataTypeUtility.parseFlexibleDate(param, "startDate");
        LocalDate endDate = DataTypeUtility.parseFlexibleDate(param, "endDate");
        return summary(email, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<SalesChartPointResponse> dayWiseSales(Map<String, Object> param, String email) {
        int parsedYear = DataTypeUtility.integerValue(param.get("year"));
        int parsedMonth = DataTypeUtility.integerValue(param.get("month"));
        int targetYear = parsedYear;
        if (targetYear == 0) {
            targetYear = com.billing.core.AppDateTime.istToday().getYear();
        }
        int targetMonth = parsedMonth;
        if (targetMonth == 0) {
            targetMonth = com.billing.core.AppDateTime.istToday().getMonthValue();
        }
        return dayWiseSales(email, targetYear, targetMonth);
    }

    @Transactional(readOnly = true)
    public List<SalesChartPointResponse> monthWiseSales(Map<String, Object> param, String email) {
        int parsedYear = DataTypeUtility.integerValue(param.get("year"));
        int targetYear = parsedYear;
        if (targetYear == 0) {
            targetYear = com.billing.core.AppDateTime.istToday().getYear();
        }
        return monthWiseSales(email, targetYear);
    }

    @Transactional(readOnly = true)
    public PageResponse<TopSellingProductResponse> topSellingProducts(Map<String, Object> param, String email) {
        LocalDate startDate = DataTypeUtility.parseFlexibleDate(param, "startDate");
        LocalDate endDate = DataTypeUtility.parseFlexibleDate(param, "endDate");
        String search = DataTypeUtility.stringValue(param.get("search"));
        if (search.length() == 0) {
            search = null;
        }
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return topSellingProducts(email, startDate, endDate, search, page, size);
    }

    @Transactional(readOnly = true)
    public List<SalesByCategoryResponse> salesByCategory(Map<String, Object> param, String email) {
        LocalDate startDate = DataTypeUtility.parseFlexibleDate(param, "startDate");
        LocalDate endDate = DataTypeUtility.parseFlexibleDate(param, "endDate");
        int limit = DataTypeUtility.integerValue(param.get("limit"));
        if (limit == 0) {
            limit = 7;
        }
        return salesByCategory(email, startDate, endDate, limit);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesByCategoryResponse> salesByCategoryPage(Map<String, Object> param, String email) {
        LocalDate startDate = DataTypeUtility.parseFlexibleDate(param, "startDate");
        LocalDate endDate = DataTypeUtility.parseFlexibleDate(param, "endDate");
        String search = DataTypeUtility.stringValue(param.get("search"));
        if (search.length() == 0) {
            search = null;
        }
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return salesByCategoryPage(email, startDate, endDate, search, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<LowStockProductResponse> lowStockProducts(Map<String, Object> param, String email) {
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return lowStockProducts(email, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerDueResponse> customerDueList(Map<String, Object> param, String email) {
        String search = DataTypeUtility.stringValue(param.get("search"));
        if (search.length() == 0) {
            search = null;
        }
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return customerDueList(email, search, page, size);
    }

    @Transactional(readOnly = true)
    public OwnerAnalyticsResponse ownerOverview(Map<String, Object> param, String email) {
        LocalDate startDate = DataTypeUtility.parseFlexibleDate(param, "startDate");
        LocalDate endDate = DataTypeUtility.parseFlexibleDate(param, "endDate");
        return ownerOverview(email, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<SalesChartPointResponse> dayWiseSales(String email, int year, int month) {
        YearMonth selectedMonth = YearMonth.of(year, month);
        // Din-wise jod SQL group by se (pehle mahine ke invoices entity bana kar har din par loop).
        Map<LocalDate, BigDecimal> salesByDate = salesByDateInRange(email, selectedMonth.atDay(1), selectedMonth.atEndOfMonth());
        List<SalesChartPointResponse> points = new ArrayList<>();

        for (int day = 1; day <= selectedMonth.lengthOfMonth(); day++) {
            LocalDate date = selectedMonth.atDay(day);
            BigDecimal amount = salesByDate.getOrDefault(date, BigDecimal.ZERO);

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
        Map<YearMonth, BigDecimal> salesByMonth = sumByMonth(salesByDateInRange(email, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)));
        List<SalesChartPointResponse> points = new ArrayList<>();

        for (int month = 1; month <= 12; month++) {
            YearMonth targetMonth = YearMonth.of(year, month);
            BigDecimal amount = salesByMonth.getOrDefault(targetMonth, BigDecimal.ZERO);
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
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        Pageable pageable = pageRequest(page, size);
        String normalizedSearch = blankToNull(search);
        // Perf: product-wise qty/sales SQL group by se. Pehle range ke saare invoices items samet
        // memory me aate the, aur HAR product (sirf page wale nahi) ke liye alag stock query (N+1).
        List<Object[]> salesRows = invoiceItemRepository.sumActiveSalesByProduct(startDate, endDate);
        List<Long> productIds = salesRows.stream()
                .map(row -> DataTypeUtility.longValue(row[0]))
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, Product> productsById = new HashMap<>();
        for (Product product : productRepository.findAllById(productIds)) {
            productsById.put(product.getId(), product);
        }
        List<ProductSalesAggregate> aggregates = new ArrayList<>();
        for (Object[] row : salesRows) {
            Product product = productsById.get(DataTypeUtility.longValue(row[0]));
            if (product == null) {
                continue;
            }
            aggregates.add(new ProductSalesAggregate(product, DataTypeUtility.integerValue(row[1]), scale(amountOf(row[2]))));
        }

        List<ProductSalesAggregate> sortedAggregates = aggregates.stream()
                .filter(aggregate -> normalizedSearch == null
                        || containsIgnoreCase(aggregate.product.getName(), normalizedSearch)
                        || containsIgnoreCase(aggregate.product.getSku(), normalizedSearch))
                .sorted(Comparator.comparing(ProductSalesAggregate::getTotalSales, Comparator.reverseOrder())
                        .thenComparing(ProductSalesAggregate::getTotalQty, Comparator.reverseOrder()))
                .toList();
        // Stock sirf current page ke products ka, ek batch query me.
        int fromIndex = Math.min((int) pageable.getOffset(), sortedAggregates.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), sortedAggregates.size());
        List<ProductSalesAggregate> pageAggregates = sortedAggregates.subList(fromIndex, toIndex);
        Map<Long, InventoryService.ProductInventorySnapshot> inventoryByProduct = inventoryService.summarize(company,
                pageAggregates.stream().map(aggregate -> aggregate.product).toList(), false);
        List<TopSellingProductResponse> pageRecords = pageAggregates.stream()
                .map(aggregate -> TopSellingProductResponse.builder()
                        .productId(aggregate.product.getId())
                        .productName(aggregate.product.getName())
                        .sku(aggregate.product.getSku())
                        .totalQtySold(aggregate.totalQty)
                        .totalSalesAmount(scale(aggregate.totalSales))
                        .currentStockQty(inventoryByProduct.getOrDefault(aggregate.product.getId(), InventoryService.ProductInventorySnapshot.empty()).getCurrentStock())
                        .build())
                .toList();
        return PageResponse.from(new PageImpl<>(pageRecords, pageable, sortedAggregates.size()));
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
        Company company = accessControlService.requireCompany(user);
        String normalizedSearch = blankToNull(search);
        List<ProductCategory> categories = categoriesFor(company);
        Map<Long, CategorySalesAggregate> aggregates = new HashMap<>();

        for (ProductCategory category : categories) {
            aggregates.put(category.getId(), new CategorySalesAggregate(category, BigDecimal.ZERO));
        }

        // Category-wise jod SQL group by se (pehle range ke saare invoices items samet memory me).
        for (Object[] row : invoiceItemRepository.sumActiveSalesByCategory(startDate, endDate)) {
            CategorySalesAggregate aggregate = aggregates.get(DataTypeUtility.longValue(row[0]));
            if (aggregate == null) {
                continue;
            }
            aggregate.totalSales = scale(aggregate.totalSales.add(amountOf(row[1])));
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
        Company company = accessControlService.requireCompany(user);
        Pageable pageable = pageRequest(page, size);
        // Perf: pehle har product ke liye summarize() 3 baar (filter + sort comparator ke andar
        // har comparison par + map) = hazaron queries. Ab saare products ka stock ek group-by query se.
        Map<Long, Integer> stockByProduct = currentStockByProduct();
        List<LowStockProductResponse> allRecords = productsFor(company).stream()
                .filter(Product::isActive)
                .filter(product -> stockByProduct.getOrDefault(product.getId(), 0) <= DataTypeUtility.integerValue(product.getMinStockQty()))
                .sorted(Comparator.comparing((Product product) -> stockByProduct.getOrDefault(product.getId(), 0)).thenComparing(Product::getName))
                .map(product -> LowStockProductResponse.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .sku(product.getSku())
                        .stockQty(stockByProduct.getOrDefault(product.getId(), 0))
                        .minStockQty(product.getMinStockQty())
                        .active(product.isActive())
                        .build())
                .toList();
        return page(allRecords, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerDueResponse> customerDueList(String email, String search, int page, int size) {
        User user = accessControlService.getCurrentUser(email);
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

    @Transactional(readOnly = true)
    public OwnerAnalyticsResponse ownerOverview(String email, LocalDate startDate, LocalDate endDate) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        LocalDate safeEnd = endDate != null ? endDate : com.billing.core.AppDateTime.istToday();
        LocalDate safeStart = startDate != null ? startDate : safeEnd.minusDays(29);
        // Perf: pehle safeEnd tak ki POORI invoices + payments history aur saare customers entity
        // bana kar aate the (Dashboard + Analytics dono har load par ye call karte hain).
        // Ab range ke din-wise jod, opening/as-of outstanding aur 12-month trend SQL aggregate se;
        // expenses (category naam chahiye) sirf range ke.
        Map<LocalDate, BigDecimal> salesByDate = amountByDate(invoiceRepository.sumActiveTotalByDate(safeStart, safeEnd));
        Map<LocalDate, BigDecimal> collectionByDate = amountByDate(paymentRepository.sumActiveAmountByDate(safeStart, safeEnd));
        Object[] rangeSummary = firstRow(invoiceRepository.summarizeActiveInRange(safeStart, safeEnd), 3);
        List<LocalDate> customerCreatedDates = customerCreatedDates();
        List<Expense> expenses = expenseRepository.findInRange(safeStart, safeEnd);

        List<Expense> filteredExpenses = expenses.stream()
                .filter(expense -> isWithinRange(expense.getExpenseDate(), safeStart, safeEnd))
                .toList();

        BigDecimal totalSales = amountOf(rangeSummary[0]);
        long totalInvoices = countOf(rangeSummary[1]);
        BigDecimal totalCollection = sumBetween(collectionByDate, safeStart, safeEnd);
        BigDecimal totalExpense = filteredExpenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingAmount = calculateOutstandingAsOf(safeEnd);
        long newCustomers = customerCreatedDates.stream()
                .filter(createdDate -> isWithinRange(createdDate, safeStart, safeEnd))
                .count();

        List<LocalDate> periodStarts = buildPeriodStarts(safeStart, safeEnd);
        boolean useDailyBuckets = java.time.temporal.ChronoUnit.DAYS.between(safeStart, safeEnd) <= 45;
        List<MetricPointResponse> salesTrend = new ArrayList<>();
        List<MetricPointResponse> collectionTrend = new ArrayList<>();
        List<MetricPointResponse> expenseTrend = new ArrayList<>();
        List<MetricPointResponse> netProfitTrend = new ArrayList<>();
        List<MetricPointResponse> outstandingTrend = new ArrayList<>();
        List<MetricPointResponse> customerGrowthTrend = new ArrayList<>();

        BigDecimal openingOutstanding = calculateOutstandingBefore(safeStart, safeEnd);
        BigDecimal runningOutstanding = openingOutstanding;
        long runningCustomers = 0L;
        int index = 1;

        for (LocalDate periodStart : periodStarts) {
            LocalDate periodEnd = resolvePeriodEnd(periodStart, safeEnd, useDailyBuckets);
            BigDecimal periodSales = sumBetween(salesByDate, periodStart, periodEnd);
            BigDecimal periodCollection = sumBetween(collectionByDate, periodStart, periodEnd);
            BigDecimal periodExpense = filteredExpenses.stream()
                    .filter(expense -> isWithinRange(expense.getExpenseDate(), periodStart, periodEnd))
                    .map(Expense::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            long periodNewCustomers = customerCreatedDates.stream()
                    .filter(createdDate -> isWithinRange(createdDate, periodStart, periodEnd))
                    .count();

            runningOutstanding = scale(runningOutstanding.add(periodSales).subtract(periodCollection));
            runningCustomers += periodNewCustomers;

            String label = buildLabel(periodStart, useDailyBuckets);
            salesTrend.add(point(label, index, periodSales));
            collectionTrend.add(point(label, index, periodCollection));
            expenseTrend.add(point(label, index, periodExpense));
            netProfitTrend.add(point(label, index, revenueCalculationService.netRevenue(periodCollection, periodExpense)));
            outstandingTrend.add(point(label, index, runningOutstanding));
            customerGrowthTrend.add(point(label, index, BigDecimal.valueOf(runningCustomers)));
            index++;
        }

        List<MetricPointResponse> monthlyRevenue = buildMonthlyRevenueTrend(safeEnd);
        List<MetricPointResponse> expenseByCategory = buildExpenseByCategory(filteredExpenses);

        return OwnerAnalyticsResponse.builder()
                .startDate(safeStart)
                .endDate(safeEnd)
                .totalSales(scale(totalSales))
                .totalCollection(scale(totalCollection))
                .totalExpense(scale(totalExpense))
                .netRevenue(revenueCalculationService.netRevenue(totalCollection, totalExpense))
                .outstandingAmount(scale(outstandingAmount))
                .newCustomers(newCustomers)
                .totalInvoices(totalInvoices)
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

    // 12 mahine (endDate wale mahine tak, endDate ke baad ka nahi): ek din-wise group-by query.
    private List<MetricPointResponse> buildMonthlyRevenueTrend(LocalDate endDate) {
        List<MetricPointResponse> points = new ArrayList<>();
        YearMonth endMonth = YearMonth.from(endDate);
        Map<YearMonth, BigDecimal> salesByMonth = sumByMonth(amountByDate(invoiceRepository.sumActiveTotalByDate(endMonth.minusMonths(11).atDay(1), endDate)));
        for (int i = 11; i >= 0; i--) {
            YearMonth month = endMonth.minusMonths(i);
            BigDecimal amount = salesByMonth.getOrDefault(month, BigDecimal.ZERO);
            points.add(point(month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.getYear(), 12 - i, amount));
        }
        return points;
    }

    // startDate se pehle tak ka (aur historyEndDate se aage nahi) sales − collections, SQL sum se.
    private BigDecimal calculateOutstandingBefore(LocalDate startDate, LocalDate historyEndDate) {
        LocalDate beforeStart = earlierOf(startDate.minusDays(1), historyEndDate);
        BigDecimal historicalSales = salesInRange(null, beforeStart);
        BigDecimal historicalCollections = amountOf(paymentRepository.sumActiveAmountInRange(null, beforeStart));
        return scale(historicalSales.subtract(historicalCollections));
    }

    private BigDecimal calculateOutstandingAsOf(LocalDate endDate) {
        if (endDate == null || !endDate.isBefore(com.billing.core.AppDateTime.istToday())) {
            return amountOf(customerRepository.sumCurrentBalance());
        }

        BigDecimal salesUntilEnd = salesInRange(null, endDate);
        BigDecimal collectionsUntilEnd = amountOf(paymentRepository.sumActiveAmountInRange(null, endDate));

        return scale(salesUntilEnd.subtract(collectionsUntilEnd));
    }

    private BigDecimal salesInRange(LocalDate startDate, LocalDate endDate) {
        return amountOf(firstRow(invoiceRepository.summarizeActiveInRange(startDate, endDate), 3)[0]);
    }

    // Saare products ka current stock (sum remainingQty) ek group-by query se.
    private Map<Long, Integer> currentStockByProduct() {
        Map<Long, Integer> stockByProduct = new HashMap<>();
        for (Object[] row : productBatchRepository.sumRemainingQtyGroupByProduct()) {
            Long productId = DataTypeUtility.longValue(row[0]);
            if (productId != null) {
                stockByProduct.put(productId, DataTypeUtility.integerValue(row[1]));
            }
        }
        return stockByProduct;
    }

    // Customer createdAt (UTC wall-time) -> IST date; sirf ek column load hota hai.
    private List<LocalDate> customerCreatedDates() {
        return customerRepository.findAllCreatedAt().stream()
                .map(com.billing.core.AppDateTime::toIstDate)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Map<LocalDate, BigDecimal> amountByDate(List<Object[]> rows) {
        Map<LocalDate, BigDecimal> amountByDate = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] == null) {
                continue;
            }
            amountByDate.put((LocalDate) row[0], amountOf(row[1]));
        }
        return amountByDate;
    }

    private Map<YearMonth, BigDecimal> sumByMonth(Map<LocalDate, BigDecimal> amountByDate) {
        Map<YearMonth, BigDecimal> amountByMonth = new HashMap<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : amountByDate.entrySet()) {
            amountByMonth.merge(YearMonth.from(entry.getKey()), entry.getValue(), BigDecimal::add);
        }
        return amountByMonth;
    }

    private BigDecimal sumBetween(Map<LocalDate, BigDecimal> amountByDate, LocalDate startDate, LocalDate endDate) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> entry : amountByDate.entrySet()) {
            if (isWithinRange(entry.getKey(), startDate, endDate)) {
                total = total.add(entry.getValue());
            }
        }
        return total;
    }

    // Do upper bounds me se pehla (null = koi limit nahi).
    private LocalDate earlierOf(LocalDate firstDate, LocalDate secondDate) {
        if (firstDate == null) {
            return secondDate;
        }
        if (secondDate == null) {
            return firstDate;
        }
        return firstDate.isBefore(secondDate) ? firstDate : secondDate;
    }

    // Aggregate query ki pehli row; khali result par zero-filled row.
    private Object[] firstRow(List<Object[]> rows, int columnCount) {
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return new Object[columnCount];
        }
        return rows.get(0);
    }

    private BigDecimal amountOf(Object value) {
        BigDecimal amount = DataTypeUtility.bigDecimalObjectValue(value);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private long countOf(Object value) {
        Long count = DataTypeUtility.longValue(value);
        return count == null ? 0L : count;
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

    // "23 Oct" (daily) / "Oct 26" (monthly): akela "23" ya "Jan" chart par mahina/saal nahi batata,
    // aur saal paar karti range me "Jan" do baar aa kar series mix kar deta tha.
    private String buildLabel(LocalDate periodStart, boolean useDailyBuckets) {
        if (useDailyBuckets) {
            return periodStart.getDayOfMonth() + " " + periodStart.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        }
        YearMonth month = YearMonth.from(periodStart);
        return month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + String.format(Locale.ENGLISH, "%02d", month.getYear() % 100);
    }

    private MetricPointResponse point(String label, int index, BigDecimal value) {
        return MetricPointResponse.builder()
                .label(label)
                .index(index)
                .value(scale(value))
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

    // Charts: sirf us mahine/saal ka din-wise jod (SQL group by, entities nahi).
    private Map<LocalDate, BigDecimal> salesByDateInRange(String email, LocalDate startDate, LocalDate endDate) {
        accessControlService.requireCompany(accessControlService.getCurrentUser(email));
        return amountByDate(invoiceRepository.sumActiveTotalByDate(startDate, endDate));
    }

    private List<Product> productsFor(Company company) {
        return productRepository.findAllByOrderByCreatedAtDesc();
    }

    private List<ProductCategory> categoriesFor(Company company) {
        return productCategoryRepository.findAllByCompanyWithFilters(company, true, null);
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
