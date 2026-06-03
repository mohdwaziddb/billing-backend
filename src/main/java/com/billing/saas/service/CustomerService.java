package com.billing.saas.service;

import com.billing.saas.dto.customer.CustomerLedgerEntryResponse;
import com.billing.saas.dto.customer.CustomerLedgerResponse;
import com.billing.saas.dto.customer.CustomerPurchaseHistoryResponse;
import com.billing.saas.dto.customer.CustomerRequest;
import com.billing.saas.dto.customer.CustomerResponse;
import com.billing.saas.dto.customer.CustomerSummaryMetrics;
import com.billing.saas.dto.invoice.InvoiceItemResponse;
import com.billing.saas.dto.invoice.InvoiceResponse;
import com.billing.saas.entity.Company;
import com.billing.saas.entity.Customer;
import com.billing.saas.entity.Invoice;
import com.billing.saas.entity.Payment;
import com.billing.saas.exception.BadRequestException;
import com.billing.saas.exception.ResourceNotFoundException;
import com.billing.saas.repository.CustomerRepository;
import com.billing.saas.repository.InvoiceRepository;
import com.billing.saas.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final AccessControlService accessControlService;
    private final AuditNameResolver auditNameResolver;

    @Transactional
    public CustomerResponse create(String email, CustomerRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        validateUnique(company, request, null);

        Customer customer = Customer.builder()
                .company(company)
                .name(request.getName())
                .mobile(request.getMobile())
                .email(blankToNull(request.getEmail()))
                .address(request.getAddress())
                .gstNo(request.getGstNo())
                .openingBalance(scale(request.getOpeningBalance()))
                .currentBalance(scale(request.getOpeningBalance()))
                .creditLimit(scale(request.getCreditLimit()))
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();

        Customer saved = customerRepository.save(customer);
        return toResponse(saved, emptyMetrics(saved));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list(String email, String search, Boolean active) {
        Company company = accessControlService.getCurrentCompany(email);
        List<Customer> customers = customerRepository.findAllByCompanyWithFilters(company, active, normalizeSearch(search));
        return toResponses(company, customers);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(String email, Long customerId) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = getCustomerOrThrow(company, customerId);
        return toResponse(customer, loadMetrics(company, List.of(customer)).get(customer.getId()));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByMobile(String email, String mobile) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = customerRepository.findByCompanyAndMobileIgnoreCase(company, mobile == null ? null : mobile.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with this mobile number"));
        return toResponse(customer, loadMetrics(company, List.of(customer)).get(customer.getId()));
    }

    @Transactional
    public CustomerResponse update(String email, Long customerId, CustomerRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = getCustomerOrThrow(company, customerId);
        validateUnique(company, request, customerId);

        BigDecimal newOpeningBalance = scale(request.getOpeningBalance());
        BigDecimal openingDelta = newOpeningBalance.subtract(customer.getOpeningBalance());
        BigDecimal updatedBalance = customer.getCurrentBalance().add(openingDelta);
        if (updatedBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Opening balance update would make current balance negative");
        }

        customer.setName(request.getName());
        customer.setMobile(request.getMobile());
        customer.setEmail(blankToNull(request.getEmail()));
        customer.setAddress(request.getAddress());
        customer.setGstNo(request.getGstNo());
        customer.setOpeningBalance(newOpeningBalance);
        customer.setCurrentBalance(scale(updatedBalance));
        customer.setCreditLimit(scale(request.getCreditLimit()));
        customer.setActive(Boolean.TRUE.equals(request.getActive()));

        Customer saved = customerRepository.save(customer);
        return toResponse(saved, loadMetrics(company, List.of(saved)).get(saved.getId()));
    }

    @Transactional
    public void delete(String email, Long customerId) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = getCustomerOrThrow(company, customerId);
        if (customer.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("Customer has outstanding balance and cannot be deleted");
        }
        customer.setActive(false);
        customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public CustomerLedgerResponse ledger(String email, Long customerId) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = getCustomerOrThrow(company, customerId);
        List<Invoice> invoices = invoiceRepository.findByCompanyAndCustomerOrderByInvoiceDateDescIdDesc(company, customer);
        List<Payment> payments = paymentRepository.findByCompanyAndCustomerOrderByPaymentDateDescIdDesc(company, customer);

        List<CustomerLedgerEntryResponse> entries = new ArrayList<>();
        BigDecimal runningBalance = scale(customer.getOpeningBalance());

        entries.add(CustomerLedgerEntryResponse.builder()
                .type("OPENING")
                .referenceId(customer.getId())
                .referenceNo("OPENING")
                .entryDate(customer.getCreatedAt() != null ? customer.getCreatedAt().toLocalDate() : LocalDate.now())
                .debit(scale(customer.getOpeningBalance()))
                .credit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .runningBalance(runningBalance)
                .remarks("Opening balance")
                .build());

        class LedgerRow {
            private final LocalDate date;
            private final int order;
            private final CustomerLedgerEntryResponse entry;

            private LedgerRow(LocalDate date, int order, CustomerLedgerEntryResponse entry) {
                this.date = date;
                this.order = order;
                this.entry = entry;
            }
        }

        List<LedgerRow> rows = new ArrayList<>();
        invoices.forEach(invoice -> rows.add(new LedgerRow(
                invoice.getInvoiceDate(),
                1,
                CustomerLedgerEntryResponse.builder()
                        .type("INVOICE")
                        .referenceId(invoice.getId())
                        .referenceNo(invoice.getInvoiceNo())
                        .entryDate(invoice.getInvoiceDate())
                        .debit(scale(invoice.getTotalAmount()))
                        .credit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .remarks("Invoice billed")
                        .build()
        )));

        payments.forEach(payment -> rows.add(new LedgerRow(
                payment.getPaymentDate(),
                2,
                CustomerLedgerEntryResponse.builder()
                        .type("PAYMENT")
                        .referenceId(payment.getId())
                        .referenceNo(payment.getInvoice() != null ? payment.getInvoice().getInvoiceNo() : "ADVANCE")
                        .entryDate(payment.getPaymentDate())
                        .debit(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                        .credit(scale(payment.getAmount()))
                        .remarks(payment.getRemarks())
                        .build()
        )));

        List<LedgerRow> sortedRows = rows.stream()
                .sorted(Comparator.comparing((LedgerRow row) -> row.date).thenComparingInt(row -> row.order))
                .toList();

        for (LedgerRow row : sortedRows) {
            runningBalance = runningBalance.add(row.entry.getDebit()).subtract(row.entry.getCredit());
            entries.add(CustomerLedgerEntryResponse.builder()
                    .type(row.entry.getType())
                    .referenceId(row.entry.getReferenceId())
                    .referenceNo(row.entry.getReferenceNo())
                    .entryDate(row.entry.getEntryDate())
                    .debit(row.entry.getDebit())
                    .credit(row.entry.getCredit())
                    .runningBalance(scale(runningBalance))
                    .remarks(row.entry.getRemarks())
                    .build());
        }

        return CustomerLedgerResponse.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .openingBalance(scale(customer.getOpeningBalance()))
                .currentBalance(scale(customer.getCurrentBalance()))
                .entries(entries)
                .build();
    }

    @Transactional(readOnly = true)
    public CustomerPurchaseHistoryResponse purchaseHistory(String email, Long customerId) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = getCustomerOrThrow(company, customerId);
        List<Invoice> invoices = invoiceRepository.findByCompanyAndCustomerOrderByInvoiceDateDescIdDesc(company, customer);
        CustomerSummaryMetrics summary = loadMetrics(company, List.of(customer)).get(customer.getId());

        return CustomerPurchaseHistoryResponse.builder()
                .customerId(customer.getId())
                .customerName(customer.getName())
                .mobile(customer.getMobile())
                .address(customer.getAddress())
                .summary(summary)
                .invoices(invoices.stream().map(this::toInvoiceResponse).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> outstanding(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        List<Customer> customers = customerRepository.findByCompanyAndActiveTrueAndCurrentBalanceGreaterThanOrderByCurrentBalanceDesc(company, BigDecimal.ZERO);
        return toResponses(company, customers);
    }

    public Customer getCustomerOrThrow(Company company, Long customerId) {
        return customerRepository.findByIdAndCompany(customerId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    public void increaseBalance(Customer customer, BigDecimal amount) {
        BigDecimal updated = customer.getCurrentBalance().add(amount);
        if (updated.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Customer balance cannot become negative");
        }
        customer.setCurrentBalance(scale(updated));
        customerRepository.save(customer);
    }

    public void decreaseBalance(Customer customer, BigDecimal amount) {
        BigDecimal updated = customer.getCurrentBalance().subtract(amount);
        if (updated.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Payment exceeds customer outstanding balance");
        }
        customer.setCurrentBalance(scale(updated));
        customerRepository.save(customer);
    }

    private List<CustomerResponse> toResponses(Company company, List<Customer> customers) {
        Map<Long, CustomerSummaryMetrics> metricsByCustomer = loadMetrics(company, customers);
        return customers.stream()
                .map(customer -> toResponse(customer, metricsByCustomer.get(customer.getId())))
                .toList();
    }

    private Map<Long, CustomerSummaryMetrics> loadMetrics(Company company, List<Customer> customers) {
        Map<Long, CustomerSummaryMetricsBuilderState> states = new HashMap<>();
        for (Customer customer : customers) {
          states.put(customer.getId(), new CustomerSummaryMetricsBuilderState(customer));
        }
        if (states.isEmpty()) {
            return Map.of();
        }

        List<Invoice> invoices = invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company);
        for (Invoice invoice : invoices) {
            CustomerSummaryMetricsBuilderState state = states.get(invoice.getCustomer().getId());
            if (state == null) {
                continue;
            }
            state.totalPurchaseAmount = state.totalPurchaseAmount.add(scale(invoice.getTotalAmount()));
            state.totalDiscountGiven = state.totalDiscountGiven.add(scale(invoice.getDiscountAmount()));
            state.hasPurchaseHistory = true;
            if (state.lastPurchaseDate == null || invoice.getInvoiceDate().isAfter(state.lastPurchaseDate)) {
                state.lastPurchaseDate = invoice.getInvoiceDate();
            }
        }

        List<Payment> payments = paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company);
        for (Payment payment : payments) {
            CustomerSummaryMetricsBuilderState state = states.get(payment.getCustomer().getId());
            if (state == null) {
                continue;
            }
            state.totalPaidAmount = state.totalPaidAmount.add(scale(payment.getAmount()));
        }

        Map<Long, CustomerSummaryMetrics> metricsByCustomer = new HashMap<>();
        for (Map.Entry<Long, CustomerSummaryMetricsBuilderState> entry : states.entrySet()) {
            CustomerSummaryMetricsBuilderState state = entry.getValue();
            metricsByCustomer.put(entry.getKey(), CustomerSummaryMetrics.builder()
                    .totalPurchaseAmount(scale(state.totalPurchaseAmount))
                    .totalPaidAmount(scale(state.totalPaidAmount))
                    .totalDiscountGiven(scale(state.totalDiscountGiven))
                    .outstandingBalance(scale(state.outstandingBalance))
                    .lastPurchaseDate(state.lastPurchaseDate)
                    .hasPurchaseHistory(state.hasPurchaseHistory)
                    .build());
        }
        return metricsByCustomer;
    }

    private CustomerSummaryMetrics emptyMetrics(Customer customer) {
        return CustomerSummaryMetrics.builder()
                .totalPurchaseAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .totalPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .totalDiscountGiven(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .outstandingBalance(scale(customer.getCurrentBalance()))
                .lastPurchaseDate(null)
                .hasPurchaseHistory(false)
                .build();
    }

    private void validateUnique(Company company, CustomerRequest request, Long customerId) {
        if (customerId == null) {
            if (customerRepository.existsByCompanyAndMobileIgnoreCase(company, request.getMobile())) {
                throw new BadRequestException("This phone number is already registered.");
            }
            if (request.getEmail() != null && !request.getEmail().isBlank()
                    && customerRepository.existsByCompanyAndEmailIgnoreCase(company, request.getEmail())) {
                throw new BadRequestException("This email address is already registered.");
            }
            return;
        }

        if (customerRepository.existsByCompanyAndMobileIgnoreCaseAndIdNot(company, request.getMobile(), customerId)) {
            throw new BadRequestException("This phone number is already registered.");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && customerRepository.existsByCompanyAndEmailIgnoreCaseAndIdNot(company, request.getEmail(), customerId)) {
            throw new BadRequestException("This email address is already registered.");
        }
    }

    private CustomerResponse toResponse(Customer customer, CustomerSummaryMetrics metrics) {
        CustomerSummaryMetrics safeMetrics = metrics == null ? emptyMetrics(customer) : metrics;
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .mobile(customer.getMobile())
                .email(customer.getEmail())
                .address(customer.getAddress())
                .gstNo(customer.getGstNo())
                .openingBalance(scale(customer.getOpeningBalance()))
                .currentBalance(scale(customer.getCurrentBalance()))
                .creditLimit(scale(customer.getCreditLimit()))
                .totalPurchaseAmount(scale(safeMetrics.getTotalPurchaseAmount()))
                .totalPaidAmount(scale(safeMetrics.getTotalPaidAmount()))
                .totalDiscountGiven(scale(safeMetrics.getTotalDiscountGiven()))
                .outstandingBalance(scale(safeMetrics.getOutstandingBalance()))
                .lastPurchaseDate(safeMetrics.getLastPurchaseDate())
                .hasPurchaseHistory(safeMetrics.isHasPurchaseHistory())
                .active(customer.isActive())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .createdBy(customer.getCreatedBy())
                .updatedBy(customer.getUpdatedBy())
                .build();
    }

    private InvoiceResponse toInvoiceResponse(Invoice invoice) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNo(invoice.getInvoiceNo())
                .customerId(invoice.getCustomer().getId())
                .customerName(invoice.getCustomer().getName())
                .customerMobile(invoice.getCustomer().getMobile())
                .customerAddress(invoice.getCustomer().getAddress())
                .subtotal(scale(invoice.getSubtotal()))
                .taxAmount(scale(invoice.getTaxAmount()))
                .discountAmount(scale(invoice.getDiscountAmount()))
                .totalAmount(scale(invoice.getTotalAmount()))
                .paidAmount(scale(invoice.getPaidAmount()))
                .balanceAmount(scale(invoice.getBalanceAmount()))
                .paymentStatus(invoice.getPaymentStatus().name())
                .invoiceDate(invoice.getInvoiceDate())
                .createdAt(invoice.getCreatedAt())
                .updatedAt(invoice.getUpdatedAt())
                .createdBy(auditNameResolver.displayName(invoice.getCreatedBy()))
                .updatedBy(auditNameResolver.displayName(invoice.getUpdatedBy()))
                .items(invoice.getItems().stream().map(item -> InvoiceItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .qty(item.getQty())
                        .price(scale(item.getPrice()))
                        .discountPercent(scale(item.getDiscountPercent()))
                        .taxPercent(scale(item.getTaxPercent()))
                        .lineTotal(scale(item.getLineTotal()))
                        .build()).toList())
                .build();
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static final class CustomerSummaryMetricsBuilderState {
        private BigDecimal totalPurchaseAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal totalPaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal totalDiscountGiven = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private final BigDecimal outstandingBalance;
        private LocalDate lastPurchaseDate;
        private boolean hasPurchaseHistory;

        private CustomerSummaryMetricsBuilderState(Customer customer) {
            this.outstandingBalance = customer.getCurrentBalance() == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : customer.getCurrentBalance().setScale(2, RoundingMode.HALF_UP);
        }
    }
}
