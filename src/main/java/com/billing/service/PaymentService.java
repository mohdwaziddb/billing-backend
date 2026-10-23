package com.billing.service;

import com.billing.entity.Company;
import com.billing.dto.PageResponse;
import com.billing.entity.Payment;
import com.billing.entity.enums.RoleName;
import com.billing.exception.ResourceNotFoundException;
import com.billing.dto.payment.PaymentRequest;
import com.billing.dto.payment.PaymentResponse;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.User;
import com.billing.entity.enums.PaymentSource;
import com.billing.exception.BadRequestException;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentRepository;
import com.billing.service.payment.PaymentEntry;
import com.billing.service.payment.PaymentRecordingService;
import com.billing.util.DataTypeUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final AccessControlService accessControlService;
    private final CustomerService customerService;
    private final InvoiceService invoiceService;
    private final AuditLogService auditLogService;
    private final PaymentModeMasterService paymentModeMasterService;
    private final AuditNameResolver auditNameResolver;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRecordingService paymentRecordingService;

    @Transactional
    public PaymentResponse create(String email, PaymentRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = customerService.getCustomerOrThrow(company, request.getCustomerId());
        Invoice invoice = resolveInvoice(company, request.getInvoiceId(), customer);
        BigDecimal amount = requirePositiveAmount(request.getAmount());
        requirePaymentDate(request);
        paymentModeMasterService.ensureDefaults(company);
        String mode = paymentModeMasterService.requireActiveOfflineModeCode(company, request.getMode());
        PaymentEntry entry = new PaymentEntry(company, email, null, customer, invoice, amount, request.getPaymentDate(),
                mode, request.getRemarks(), request.getReferenceNo(), PaymentSource.MANUAL, null);

        if (invoice == null) {
            // Invoice select kiye bina: customer ke open invoices par FIFO (sabse purana pehle),
            // har invoice ki alag Payment row; bacha amount unlinked rehta hai.
            return toResponse(paymentRecordingService.recordAllocated(entry).get(0));
        }
        return toResponse(paymentRecordingService.record(entry));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(String email) {
        User user = accessControlService.getCurrentUser(email);
        List<Payment> payments = paymentRepository.findByCompanyOrderByPaymentDateDescIdDesc(accessControlService.requireCompany(user));
        return payments.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> page(String email, int page, int size) {
        return page(email, null, null, null, null, null, null, null, null, null, null, "ACTIVE", page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> page(Map<String, Object> param, String email) {
        String search = DataTypeUtility.stringValue(param.get("search"));
        if (search.length() == 0) {
            search = null;
        }
        String paymentStatus = DataTypeUtility.stringValue(param.get("paymentStatus"));
        if (paymentStatus.length() == 0) {
            paymentStatus = null;
        }
        LocalDate startDate = DataTypeUtility.parseFlexibleDate(param, "startDate");
        LocalDate endDate = DataTypeUtility.parseFlexibleDate(param, "endDate");
        BigDecimal minAmount = DataTypeUtility.bigDecimalObjectValue(param.get("minAmount"));
        BigDecimal maxAmount = DataTypeUtility.bigDecimalObjectValue(param.get("maxAmount"));
        String mode = DataTypeUtility.stringValue(param.get("mode"));
        if (mode.length() == 0) {
            mode = null;
        }
        Boolean invoiceLinked = null;
        Object invoiceLinkedRaw = param.get("invoiceLinked");
        if (invoiceLinkedRaw != null) {
            String invoiceLinkedStr = DataTypeUtility.stringValue(invoiceLinkedRaw);
            if (invoiceLinkedStr.length() > 0) {
                invoiceLinked = DataTypeUtility.booleanValue(invoiceLinkedRaw);
            }
        }
        RoleName createdByRole = null;
        String createdByRoleStr = DataTypeUtility.stringValue(param.get("createdByRole"));
        if (createdByRoleStr.length() > 0) {
            try {
                createdByRole = RoleName.valueOf(createdByRoleStr);
            } catch (Exception ignore) {
            }
        }
        // Invoice detail page sirf us invoice ke payments maangta hai (pehle saare laakar filter hota tha).
        Long invoiceId = DataTypeUtility.getForeignKeyValue(param.get("invoiceId"));
        String recordStatus = DataTypeUtility.stringValue(param.get("recordStatus"));
        if (recordStatus.length() == 0) {
            recordStatus = "ACTIVE";
        }
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return page(email, search, paymentStatus, startDate, endDate, minAmount, maxAmount, mode, invoiceLinked, createdByRole, invoiceId, recordStatus, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> page(String email,
                                              String search,
                                              String paymentStatus,
                                              LocalDate startDate,
                                              LocalDate endDate,
                                              BigDecimal minAmount,
                                              BigDecimal maxAmount,
                                              String mode,
                                              Boolean invoiceLinked,
                                              RoleName createdByRole,
                                              Long invoiceId,
                                              String recordStatus,
                                              int page,
                                              int size) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        int safeSize = Math.max(1, Math.min(size, 1000));
        PageRequest pageable = PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "paymentDate").and(Sort.by(Sort.Direction.DESC, "id")));
        if (isUnsupportedPaymentStatus(paymentStatus)) {
            return PageResponse.<PaymentResponse>builder()
                    .records(List.of())
                    .page(Math.max(0, page))
                    .size(safeSize)
                    .totalRecords(0)
                    .totalPages(0)
                    .build();
        }
        return PageResponse.from(paymentRepository.searchPayments(
                company,
                resolveDeletedFilter(recordStatus),
                blankToNull(search),
                startDate,
                endDate,
                minAmount,
                maxAmount,
                blankToNull(mode) == null ? null : blankToNull(mode).toUpperCase(java.util.Locale.ENGLISH),
                invoiceLinked,
                createdByRole,
                invoiceId,
                pageable
        ).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(String email, Long paymentId) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        return toResponse(getPaymentOrThrow(company, paymentId));
    }

    @Transactional
    public PaymentResponse update(String email, Long paymentId, PaymentRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Payment payment = getPaymentOrThrow(company, paymentId);
        if (payment.isDeleted()) {
            throw new BadRequestException("Restore payment before editing it");
        }
        if (payment.getSource() == PaymentSource.GATEWAY) {
            throw new BadRequestException("Online gateway payments cannot be edited");
        }
        Map<String, Object> oldData = snapshot(payment);
        Invoice previousInvoice = payment.getInvoice();
        BigDecimal previousAmount = scale(payment.getAmount());
        BigDecimal previousOutstanding = previousInvoice != null ? scale(previousInvoice.getBalanceAmount()) : null;

        revertPayment(payment);

        Customer customer = customerService.getCustomerOrThrow(company, request.getCustomerId());
        Invoice invoice = resolveInvoice(company, request.getInvoiceId(), customer);
        BigDecimal amount = requirePositiveAmount(request.getAmount());
        requirePaymentDate(request);
        if (invoice == null && !invoiceRepository.lockOpenInvoicesOldestFirst(customer).isEmpty()) {
            // Edit me auto-split nahi (ek payment row = ek invoice). Bina invoice ke
            // customer balance ghatta aur invoice UNPAID reh jata tha.
            throw new BadRequestException("This customer has unpaid invoices. Select the invoice this payment is for.");
        }
        paymentModeMasterService.ensureDefaults(company);
        String mode = paymentModeMasterService.requireActiveOfflineModeCode(company, request.getMode());

        customerService.decreaseBalance(customer, amount);
        BigDecimal nextOldOutstanding = invoice != null && invoice.equals(previousInvoice) && previousOutstanding != null
                ? previousOutstanding
                : invoice != null ? scale(invoice.getBalanceAmount()) : null;
        if (invoice != null) {
            invoiceService.applyPayment(invoice, amount);
        }

        payment.setCustomer(customer);
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setPaymentDate(request.getPaymentDate());
        payment.setMode(mode);
        payment.setRemarks(request.getRemarks());
        payment.setReferenceNo(blankToNull(request.getReferenceNo()));

        Payment saved = paymentRepository.save(payment);
        auditLogService.logUpdate(email, company, "Payment", "Payment", saved.getId(), oldData, snapshot(saved));
        if (previousInvoice != null && !previousInvoice.equals(invoice)) {
            invoiceService.logPaymentUpdated(email, company, previousInvoice, previousAmount.negate(), previousOutstanding, previousInvoice.getBalanceAmount(), oldData.get("mode") != null ? String.valueOf(oldData.get("mode")) : null);
        }
        if (invoice != null) {
            invoiceService.logPaymentUpdated(email, company, invoice, amount, nextOldOutstanding, invoice.getBalanceAmount(), saved.getMode());
        }
        return toResponse(saved);
    }

    @Transactional
    public PaymentResponse create(Map<String, Object> param, String email) {
        PaymentRequest paymentRequest = mapToPaymentRequest(param);
        return create(email, paymentRequest);
    }

    @Transactional
    public PaymentResponse update(Map<String, Object> param, Long paymentId, String email) {
        PaymentRequest paymentRequest = mapToPaymentRequest(param);
        return update(email, paymentId, paymentRequest);
    }

    @Transactional
    public void delete(String email, Long paymentId) {
        Company company = accessControlService.getCurrentCompany(email);
        Payment payment = getPaymentOrThrow(company, paymentId);
        if (payment.isDeleted()) {
            return;
        }
        Map<String, Object> oldData = snapshot(payment);
        Invoice invoice = payment.getInvoice();
        BigDecimal amount = scale(payment.getAmount());
        BigDecimal oldOutstanding = invoice != null ? scale(invoice.getBalanceAmount()) : null;
        revertPayment(payment);
        payment.setDeleted(true);
        paymentRepository.save(payment);
        auditLogService.logCustomUpdate(email, company, "Payment", "Payment", paymentId, "PAYMENT_DELETED", oldData, snapshot(payment));
        if (invoice != null) {
            invoiceService.logPaymentDeleted(email, company, invoice, amount, oldOutstanding, invoice.getBalanceAmount(), oldData.get("mode") != null ? String.valueOf(oldData.get("mode")) : null);
        }
    }

    @Transactional
    public PaymentResponse restore(String email, Long paymentId) {
        Company company = accessControlService.getCurrentCompany(email);
        Payment payment = getPaymentIncludingDeleted(paymentId);
        if (!payment.isDeleted()) {
            return toResponse(payment);
        }
        if (payment.getInvoice() != null && payment.getInvoice().isDeleted()) {
            throw new BadRequestException("Restore the invoice first");
        }
        Map<String, Object> oldData = snapshot(payment);
        reapplyPayment(payment);
        payment.setDeleted(false);
        Payment saved = paymentRepository.save(payment);
        auditLogService.logCustomUpdate(email, company, "Payment", "Payment", paymentId, "PAYMENT_RESTORED", oldData, snapshot(saved));
        return toResponse(saved);
    }

    // Restore hi ek path hai jise deleted row chahiye; baaki sab ke liye deleted = not found.
    private Payment getPaymentIncludingDeleted(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    private Payment getPaymentOrThrow(Company company, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.isDeleted()) {
            throw new ResourceNotFoundException("Payment not found");
        }
        return payment;
    }

    private Invoice resolveInvoice(Company company, Long invoiceId, Customer customer) {
        if (invoiceId == null) {
            return null;
        }
        Invoice invoice = invoiceService.getInvoiceOrThrow(company, invoiceId);
        if (!invoice.getCustomer().getId().equals(customer.getId())) {
            throw new BadRequestException("Payment customer and invoice customer must match");
        }
        if (invoice.isDeleted()) {
            throw new BadRequestException("Cannot add payment to deleted invoice");
        }
        return invoice;
    }

    private void revertPayment(Payment payment) {
        BigDecimal amount = scale(payment.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        customerService.increaseBalance(payment.getCustomer(), amount);
        if (payment.getInvoice() != null) {
            invoiceService.reversePayment(payment.getInvoice(), amount);
        }
    }

    private void reapplyPayment(Payment payment) {
        BigDecimal amount = scale(payment.getAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (!payment.getCustomer().isActive()) {
            throw new BadRequestException("Cannot restore payment for inactive customer");
        }
        customerService.decreaseBalance(payment.getCustomer(), amount);
        if (payment.getInvoice() != null) {
            invoiceService.applyPayment(payment.getInvoice(), amount);
        }
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .customerId(payment.getCustomer().getId())
                .customerName(payment.getCustomer().getName())
                .customerMobile(payment.getCustomer().getMobile())
                .customerEmail(payment.getCustomer().getEmail())
                .invoiceId(payment.getInvoice() != null ? payment.getInvoice().getId() : null)
                .invoiceNo(payment.getInvoice() != null ? payment.getInvoice().getInvoiceNo() : null)
                .amount(scale(payment.getAmount()))
                .paymentDate(payment.getPaymentDate())
                .mode(payment.getMode())
                .remarks(payment.getRemarks())
                .referenceNo(payment.getReferenceNo())
                .source(payment.getSource() == null ? PaymentSource.MANUAL.name() : payment.getSource().name())
                .transactionRef(payment.getPaymentTransaction() != null ? payment.getPaymentTransaction().getTxnRef() : null)
                .gatewayType(payment.getPaymentTransaction() != null ? payment.getPaymentTransaction().getGatewayType().name() : null)
                .deleted(payment.isDeleted())
                .createdAt(payment.getCreatedAt())
                .createdBy(auditNameResolver.displayName(payment.getCreatedBy()))
                .build();
    }

    private Map<String, Object> snapshot(Payment payment) {
        return paymentRecordingService.snapshot(payment);
    }

    private Boolean resolveDeletedFilter(String recordStatus) {
        String value = blankToNull(recordStatus);
        if (value == null || "ACTIVE".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        if ("DELETED".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("ALL".equalsIgnoreCase(value)) {
            return null;
        }
        throw new BadRequestException("Invalid record status filter");
    }

    private boolean isUnsupportedPaymentStatus(String status) {
        String value = blankToNull(status);
        if (value == null || "SUCCESS".equalsIgnoreCase(value)) {
            return false;
        }
        return true;
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

    private void requirePaymentDate(PaymentRequest request) {
        if (request.getPaymentDate() == null) {
            throw new BadRequestException("Payment date is required");
        }
    }

    private BigDecimal requirePositiveAmount(BigDecimal value) {
        BigDecimal amount = scale(value);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Payment amount must be greater than 0");
        }
        return amount;
    }

    private PaymentRequest mapToPaymentRequest(Map<String, Object> param) {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setCustomerId(DataTypeUtility.getForeignKeyValue(param.get("customerId")));
        paymentRequest.setInvoiceId(DataTypeUtility.getForeignKeyValue(param.get("invoiceId")));
        paymentRequest.setAmount(DataTypeUtility.bigDecimalObjectValue(param.get("amount")));
        if (paymentRequest.getAmount() == null) {
            Double amountDouble = DataTypeUtility.doubleObjectValue(param.get("amount"));
            if (amountDouble != null) {
                paymentRequest.setAmount(BigDecimal.valueOf(amountDouble));
            }
        }
        String paymentDateStr = DataTypeUtility.stringValue(param.get("paymentDate"));
        LocalDate parsedPaymentDate = DataTypeUtility.parseLocalDate(paymentDateStr, "yyyy-MM-dd");
        if (parsedPaymentDate == null) {
            parsedPaymentDate = DataTypeUtility.parseLocalDate(paymentDateStr, "dd-MM-yyyy");
        }
        if (parsedPaymentDate == null) {
            parsedPaymentDate = DataTypeUtility.parseLocalDate(paymentDateStr, "yyyy/MM/dd");
        }
        paymentRequest.setPaymentDate(parsedPaymentDate);
        String mode = DataTypeUtility.stringValue(param.get("mode"));
        if (mode.length() == 0) {
            mode = null;
        }
        paymentRequest.setMode(mode);
        String remarks = DataTypeUtility.stringValue(param.get("remarks"));
        if (remarks.length() == 0) {
            remarks = null;
        }
        paymentRequest.setRemarks(remarks);
        String referenceNo = DataTypeUtility.stringValue(param.get("referenceNo"));
        if (referenceNo.length() == 0) {
            referenceNo = null;
        }
        paymentRequest.setReferenceNo(referenceNo);
        return paymentRequest;
    }
}
