package com.billing.saas.service;

import com.billing.saas.dto.analytics.AnalyticsSummaryResponse;
import com.billing.saas.dto.analytics.CustomerDueResponse;
import com.billing.saas.dto.analytics.LowStockProductResponse;
import com.billing.saas.dto.analytics.SalesChartPointResponse;
import com.billing.saas.dto.analytics.SalesTrendStatus;
import com.billing.saas.dto.analytics.TopSellingProductResponse;
import com.billing.saas.entity.Company;
import com.billing.saas.entity.Customer;
import com.billing.saas.entity.Invoice;
import com.billing.saas.entity.InvoiceItem;
import com.billing.saas.entity.Product;
import com.billing.saas.repository.CustomerRepository;
import com.billing.saas.repository.InvoiceRepository;
import com.billing.saas.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
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
    private final CustomerRepository customerRepository;

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        List<Invoice> invoices = invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company);
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth lastMonth = thisMonth.minusMonths(1);

        BigDecimal todaySales = sumInvoiceSalesForDate(invoices, today);
        BigDecimal yesterdaySales = sumInvoiceSalesForDate(invoices, yesterday);
        BigDecimal thisMonthSales = sumInvoiceSalesForMonth(invoices, thisMonth);
        BigDecimal lastMonthSales = sumInvoiceSalesForMonth(invoices, lastMonth);
        BigDecimal outstanding = customerRepository.findByCompanyAndCurrentBalanceGreaterThanOrderByCurrentBalanceDesc(company, BigDecimal.ZERO)
                .stream()
                .map(Customer::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long lowStockCount = productRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .filter(Product::isActive)
                .filter(product -> product.getStockQty() <= product.getMinStockQty())
                .count();
        long dueCustomers = customerRepository.findByCompanyAndCurrentBalanceGreaterThanOrderByCurrentBalanceDesc(company, BigDecimal.ZERO).size();

        return AnalyticsSummaryResponse.builder()
                .todaySales(scale(todaySales))
                .yesterdaySales(scale(yesterdaySales))
                .thisMonthSales(scale(thisMonthSales))
                .lastMonthSales(scale(lastMonthSales))
                .totalOutstandingBalance(scale(outstanding))
                .lowStockProducts(lowStockCount)
                .dueCustomers(dueCustomers)
                .salesTrendPercentage(calculateTrendPercentage(thisMonthSales, lastMonthSales))
                .trendStatus(resolveTrendStatus(thisMonthSales, lastMonthSales))
                .build();
    }

    @Transactional(readOnly = true)
    public List<SalesChartPointResponse> dayWiseSales(String email, int year, int month) {
        Company company = accessControlService.getCurrentCompany(email);
        List<Invoice> invoices = invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company);
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
        Company company = accessControlService.getCurrentCompany(email);
        List<Invoice> invoices = invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company);
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
    public List<TopSellingProductResponse> topSellingProducts(String email, int limit) {
        Company company = accessControlService.getCurrentCompany(email);
        List<Invoice> invoices = invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company);
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

        return aggregates.values().stream()
                .sorted(Comparator.comparing(ProductSalesAggregate::getTotalQty, Comparator.reverseOrder())
                        .thenComparing(ProductSalesAggregate::getTotalSales, Comparator.reverseOrder()))
                .limit(Math.max(limit, 1))
                .map(aggregate -> TopSellingProductResponse.builder()
                        .productId(aggregate.product.getId())
                        .productName(aggregate.product.getName())
                        .sku(aggregate.product.getSku())
                        .totalQtySold(aggregate.totalQty)
                        .totalSalesAmount(scale(aggregate.totalSales))
                        .currentStockQty(aggregate.product.getStockQty())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LowStockProductResponse> lowStockProducts(String email, int limit) {
        Company company = accessControlService.getCurrentCompany(email);
        return productRepository.findByCompanyOrderByCreatedAtDesc(company).stream()
                .filter(Product::isActive)
                .filter(product -> product.getStockQty() <= product.getMinStockQty())
                .sorted(Comparator.comparing(Product::getStockQty).thenComparing(Product::getName))
                .limit(Math.max(limit, 1))
                .map(product -> LowStockProductResponse.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .sku(product.getSku())
                        .stockQty(product.getStockQty())
                        .minStockQty(product.getMinStockQty())
                        .active(product.isActive())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CustomerDueResponse> customerDueList(String email, int limit) {
        Company company = accessControlService.getCurrentCompany(email);
        return customerRepository.findByCompanyAndCurrentBalanceGreaterThanOrderByCurrentBalanceDesc(company, BigDecimal.ZERO).stream()
                .limit(Math.max(limit, 1))
                .map(customer -> CustomerDueResponse.builder()
                        .customerId(customer.getId())
                        .customerName(customer.getName())
                        .mobile(customer.getMobile())
                        .email(customer.getEmail())
                        .currentBalance(scale(customer.getCurrentBalance()))
                        .creditLimit(scale(customer.getCreditLimit()))
                        .build())
                .toList();
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
}
