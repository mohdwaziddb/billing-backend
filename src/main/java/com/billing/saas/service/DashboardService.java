package com.billing.saas.service;

import com.billing.saas.dto.dashboard.DashboardSummaryResponse;
import com.billing.saas.dto.dashboard.DashboardTopCustomerResponse;
import com.billing.saas.entity.Company;
import com.billing.saas.entity.Customer;
import com.billing.saas.entity.Invoice;
import com.billing.saas.entity.Payment;
import com.billing.saas.repository.CustomerRepository;
import com.billing.saas.repository.InvoiceRepository;
import com.billing.saas.repository.PaymentRepository;
import com.billing.saas.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(String email, LocalDate startDate, LocalDate endDate) {
        Company company = accessControlService.getCurrentCompany(email);
        List<Invoice> allInvoices = invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company);
        List<Payment> allPayments = paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company);
        List<Customer> allCustomers = customerRepository.findByCompanyOrderByCreatedAtDesc(company);

        LocalDate safeStart = startDate;
        LocalDate safeEnd = endDate;

        List<Invoice> filteredInvoices = allInvoices.stream()
                .filter(invoice -> isWithinRange(invoice.getInvoiceDate(), safeStart, safeEnd))
                .toList();
        List<Payment> filteredPayments = allPayments.stream()
                .filter(payment -> isWithinRange(payment.getPaymentDate(), safeStart, safeEnd))
                .toList();
        List<Customer> newCustomers = allCustomers.stream()
                .filter(customer -> customer.getCreatedAt() != null && isWithinRange(customer.getCreatedAt().toLocalDate(), safeStart, safeEnd))
                .toList();

        BigDecimal totalSales = filteredInvoices.stream()
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCollection = filteredPayments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = calculateOutstandingAsOf(allCustomers, allInvoices, allPayments, safeEnd);

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
                .limit(5)
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
                .newCustomers(newCustomers.size())
                .totalInvoices(filteredInvoices.size())
                .totalProducts(productRepository.countByCompany(company))
                .totalRevenue(scale(totalCollection))
                .outstandingBalance(scale(outstanding))
                .topCustomers(topCustomers)
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

    private BigDecimal calculateOutstandingAsOf(List<Customer> customers, List<Invoice> invoices, List<Payment> payments, LocalDate endDate) {
        if (endDate == null || !endDate.isBefore(LocalDate.now())) {
            return customers.stream()
                    .map(Customer::getCurrentBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal openingBalances = customers.stream()
                .filter(customer -> customer.getCreatedAt() == null || !customer.getCreatedAt().toLocalDate().isAfter(endDate))
                .map(Customer::getOpeningBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salesUntilEnd = invoices.stream()
                .filter(invoice -> invoice.getInvoiceDate() != null && !invoice.getInvoiceDate().isAfter(endDate))
                .map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collectionsUntilEnd = payments.stream()
                .filter(payment -> payment.getPaymentDate() != null && !payment.getPaymentDate().isAfter(endDate))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return scale(openingBalances.add(salesUntilEnd).subtract(collectionsUntilEnd));
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static final class DashboardCustomerAggregate {
        private final Customer customer;
        private BigDecimal totalPurchaseAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal totalPaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private LocalDate lastPurchaseDate;

        private DashboardCustomerAggregate(Customer customer) {
            this.customer = customer;
        }

        public BigDecimal getTotalPurchaseAmount() {
            return totalPurchaseAmount;
        }
    }
}
