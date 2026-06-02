package com.billing.saas.service;

import com.billing.saas.dto.customer.CustomerLedgerEntryResponse;
import com.billing.saas.dto.customer.CustomerLedgerResponse;
import com.billing.saas.dto.customer.CustomerRequest;
import com.billing.saas.dto.customer.CustomerResponse;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final AccessControlService accessControlService;

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

        return toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> list(String email, String search, Boolean active) {
        Company company = accessControlService.getCurrentCompany(email);
        return customerRepository.findAllByCompanyWithFilters(company, active, normalizeSearch(search)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(String email, Long customerId) {
        Company company = accessControlService.getCurrentCompany(email);
        return toResponse(getCustomerOrThrow(company, customerId));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByMobile(String email, String mobile) {
        Company company = accessControlService.getCurrentCompany(email);
        return customerRepository.findByCompanyAndMobileIgnoreCase(company, mobile == null ? null : mobile.trim())
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with this mobile number"));
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

        return toResponse(customerRepository.save(customer));
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
                .credit(BigDecimal.ZERO)
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
                        .credit(BigDecimal.ZERO)
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
                        .debit(BigDecimal.ZERO)
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
    public List<CustomerResponse> outstanding(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        return customerRepository.findByCompanyAndActiveTrueAndCurrentBalanceGreaterThanOrderByCurrentBalanceDesc(company, BigDecimal.ZERO).stream()
                .map(this::toResponse)
                .toList();
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

    private void validateUnique(Company company, CustomerRequest request, Long customerId) {
        if (customerId == null) {
            if (customerRepository.existsByCompanyAndMobileIgnoreCase(company, request.getMobile())) {
                throw new BadRequestException("Customer mobile already exists");
            }
            if (request.getEmail() != null && !request.getEmail().isBlank()
                    && customerRepository.existsByCompanyAndEmailIgnoreCase(company, request.getEmail())) {
                throw new BadRequestException("Customer email already exists");
            }
            return;
        }

        if (customerRepository.existsByCompanyAndMobileIgnoreCaseAndIdNot(company, request.getMobile(), customerId)) {
            throw new BadRequestException("Customer mobile already exists");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && customerRepository.existsByCompanyAndEmailIgnoreCaseAndIdNot(company, request.getEmail(), customerId)) {
            throw new BadRequestException("Customer email already exists");
        }
    }

    private CustomerResponse toResponse(Customer customer) {
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
                .active(customer.isActive())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .createdBy(customer.getCreatedBy())
                .updatedBy(customer.getUpdatedBy())
                .build();
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP) : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
