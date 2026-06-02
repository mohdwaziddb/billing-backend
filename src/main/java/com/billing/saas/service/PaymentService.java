package com.billing.saas.service;

import com.billing.saas.dto.payment.PaymentRequest;
import com.billing.saas.dto.payment.PaymentResponse;
import com.billing.saas.entity.Company;
import com.billing.saas.entity.Customer;
import com.billing.saas.entity.Invoice;
import com.billing.saas.entity.Payment;
import com.billing.saas.exception.BadRequestException;
import com.billing.saas.exception.ResourceNotFoundException;
import com.billing.saas.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccessControlService accessControlService;
    private final CustomerService customerService;
    private final InvoiceService invoiceService;

    @Transactional
    public PaymentResponse create(String email, PaymentRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = customerService.getCustomerOrThrow(company, request.getCustomerId());
        Invoice invoice = resolveInvoice(company, request.getInvoiceId(), customer);
        BigDecimal amount = scale(request.getAmount());

        customerService.decreaseBalance(customer, amount);
        if (invoice != null) {
            invoiceService.applyPayment(invoice, amount);
        }

        Payment payment = Payment.builder()
                .company(company)
                .customer(customer)
                .invoice(invoice)
                .amount(amount)
                .paymentDate(request.getPaymentDate())
                .mode(request.getMode())
                .remarks(request.getRemarks())
                .build();

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        return paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(company).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(String email, Long paymentId) {
        Company company = accessControlService.getCurrentCompany(email);
        return toResponse(getPaymentOrThrow(company, paymentId));
    }

    @Transactional
    public PaymentResponse update(String email, Long paymentId, PaymentRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Payment payment = getPaymentOrThrow(company, paymentId);

        revertPayment(payment);

        Customer customer = customerService.getCustomerOrThrow(company, request.getCustomerId());
        Invoice invoice = resolveInvoice(company, request.getInvoiceId(), customer);
        BigDecimal amount = scale(request.getAmount());

        customerService.decreaseBalance(customer, amount);
        if (invoice != null) {
            invoiceService.applyPayment(invoice, amount);
        }

        payment.setCustomer(customer);
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setPaymentDate(request.getPaymentDate());
        payment.setMode(request.getMode());
        payment.setRemarks(request.getRemarks());

        return toResponse(paymentRepository.save(payment));
    }

    @Transactional
    public void delete(String email, Long paymentId) {
        Company company = accessControlService.getCurrentCompany(email);
        Payment payment = getPaymentOrThrow(company, paymentId);
        revertPayment(payment);
        paymentRepository.delete(payment);
    }

    private Payment getPaymentOrThrow(Company company, Long paymentId) {
        return paymentRepository.findByIdAndCompany(paymentId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    private Invoice resolveInvoice(Company company, Long invoiceId, Customer customer) {
        if (invoiceId == null) {
            return null;
        }
        Invoice invoice = invoiceService.getInvoiceOrThrow(company, invoiceId);
        if (!invoice.getCustomer().getId().equals(customer.getId())) {
            throw new BadRequestException("Payment customer and invoice customer must match");
        }
        return invoice;
    }

    private void revertPayment(Payment payment) {
        customerService.increaseBalance(payment.getCustomer(), scale(payment.getAmount()));
        if (payment.getInvoice() != null) {
            invoiceService.reversePayment(payment.getInvoice(), scale(payment.getAmount()));
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .customerId(payment.getCustomer().getId())
                .customerName(payment.getCustomer().getName())
                .invoiceId(payment.getInvoice() != null ? payment.getInvoice().getId() : null)
                .invoiceNo(payment.getInvoice() != null ? payment.getInvoice().getInvoiceNo() : null)
                .amount(scale(payment.getAmount()))
                .paymentDate(payment.getPaymentDate())
                .mode(payment.getMode().name())
                .remarks(payment.getRemarks())
                .createdAt(payment.getCreatedAt())
                .createdBy(payment.getCreatedBy())
                .build();
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
