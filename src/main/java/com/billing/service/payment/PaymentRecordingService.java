package com.billing.service.payment;

import com.billing.entity.Invoice;
import com.billing.entity.Payment;
import com.billing.entity.enums.PaymentSource;
import com.billing.exception.BadRequestException;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentRepository;
import com.billing.service.AuditLogService;
import com.billing.service.CustomerService;
import com.billing.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Payment record karne ka EK common path: customer balance ghatana, invoice
 * par apply (row lock ke saath), Payment row save aur audit. Invoice initial
 * payment, Record Payment aur online gateway settlement teeno yahi use karte hain.
 */
@Service
@RequiredArgsConstructor
public class PaymentRecordingService {

    private final PaymentRepository paymentRepository;
    private final CustomerService customerService;
    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepository;
    private final AuditLogService auditLogService;

    /** Ek invoice (ya bina invoice, customer balance) par payment. */
    @Transactional
    public Payment record(PaymentEntry entry) {
        BigDecimal amount = scale(entry.amount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be greater than 0");
        }
        Invoice invoice = entry.invoice();
        if (invoice != null && amount.compareTo(scale(invoice.getBalanceAmount())) > 0) {
            throw new BadRequestException("Paid amount cannot exceed invoice outstanding amount");
        }
        BigDecimal oldOutstanding = invoice != null ? scale(invoice.getBalanceAmount()) : null;

        customerService.decreaseBalance(entry.customer(), amount);
        if (invoice != null) {
            invoiceService.applyPayment(invoice, amount);
        }

        Payment saved = paymentRepository.save(Payment.builder()
                .company(entry.company())
                .customer(entry.customer())
                .invoice(invoice)
                .amount(amount)
                .paymentDate(entry.paymentDate())
                .mode(entry.modeCode())
                .remarks(entry.remarks())
                .referenceNo(blankToNull(entry.referenceNo()))
                .source(entry.source() == null ? PaymentSource.MANUAL : entry.source())
                .paymentTransaction(entry.transaction())
                .build());

        if (entry.systemActor()) {
            auditLogService.logCreateAsActor(entry.actorName(), entry.company(), "Payment", "Payment", saved.getId(), snapshot(saved));
        } else {
            auditLogService.logCreate(entry.actorEmail(), entry.company(), "Payment", "Payment", saved.getId(), snapshot(saved));
        }
        if (invoice != null) {
            invoiceService.logPaymentApplied(entry.actorEmail(), entry.actorName(), entry.company(), invoice, amount,
                    oldOutstanding, invoice.getBalanceAmount(), saved.getMode());
        }
        return saved;
    }

    /**
     * Pehle given invoice (agar ho), phir customer ke baaki open invoices par
     * FIFO (sabse purana pehle); bacha amount unlinked (advance / opening balance).
     * Har invoice ke liye alag Payment row.
     */
    @Transactional
    public List<Payment> recordAllocated(PaymentEntry entry) {
        BigDecimal remaining = scale(entry.amount());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be greater than 0");
        }
        List<Payment> saved = new ArrayList<>();
        Invoice preferred = entry.invoice();
        if (preferred != null && !preferred.isDeleted()) {
            BigDecimal part = remaining.min(scale(preferred.getBalanceAmount()));
            if (part.compareTo(BigDecimal.ZERO) > 0) {
                saved.add(record(entry.withInvoiceAndAmount(preferred, part)));
                remaining = scale(remaining.subtract(part));
            }
        }
        for (Invoice openInvoice : invoiceRepository.lockOpenInvoicesOldestFirst(entry.customer())) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            if (preferred != null && openInvoice.getId().equals(preferred.getId())) {
                continue;
            }
            BigDecimal part = remaining.min(scale(openInvoice.getBalanceAmount()));
            if (part.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            saved.add(record(entry.withInvoiceAndAmount(openInvoice, part)));
            remaining = scale(remaining.subtract(part));
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            saved.add(record(entry.withInvoiceAndAmount(null, remaining)));
        }
        return saved;
    }

    public Map<String, Object> snapshot(Payment payment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerId", payment.getCustomer().getId());
        data.put("customerName", payment.getCustomer().getName());
        data.put("invoiceId", payment.getInvoice() != null ? payment.getInvoice().getId() : null);
        data.put("invoiceNo", payment.getInvoice() != null ? payment.getInvoice().getInvoiceNo() : null);
        data.put("amount", scale(payment.getAmount()));
        data.put("paymentDate", payment.getPaymentDate());
        data.put("mode", payment.getMode());
        data.put("referenceNo", payment.getReferenceNo());
        data.put("source", payment.getSource() == null ? PaymentSource.MANUAL.name() : payment.getSource().name());
        data.put("remarks", payment.getRemarks());
        data.put("deleted", payment.isDeleted());
        return data;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
