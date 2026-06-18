package com.billing.service;

import com.billing.entity.enums.InvoiceStatus;
import com.billing.entity.enums.RoleName;
import com.billing.exception.ResourceNotFoundException;
import com.billing.dto.invoice.InvoiceItemRequest;
import com.billing.dto.invoice.InvoiceItemResponse;
import com.billing.dto.invoice.InvoiceRequest;
import com.billing.dto.invoice.InvoiceResponse;
import com.billing.dto.PageResponse;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.entity.Payment;
import com.billing.entity.Product;
import com.billing.entity.User;
import com.billing.exception.BadRequestException;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentRepository;
import com.billing.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final AccessControlService accessControlService;
    private final CustomerService customerService;
    private final AuditNameResolver auditNameResolver;
    private final InvoiceCalculationService invoiceCalculationService;
    private final AuditLogService auditLogService;
    private final PaymentModeMasterService paymentModeMasterService;

    @Transactional
    public InvoiceResponse create(String email, InvoiceRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Customer customer = customerService.getCustomerOrThrow(company, request.getCustomerId());
        if (!customer.isActive()) {
            throw new BadRequestException("Inactive customer cannot be invoiced");
        }
        BigDecimal initialPaidAmount = scale(request.getPaidAmount());
        String initialPaymentMode = blankToNull(request.getPaymentMode());

        Invoice invoice = Invoice.builder()
                .company(company)
                .customer(customer)
                .invoiceNo(generateInvoiceNumber(company, customer, request.getInvoiceDate()))
                .invoiceDate(request.getInvoiceDate())
                .paidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .build();

        request.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        applyInvoiceState(invoice, request, customer, true);
        invoiceRepository.save(invoice);
        if (initialPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
            createInitialPayment(email, company, customer, invoice, initialPaidAmount, initialPaymentMode, request.getInvoiceDate());
        }
        auditLogService.logCreate(email, company, "Invoice", "Invoice", invoice.getId(), snapshot(invoice));
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(String email, Long customerId) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        List<Invoice> invoices = customerId == null
                ? invoiceRepository.findByCompanyOrderByInvoiceDateDescIdDesc(company)
                : invoiceRepository.findByCompanyAndCustomerOrderByInvoiceDateDescIdDesc(
                        company,
                        customerService.getCustomerOrThrow(company, customerId)
                );

        return invoices.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> page(String email, Long customerId, int page, int size) {
        return page(email, customerId, null, null, null, null, null, null, null, null, null, null, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> page(String email,
                                              Long customerId,
                                              String search,
                                              String invoiceStatus,
                                              String paymentStatus,
                                              LocalDate startDate,
                                              LocalDate endDate,
                                              String outstandingFilter,
                                              BigDecimal minAmount,
                                              BigDecimal maxAmount,
                                              Long categoryId,
                                              RoleName createdByRole,
                                              int page,
                                              int size) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        int safeSize = Math.max(1, Math.min(size, 1000));
        PageRequest pageable = PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "invoiceDate").and(Sort.by(Sort.Direction.DESC, "id")));
        InvoiceStatus resolvedStatus = resolveFilterStatus(invoiceStatus, paymentStatus);
        if (isUnsupportedInvoiceStatus(invoiceStatus)) {
            return PageResponse.<InvoiceResponse>builder()
                    .records(List.of())
                    .page(Math.max(0, page))
                    .size(safeSize)
                    .totalRecords(0)
                    .totalPages(0)
                    .build();
        }
        Customer customer = company == null || customerId == null ? null : customerService.getCustomerOrThrow(company, customerId);
        Page<Invoice> invoices = invoiceRepository.searchInvoices(
                company,
                customer,
                customerId,
                blankToNull(search),
                resolvedStatus,
                startDate,
                endDate,
                normalizeOutstandingFilter(outstandingFilter),
                minAmount,
                maxAmount,
                categoryId,
                createdByRole,
                pageable
        );
        return PageResponse.from(invoices.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(String email, Long invoiceId) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        return toResponse(getInvoiceOrThrow(company, invoiceId));
    }

    @Transactional
    public InvoiceResponse update(String email, Long invoiceId, InvoiceRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        Invoice invoice = getInvoiceOrThrow(company, invoiceId);
        Map<String, Object> oldData = snapshot(invoice);
        Customer newCustomer = customerService.getCustomerOrThrow(company, request.getCustomerId());
        if (!newCustomer.isActive()) {
            throw new BadRequestException("Inactive customer cannot be invoiced");
        }

        restoreInvoiceEffects(invoice);
        invoice.setCustomer(newCustomer);
        invoice.setInvoiceDate(request.getInvoiceDate());
        applyInvoiceState(invoice, request, newCustomer, false);
        invoiceRepository.save(invoice);
        auditLogService.logUpdate(email, company, "Invoice", "Invoice", invoice.getId(), oldData, snapshot(invoice));
        return toResponse(invoice);
    }

    @Transactional
    public void delete(String email, Long invoiceId) {
        Company company = accessControlService.getCurrentCompany(email);
        Invoice invoice = getInvoiceOrThrow(company, invoiceId);
        Map<String, Object> oldData = snapshot(invoice);
        if (paymentRepository.existsByInvoice(invoice) || scale(invoice.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("Delete linked payments before deleting this invoice");
        }
        restoreInvoiceEffects(invoice);
        invoiceRepository.delete(invoice);
        auditLogService.logDelete(email, company, "Invoice", "Invoice", invoiceId, oldData);
    }

    public Invoice getInvoiceOrThrow(Company company, Long invoiceId) {
        return invoiceRepository.findByIdAndCompany(invoiceId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    public void applyPayment(Invoice invoice, BigDecimal amount) {
        BigDecimal nextPaid = scale(invoice.getPaidAmount().add(amount));
        if (nextPaid.compareTo(scale(invoice.getTotalAmount())) > 0) {
            throw new BadRequestException("Payment exceeds invoice balance");
        }
        invoice.setPaidAmount(nextPaid);
        invoice.setBalanceAmount(scale(invoice.getTotalAmount().subtract(nextPaid)));
        invoice.setPaymentStatus(resolveStatus(invoice.getBalanceAmount(), invoice.getPaidAmount()));
        invoiceRepository.save(invoice);
    }

    public void logPaymentApplied(String email, Company company, Invoice invoice, BigDecimal amount, BigDecimal oldOutstanding, BigDecimal newOutstanding, String paymentMode) {
        auditLogService.logEvent(email, company, "Invoice", "Invoice", invoice.getId(), "PAYMENT_ADDED", paymentAuditData(invoice, amount, oldOutstanding, newOutstanding, paymentMode));
    }

    public void logPaymentUpdated(String email, Company company, Invoice invoice, BigDecimal amount, BigDecimal oldOutstanding, BigDecimal newOutstanding, String paymentMode) {
        auditLogService.logEvent(email, company, "Invoice", "Invoice", invoice.getId(), "PAYMENT_UPDATED", paymentAuditData(invoice, amount, oldOutstanding, newOutstanding, paymentMode));
    }

    public void logPaymentDeleted(String email, Company company, Invoice invoice, BigDecimal amount, BigDecimal oldOutstanding, BigDecimal newOutstanding, String paymentMode) {
        auditLogService.logEvent(email, company, "Invoice", "Invoice", invoice.getId(), "PAYMENT_DELETED", paymentAuditData(invoice, amount, oldOutstanding, newOutstanding, paymentMode));
    }

    public void reversePayment(Invoice invoice, BigDecimal amount) {
        BigDecimal nextPaid = scale(invoice.getPaidAmount().subtract(amount));
        if (nextPaid.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Payment reversal would make paid amount negative");
        }
        invoice.setPaidAmount(nextPaid);
        invoice.setBalanceAmount(scale(invoice.getTotalAmount().subtract(nextPaid)));
        invoice.setPaymentStatus(resolveStatus(invoice.getBalanceAmount(), invoice.getPaidAmount()));
        invoiceRepository.save(invoice);
    }

    private void createInitialPayment(String email, Company company, Customer customer, Invoice invoice, BigDecimal amount, String mode, LocalDate paymentDate) {
        if (mode == null) {
            throw new BadRequestException("Select payment mode when paid amount is greater than 0");
        }
        if (amount.compareTo(scale(invoice.getBalanceAmount())) > 0) {
            throw new BadRequestException("Paid amount cannot exceed invoice outstanding amount");
        }
        paymentModeMasterService.ensureDefaults(company);
        String modeCode = paymentModeMasterService.requireActiveModeCode(company, mode);
        BigDecimal oldOutstanding = scale(invoice.getBalanceAmount());

        customerService.decreaseBalance(customer, amount);
        applyPayment(invoice, amount);

        Payment payment = Payment.builder()
                .company(company)
                .customer(customer)
                .invoice(invoice)
                .amount(amount)
                .paymentDate(paymentDate)
                .mode(modeCode)
                .remarks("Initial invoice payment")
                .build();
        Payment saved = paymentRepository.save(payment);
        auditLogService.logCreate(email, company, "Payment", "Payment", saved.getId(), paymentSnapshot(saved));
        logPaymentApplied(email, company, invoice, amount, oldOutstanding, invoice.getBalanceAmount(), modeCode);
    }

    private void applyInvoiceState(Invoice invoice, InvoiceRequest request, Customer customer, boolean creating) {
        List<InvoiceCalculationService.CalculationLineInput> calculationLines = new ArrayList<>();

        for (InvoiceItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findByIdAndCompany(itemRequest.getProductId(), invoice.getCompany())
                    .orElseThrow(() -> new BadRequestException("Product not found for id " + itemRequest.getProductId()));

            if (!product.isActive()) {
                throw new BadRequestException("Inactive product cannot be invoiced: " + product.getName());
            }
            if (product.getStockQty() < itemRequest.getQty()) {
                throw new BadRequestException("Insufficient stock for product " + product.getName());
            }

            product.setStockQty(product.getStockQty() - itemRequest.getQty());
            productRepository.save(product);

            calculationLines.add(new InvoiceCalculationService.CalculationLineInput(
                    product,
                    itemRequest.getQty(),
                    itemRequest.getPrice(),
                    itemRequest.getTaxPercent(),
                    scale(itemRequest.getDiscountPercent()),
                    itemRequest.getDiscountType(),
                    itemRequest.getDiscountValue()
            ));
        }

        BigDecimal paidAmount = request.getPaidAmount() != null
                ? scale(request.getPaidAmount())
                : creating ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : scale(invoice.getPaidAmount());
        InvoiceCalculationService.CalculationResult calculation = invoiceCalculationService.calculate(
                calculationLines,
                request.getDiscountAmount(),
                paidAmount
        );

        List<InvoiceItem> items = new ArrayList<>();
        for (InvoiceCalculationService.CalculationLine line : calculation.lines()) {
            items.add(InvoiceItem.builder()
                    .company(invoice.getCompany())
                    .invoice(invoice)
                    .product(line.product())
                    .qty(line.qty())
                    .price(line.unitPrice())
                    .discountPercent(line.discountPercent())
                    .taxPercent(line.taxPercent())
                    .lineTotal(line.grandTotal())
                    .build());
        }

        BigDecimal previousBalance = creating ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : scale(invoice.getBalanceAmount());

        invoice.getItems().clear();
        invoice.getItems().addAll(items);
        invoice.setSubtotal(scale(calculation.subtotal()));
        invoice.setTaxAmount(scale(calculation.taxAmount()));
        invoice.setDiscountAmount(scale(calculation.totalDiscount()));
        invoice.setTotalAmount(scale(calculation.grandTotal()));
        invoice.setPaidAmount(scale(calculation.paidAmount()));
        invoice.setBalanceAmount(scale(calculation.outstandingAmount()));
        invoice.setPaymentStatus(resolveStatus(invoice.getBalanceAmount(), invoice.getPaidAmount()));

        customerService.increaseBalance(customer, invoice.getBalanceAmount().subtract(previousBalance));
    }

    private void restoreInvoiceEffects(Invoice invoice) {
        Customer oldCustomer = invoice.getCustomer();
        customerService.decreaseBalance(oldCustomer, scale(invoice.getBalanceAmount()));

        for (InvoiceItem item : invoice.getItems()) {
            Product product = item.getProduct();
            product.setStockQty(product.getStockQty() + item.getQty());
            productRepository.save(product);
        }

        invoice.getItems().clear();
        invoice.setSubtotal(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setTaxAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setBalanceAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setPaymentStatus(InvoiceStatus.UNPAID);
    }

    private InvoiceStatus resolveStatus(BigDecimal balanceAmount, BigDecimal paidAmount) {
        if (balanceAmount.compareTo(BigDecimal.ZERO) == 0) {
            return InvoiceStatus.PAID;
        }
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            return InvoiceStatus.PARTIAL;
        }
        return InvoiceStatus.UNPAID;
    }

    private InvoiceStatus resolveFilterStatus(String invoiceStatus, String paymentStatus) {
        String invoiceValue = blankToNull(invoiceStatus);
        if (invoiceValue != null) {
            return switch (invoiceValue.trim().toUpperCase()) {
                case "PENDING", "UNPAID" -> InvoiceStatus.UNPAID;
                case "PARTIAL", "PARTIAL_PAID" -> InvoiceStatus.PARTIAL;
                case "PAID", "FULLY_PAID" -> InvoiceStatus.PAID;
                default -> null;
            };
        }
        String paymentValue = blankToNull(paymentStatus);
        if (paymentValue == null) {
            return null;
        }
        return switch (paymentValue.trim().toUpperCase()) {
            case "UNPAID" -> InvoiceStatus.UNPAID;
            case "PARTIAL" -> InvoiceStatus.PARTIAL;
            case "PAID", "FULLY_PAID" -> InvoiceStatus.PAID;
            default -> null;
        };
    }

    private boolean isUnsupportedInvoiceStatus(String invoiceStatus) {
        String value = blankToNull(invoiceStatus);
        if (value == null) {
            return false;
        }
        return switch (value.trim().toUpperCase()) {
            case "DRAFT", "CANCELLED" -> true;
            default -> false;
        };
    }

    private String normalizeOutstandingFilter(String outstandingFilter) {
        String value = blankToNull(outstandingFilter);
        return value == null ? null : value.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String generateInvoiceNumber(Company company, Customer customer, java.time.LocalDate invoiceDate) {
        long nextSequence = invoiceRepository.countByCompanyAndInvoiceDate(company, invoiceDate) + 1L;
        String customerRef = buildCustomerReference(customer.getName());
        String mobileSuffix = buildMobileSuffix(customer.getMobile());
        String dateSegment = invoiceDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        return String.format("INV-%s-%s-%s-%03d", customerRef, mobileSuffix, dateSegment, nextSequence);
    }

    private InvoiceResponse toResponse(Invoice invoice) {
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

    private String buildCustomerReference(String customerName) {
        String sanitized = customerName == null ? "" : customerName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (sanitized.isEmpty()) {
            return "CUST";
        }
        return sanitized.length() > 6 ? sanitized.substring(0, 6) : sanitized;
    }

    private String buildMobileSuffix(String mobile) {
        String digits = mobile == null ? "" : mobile.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            return digits.substring(digits.length() - 4);
        }
        return String.format("%04d", digits.isEmpty() ? 0 : Integer.parseInt(digits));
    }

    private Map<String, Object> snapshot(Invoice invoice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceNo", invoice.getInvoiceNo());
        data.put("customerId", invoice.getCustomer().getId());
        data.put("customerName", invoice.getCustomer().getName());
        data.put("invoiceDate", invoice.getInvoiceDate());
        data.put("subtotal", scale(invoice.getSubtotal()));
        data.put("taxAmount", scale(invoice.getTaxAmount()));
        data.put("discountAmount", scale(invoice.getDiscountAmount()));
        data.put("totalAmount", scale(invoice.getTotalAmount()));
        data.put("paidAmount", scale(invoice.getPaidAmount()));
        data.put("balanceAmount", scale(invoice.getBalanceAmount()));
        data.put("paymentStatus", invoice.getPaymentStatus() != null ? invoice.getPaymentStatus().name() : null);
        data.put("items", invoice.getItems().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", item.getProduct().getId());
            row.put("productName", item.getProduct().getName());
            row.put("qty", item.getQty());
            row.put("price", scale(item.getPrice()));
            row.put("discountPercent", scale(item.getDiscountPercent()));
            row.put("taxPercent", scale(item.getTaxPercent()));
            row.put("lineTotal", scale(item.getLineTotal()));
            return row;
        }).toList());
        return data;
    }

    private Map<String, Object> paymentSnapshot(Payment payment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerId", payment.getCustomer().getId());
        data.put("customerName", payment.getCustomer().getName());
        data.put("invoiceId", payment.getInvoice() != null ? payment.getInvoice().getId() : null);
        data.put("invoiceNo", payment.getInvoice() != null ? payment.getInvoice().getInvoiceNo() : null);
        data.put("amount", scale(payment.getAmount()));
        data.put("paymentDate", payment.getPaymentDate());
        data.put("mode", payment.getMode());
        data.put("remarks", payment.getRemarks());
        return data;
    }

    private Map<String, Object> paymentAuditData(Invoice invoice, BigDecimal amount, BigDecimal oldOutstanding, BigDecimal newOutstanding, String paymentMode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceNo", invoice.getInvoiceNo());
        data.put("customerName", invoice.getCustomer().getName());
        data.put("paymentAmount", scale(amount));
        data.put("paymentMode", paymentMode);
        data.put("oldOutstanding", scale(oldOutstanding));
        data.put("newOutstanding", scale(newOutstanding));
        data.put("outstandingChange", scale(newOutstanding).subtract(scale(oldOutstanding)).setScale(2, RoundingMode.HALF_UP));
        data.put("paidAmount", scale(invoice.getPaidAmount()));
        data.put("balanceAmount", scale(invoice.getBalanceAmount()));
        return data;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
