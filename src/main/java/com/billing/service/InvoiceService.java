package com.billing.service;

import com.billing.dto.PageResponse;
import com.billing.dto.invoice.InvoiceItemRequest;
import com.billing.dto.invoice.InvoiceItemResponse;
import com.billing.dto.invoice.InvoiceRequest;
import com.billing.dto.invoice.InvoiceResponse;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.entity.Payment;
import com.billing.entity.Product;
import com.billing.entity.TaxMaster;
import com.billing.entity.User;
import com.billing.entity.enums.DocumentSequenceType;
import com.billing.entity.enums.InvoiceStatus;
import com.billing.entity.enums.RoleName;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.InventoryLedgerRepository;
import com.billing.repository.InvoiceItemAllocationRepository;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentRepository;
import com.billing.repository.ProductRepository;
import com.billing.repository.UserRepository;
import com.billing.util.DataTypeUtility;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final AccessControlService accessControlService;
    private final CustomerService customerService;
    private final AuditNameResolver auditNameResolver;
    private final TaxCalculationService taxCalculationService;
    private final TaxMasterService taxMasterService;
    private final AuditLogService auditLogService;
    private final PaymentModeMasterService paymentModeMasterService;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final DocumentNumberService documentNumberService;
    private final InvoiceItemAllocationRepository invoiceItemAllocationRepository;
    private final InventoryLedgerRepository inventoryLedgerRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // Lazy: PaymentRecordingService khud InvoiceService (applyPayment) use karta hai.
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.billing.service.payment.PaymentRecordingService paymentRecordingService;

    @Transactional
    public InvoiceResponse create(Map<String, Object> param, String email) {
        InvoiceRequest invoiceRequest = mapToRequest(param);
        return create(email, invoiceRequest);
    }

    @Transactional
    public InvoiceResponse create(String email, InvoiceRequest request) {
        validateInvoiceRequest(request);
        Company company = accessControlService.getCurrentCompany(email);
        // POST = WRITE pool, isliye lazy seed yahi safe hai. Naye tenant me
        // payment_modes empty hoti hai; paid=0 wale invoice par bhi defaults
        // ban jayein taaki dropdown agli GET par bhara mile (GET read pool
        // par seed karna "Connection is read-only" deta hai — live bug tha).
        paymentModeMasterService.ensureDefaults(company);
        Customer customer = customerService.getCustomerOrThrow(company, request.getCustomerId());
        if (!customer.isActive()) {
            throw new BadRequestException("Inactive customer cannot be invoiced");
        }
        BigDecimal initialPaidAmount = scale(request.getPaidAmount());
        String initialPaymentMode = blankToNull(request.getPaymentMode());

        Invoice invoice = Invoice.builder()
                .company(company)
                .customer(customer)
                .referByUser(resolveReferByUser(company, request.getReferByUserId()))
                .invoiceNo(documentNumberService.next(DocumentSequenceType.INVOICE, request.getInvoiceDate()))
                .invoiceDate(request.getInvoiceDate())
                .subtotal(zero())
                .taxableAmount(zero())
                .cgstTotal(zero())
                .sgstTotal(zero())
                .igstTotal(zero())
                .taxAmount(zero())
                .discountAmount(zero())
                .roundOff(zero())
                .grandTotal(zero())
                .totalAmount(zero())
                .paidAmount(zero())
                .balanceAmount(zero())
                .paymentStatus(InvoiceStatus.UNPAID)
                .build();

        request.setPaidAmount(zero());
        applyInvoiceState(invoice, request, customer, true);
        invoiceRepository.saveAndFlush(invoice);
        inventoryService.allocateForInvoice(invoice);
        if (initialPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
            createInitialPayment(email, company, customer, invoice, initialPaidAmount, initialPaymentMode, request.getInvoiceDate(), request.getReferenceNo());
        }
        auditLogService.logCreate(email, company, "Invoice", "Invoice", invoice.getId(), snapshot(invoice));
        if (invoice.getReferByUser() != null) {
            auditLogService.logEvent(email, company, "Invoice", "Invoice", invoice.getId(), "INVOICE_CREATED_WITH_REFER_BY", referBySnapshot(invoice));
        }
        return toResponse(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list(String email, Long customerId) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        List<Invoice> invoices = customerId == null
                ? invoiceRepository.findAllByOrderByInvoiceDateDescIdDesc()
                : invoiceRepository.findByCompanyAndCustomerOrderByInvoiceDateDescIdDesc(
                company,
                customerService.getCustomerOrThrow(company, customerId)
        );

        return invoices.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> page(String email, Long customerId, int page, int size) {
        return page(email, customerId, null, null, null, null, null, null, null, null, null, null, "ACTIVE", page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> page(Map<String, Object> param, String email) {
        Long customerId = DataTypeUtility.getForeignKeyValue(param.get("customerId"));
        String search = DataTypeUtility.stringValue(param.get("search"));
        if (search.length() == 0) {
            search = null;
        }
        String invoiceStatus = DataTypeUtility.stringValue(param.get("invoiceStatus"));
        if (invoiceStatus.length() == 0) {
            invoiceStatus = null;
        }
        String paymentStatus = DataTypeUtility.stringValue(param.get("paymentStatus"));
        if (paymentStatus.length() == 0) {
            paymentStatus = null;
        }
        LocalDate startDate = DataTypeUtility.parseFlexibleDate(param, "startDate");
        LocalDate endDate = DataTypeUtility.parseFlexibleDate(param, "endDate");
        String outstandingFilter = DataTypeUtility.stringValue(param.get("outstandingFilter"));
        if (outstandingFilter.length() == 0) {
            outstandingFilter = null;
        }
        BigDecimal minAmount = DataTypeUtility.bigDecimalObjectValue(param.get("minAmount"));
        BigDecimal maxAmount = DataTypeUtility.bigDecimalObjectValue(param.get("maxAmount"));
        Long categoryId = DataTypeUtility.getForeignKeyValue(param.get("categoryId"));
        RoleName createdByRole = null;
        String createdByRoleStr = DataTypeUtility.stringValue(param.get("createdByRole"));
        if (createdByRoleStr.length() > 0) {
            try {
                createdByRole = RoleName.valueOf(createdByRoleStr);
            } catch (Exception ignore) {
            }
        }
        String recordStatus = DataTypeUtility.stringValue(param.get("recordStatus"));
        if (recordStatus.length() == 0) {
            recordStatus = "ACTIVE";
        }
        int page = DataTypeUtility.integerValue(param.get("page"));
        int size = DataTypeUtility.integerValue(param.get("size"));
        if (size == 0) {
            size = 20;
        }
        return page(email, customerId, search, invoiceStatus, paymentStatus, startDate, endDate, outstandingFilter, minAmount, maxAmount, categoryId, createdByRole, recordStatus, page, size);
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
                                              String recordStatus,
                                              int page,
                                              int size) {
        User user = accessControlService.getCurrentUser(email);
        Company company = accessControlService.requireCompany(user);
        int safeSize = Math.max(1, Math.min(size, 1000));
        PageRequest pageable = PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "invoiceDate").and(Sort.by(Sort.Direction.DESC, "id")));
        InvoiceStatus resolvedStatus = resolveFilterStatus(invoiceStatus, paymentStatus);
        if (isUnsupportedInvoiceStatus(invoiceStatus)) {
            return PageResponse.<InvoiceResponse>builder().records(List.of()).page(Math.max(0, page)).size(safeSize).totalRecords(0).totalPages(0).build();
        }
        Customer customer = company == null || customerId == null ? null : customerService.getCustomerOrThrow(company, customerId);
        Page<Invoice> invoices = invoiceRepository.searchInvoices(
                company,
                customer,
                customerId,
                resolveDeletedFilter(recordStatus),
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
    public InvoiceResponse update(Map<String, Object> param, Long invoiceId, String email) {
        InvoiceRequest invoiceRequest = mapToRequest(param);
        return update(email, invoiceId, invoiceRequest);
    }

    @Transactional
    public InvoiceResponse update(String email, Long invoiceId, InvoiceRequest request) {
        validateInvoiceRequest(request);
        Company company = accessControlService.getCurrentCompany(email);
        Invoice invoice = getInvoiceOrThrow(company, invoiceId);
        if (invoice.isDeleted()) {
            throw new BadRequestException("Restore invoice before editing it");
        }
        // Payments purane customer ke balance se jude hain; customer badalne se ledger bigadta tha.
        if (scale(invoice.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0
                && !invoice.getCustomer().getId().equals(request.getCustomerId())) {
            throw new BadRequestException("Customer cannot be changed on an invoice that has payments. Delete its payments first.");
        }
        Map<String, Object> oldData = snapshot(invoice);
        Long oldReferByUserId = invoice.getReferByUser() != null ? invoice.getReferByUser().getId() : null;
        Customer newCustomer = customerService.getCustomerOrThrow(company, request.getCustomerId());
        if (!newCustomer.isActive()) {
            throw new BadRequestException("Inactive customer cannot be invoiced");
        }

        restoreInvoiceEffects(invoice);
        invoice.setCustomer(newCustomer);
        invoice.setReferByUser(resolveReferByUser(company, request.getReferByUserId()));
        invoice.setInvoiceDate(request.getInvoiceDate());
        applyInvoiceState(invoice, request, newCustomer, false);
        invoiceRepository.saveAndFlush(invoice);
        inventoryService.allocateForInvoice(invoice);
        auditLogService.logUpdate(email, company, "Invoice", "Invoice", invoice.getId(), oldData, snapshot(invoice));
        Long newReferByUserId = invoice.getReferByUser() != null ? invoice.getReferByUser().getId() : null;
        if (!java.util.Objects.equals(oldReferByUserId, newReferByUserId)) {
            Map<String, Object> referralChange = new LinkedHashMap<>();
            referralChange.put("oldReferByUserId", oldReferByUserId);
            referralChange.put("newReferByUserId", newReferByUserId);
            referralChange.putAll(referBySnapshot(invoice));
            auditLogService.logEvent(email, company, "Invoice", "Invoice", invoice.getId(), "REFER_BY_UPDATED", referralChange);
        }
        return toResponse(invoice);
    }

    @Transactional
    public void delete(String email, Long invoiceId) {
        Company company = accessControlService.getCurrentCompany(email);
        Invoice invoice = getInvoiceOrThrow(company, invoiceId);
        if (invoice.isDeleted()) {
            return;
        }
        Map<String, Object> oldData = snapshot(invoice);
        if (paymentRepository.existsByInvoiceAndDeletedFalse(invoice) || scale(invoice.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("Delete linked payments before deleting this invoice");
        }
        removeInvoiceBusinessEffects(invoice);
        invoice.setDeleted(true);
        invoiceRepository.save(invoice);
        auditLogService.logCustomUpdate(email, company, "Invoice", "Invoice", invoiceId, "INVOICE_DELETED", oldData, snapshot(invoice));
    }

    @Transactional
    public InvoiceResponse restore(String email, Long invoiceId) {
        Company company = accessControlService.getCurrentCompany(email);
        Invoice invoice = getInvoiceIncludingDeleted(invoiceId);
        if (!invoice.isDeleted()) {
            return toResponse(invoice);
        }
        Map<String, Object> oldData = snapshot(invoice);
        reapplyInvoiceBusinessEffects(invoice);
        invoice.setDeleted(false);
        Invoice saved = invoiceRepository.save(invoice);
        auditLogService.logCustomUpdate(email, company, "Invoice", "Invoice", invoiceId, "INVOICE_RESTORED", oldData, snapshot(saved));
        return toResponse(saved);
    }

    // Restore hi ek path hai jise deleted row chahiye; baaki sab ke liye deleted = not found.
    private Invoice getInvoiceIncludingDeleted(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    public Invoice getInvoiceOrThrow(Company company, Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        // Soft-deleted rows must behave as not-found so enumeration of
        // deleted IDs does not leak existence/timing info.
        if (invoice.isDeleted()) {
            throw new ResourceNotFoundException("Invoice not found");
        }
        return invoice;
    }

    public void applyPayment(Invoice invoice, BigDecimal amount) {
        lockForPaymentChange(invoice);
        if (invoice.isDeleted()) {
            throw new BadRequestException("Cannot apply payment to deleted invoice");
        }
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

    /** email null = system actor (gateway webhook / reconciliation), actorName se audit. */
    public void logPaymentApplied(String email, String actorName, Company company, Invoice invoice, BigDecimal amount, BigDecimal oldOutstanding, BigDecimal newOutstanding, String paymentMode) {
        if (email == null) {
            auditLogService.logEventAsActor(actorName, company, "Invoice", "Invoice", invoice.getId(), "PAYMENT_ADDED", paymentAuditData(invoice, amount, oldOutstanding, newOutstanding, paymentMode));
            return;
        }
        logPaymentApplied(email, company, invoice, amount, oldOutstanding, newOutstanding, paymentMode);
    }

    public void logPaymentUpdated(String email, Company company, Invoice invoice, BigDecimal amount, BigDecimal oldOutstanding, BigDecimal newOutstanding, String paymentMode) {
        auditLogService.logEvent(email, company, "Invoice", "Invoice", invoice.getId(), "PAYMENT_UPDATED", paymentAuditData(invoice, amount, oldOutstanding, newOutstanding, paymentMode));
    }

    public void logPaymentDeleted(String email, Company company, Invoice invoice, BigDecimal amount, BigDecimal oldOutstanding, BigDecimal newOutstanding, String paymentMode) {
        auditLogService.logEvent(email, company, "Invoice", "Invoice", invoice.getId(), "PAYMENT_DELETED", paymentAuditData(invoice, amount, oldOutstanding, newOutstanding, paymentMode));
    }

    public void reversePayment(Invoice invoice, BigDecimal amount) {
        lockForPaymentChange(invoice);
        if (invoice.isDeleted()) {
            throw new BadRequestException("Cannot reverse payment on deleted invoice");
        }
        BigDecimal nextPaid = scale(invoice.getPaidAmount().subtract(amount));
        if (nextPaid.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Payment reversal would make paid amount negative");
        }
        invoice.setPaidAmount(nextPaid);
        invoice.setBalanceAmount(scale(invoice.getTotalAmount().subtract(nextPaid)));
        invoice.setPaymentStatus(resolveStatus(invoice.getBalanceAmount(), invoice.getPaidAmount()));
        invoiceRepository.save(invoice);
    }

    /**
     * Do payments ek saath ek hi invoice par aayein to dono purana paidAmount
     * padh kar ek ka update kho deta tha. Ab row lock (SELECT ... FOR UPDATE)
     * leke DB se taaza value padhte hain; doosri transaction commit tak rukti hai.
     * Pehle flush: isi transaction ke pending changes (jaise payment edit me
     * pehle reverse) refresh me kho na jayein.
     */
    private void lockForPaymentChange(Invoice invoice) {
        if (invoice.getId() == null) {
            return;
        }
        entityManager.flush();
        entityManager.refresh(invoice, LockModeType.PESSIMISTIC_WRITE);
    }

    private void createInitialPayment(String email, Company company, Customer customer, Invoice invoice, BigDecimal amount, String mode, LocalDate paymentDate, String referenceNo) {
        if (mode == null) {
            throw new BadRequestException("Select payment mode when paid amount is greater than 0");
        }
        if (amount.compareTo(scale(invoice.getBalanceAmount())) > 0) {
            throw new BadRequestException("Paid amount cannot exceed invoice outstanding amount");
        }
        paymentModeMasterService.ensureDefaults(company);
        // Online mode yahan nahi: invoice ke baad gateway (Collect Online) se aata hai.
        String modeCode = paymentModeMasterService.requireActiveOfflineModeCode(company, mode);
        paymentRecordingService.record(new com.billing.service.payment.PaymentEntry(
                company, email, null, customer, invoice, amount, paymentDate, modeCode,
                "Initial invoice payment", referenceNo, com.billing.entity.enums.PaymentSource.MANUAL, null));
    }

    private void applyInvoiceState(Invoice invoice, InvoiceRequest request, Customer customer, boolean creating) {
        List<TaxCalculationService.TaxDocumentLineInput> calculationLines = new ArrayList<>();

        for (InvoiceItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findByIdAndCompany(itemRequest.getProductId(), invoice.getCompany())
                    .orElseThrow(() -> new BadRequestException("Product not found for id " + itemRequest.getProductId()));

            if (!product.isActive()) {
                throw new BadRequestException("Inactive product cannot be invoiced: " + product.getName());
            }
            inventoryService.assertSufficientStock(invoice.getCompany(), product, itemRequest.getQty());

            TaxMaster taxMaster = taxMasterService.resolveForProduct(invoice.getCompany(), product.getTaxMaster() != null ? product.getTaxMaster().getId() : null, BigDecimal.ZERO, product.isTaxable());
            calculationLines.add(new TaxCalculationService.TaxDocumentLineInput(
                    product,
                    taxMaster,
                    itemRequest.getQty(),
                    itemRequest.getPrice(),
                    scale(itemRequest.getDiscountPercent()),
                    itemRequest.getDiscountType(),
                    itemRequest.getDiscountValue()
            ));
        }

        // Edit par paidAmount request se kabhi nahi: wo Payment rows se aata hai
        // (pehle body ka 0 bhejne se invoice UNPAID ho jata tha, payments reh jaate the).
        BigDecimal paidAmount = creating ? scale(request.getPaidAmount()) : scale(invoice.getPaidAmount());
        TaxCalculationService.TaxDocumentCalculation calculation = taxCalculationService.calculate(
                invoice.getCompany(),
                customer,
                calculationLines,
                request.getDiscountAmount(),
                paidAmount
        );

        List<InvoiceItem> items = new ArrayList<>();
        for (TaxCalculationService.TaxDocumentLine line : calculation.lines()) {
            items.add(InvoiceItem.builder()
                    .company(invoice.getCompany())
                    .invoice(invoice)
                    .product(line.product())
                    .taxMaster(line.taxMaster())
                    .qty(line.qty())
                    .price(line.unitPrice())
                    .discountPercent(line.lineTotal().compareTo(BigDecimal.ZERO) > 0
                            ? scale(line.productDiscount().multiply(BigDecimal.valueOf(100)).divide(line.lineTotal(), 2, RoundingMode.HALF_UP))
                            : zero())
                    .discountAmount(scale(line.productDiscount().add(line.invoiceDiscountShare())))
                    .taxPercent(scale(line.taxRate()))
                    .taxName(line.taxMaster() != null ? line.taxMaster().getTaxName() : null)
                    .taxRate(scale(line.taxRate()))
                    .hsnCode(line.product().getHsnCode())
                    .taxableAmount(scale(line.taxableAmount()))
                    .cgstRate(scale(line.cgstRate()))
                    .cgstAmount(scale(line.cgstAmount()))
                    .sgstRate(scale(line.sgstRate()))
                    .sgstAmount(scale(line.sgstAmount()))
                    .igstRate(scale(line.igstRate()))
                    .igstAmount(scale(line.igstAmount()))
                    .netAmount(scale(line.taxableAmount().add(line.cgstAmount()).add(line.sgstAmount()).add(line.igstAmount())))
                    .grandAmount(scale(line.grandTotal()))
                    .lineTotal(scale(line.grandTotal()))
                    .build());
        }

        BigDecimal previousBalance = creating ? zero() : scale(invoice.getBalanceAmount());

        invoice.setPlaceOfSupplyStateId(customer.getStateMaster() != null ? customer.getStateMaster().getId() : null);
        invoice.setPlaceOfSupplyState(customer.getStateMaster() != null ? customer.getStateMaster().getStateName() : customer.getState());
        invoice.setCustomerGstin(customer.getGstin() != null && !customer.getGstin().isBlank() ? customer.getGstin() : customer.getGstNo());

        invoice.getItems().clear();
        invoice.getItems().addAll(items);
        invoice.setSubtotal(scale(calculation.subtotal()));
        invoice.setTaxableAmount(scale(calculation.taxableAmount()));
        invoice.setCgstTotal(scale(calculation.cgstTotal()));
        invoice.setSgstTotal(scale(calculation.sgstTotal()));
        invoice.setIgstTotal(scale(calculation.igstTotal()));
        invoice.setTaxAmount(scale(calculation.totalTaxAmount()));
        invoice.setDiscountAmount(scale(calculation.discountAmount()));
        invoice.setRoundOff(scale(calculation.roundOff()));
        invoice.setGrandTotal(scale(calculation.grandTotal()));
        invoice.setTotalAmount(scale(calculation.grandTotal()));
        invoice.setPaidAmount(scale(calculation.paidAmount()));
        invoice.setBalanceAmount(scale(calculation.outstandingAmount()));
        invoice.setPaymentStatus(resolveStatus(invoice.getBalanceAmount(), invoice.getPaidAmount()));

        customerService.increaseBalance(customer, invoice.getBalanceAmount().subtract(previousBalance));
    }

    private void restoreInvoiceEffects(Invoice invoice) {
        Customer oldCustomer = invoice.getCustomer();
        customerService.decreaseBalance(oldCustomer, scale(invoice.getBalanceAmount()));
        inventoryService.releaseInvoiceAllocations(invoice, "Invoice allocation released for edit");
        // Purane item rows orphanRemoval se delete honge; unpe released allocations
        // aur ledger history ka FK tha (edit hamesha fail hota tha). Link hatao,
        // history invoice_id se judi rehti hai.
        List<InvoiceItem> oldItems = new ArrayList<>(invoice.getItems());
        if (!oldItems.isEmpty()) {
            invoiceItemAllocationRepository.detachReleasedFromItems(oldItems);
            inventoryLedgerRepository.detachFromItems(oldItems);
        }

        invoice.getItems().clear();
        invoice.setSubtotal(zero());
        invoice.setTaxableAmount(zero());
        invoice.setCgstTotal(zero());
        invoice.setSgstTotal(zero());
        invoice.setIgstTotal(zero());
        invoice.setTaxAmount(zero());
        invoice.setDiscountAmount(zero());
        invoice.setRoundOff(zero());
        invoice.setGrandTotal(zero());
        invoice.setTotalAmount(zero());
        invoice.setBalanceAmount(zero());
        invoice.setPaymentStatus(InvoiceStatus.UNPAID);
    }

    private void removeInvoiceBusinessEffects(Invoice invoice) {
        customerService.decreaseBalance(invoice.getCustomer(), scale(invoice.getBalanceAmount()));
        inventoryService.releaseInvoiceAllocations(invoice, "Invoice allocation released for delete");
    }

    private void reapplyInvoiceBusinessEffects(Invoice invoice) {
        if (!invoice.getCustomer().isActive()) {
            throw new BadRequestException("Cannot restore invoice for inactive customer");
        }
        for (InvoiceItem item : invoice.getItems()) {
            Product product = item.getProduct();
            if (!product.isActive()) {
                throw new BadRequestException("Cannot restore invoice with inactive product: " + product.getName());
            }
            inventoryService.assertSufficientStock(invoice.getCompany(), product, item.getQty());
        }
        inventoryService.allocateForInvoice(invoice);
        customerService.increaseBalance(invoice.getCustomer(), scale(invoice.getBalanceAmount()));
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

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNo(invoice.getInvoiceNo())
                .customerId(invoice.getCustomer().getId())
                .customerName(invoice.getCustomer().getName())
                .customerMobile(invoice.getCustomer().getMobile())
                .customerEmail(invoice.getCustomer().getEmail())
                .customerAddress(invoice.getCustomer().getAddress())
                .customerState(invoice.getCustomer().getState())
                .customerStateId(invoice.getCustomer().getStateMaster() != null ? invoice.getCustomer().getStateMaster().getId() : null)
                .customerGstin(invoice.getCustomer().getGstin() != null ? invoice.getCustomer().getGstin() : invoice.getCustomer().getGstNo())
                .referByUserId(invoice.getReferByUser() != null ? invoice.getReferByUser().getId() : null)
                .referByUserName(invoice.getReferByUser() != null ? invoice.getReferByUser().getFullName() : null)
                .referByUsername(invoice.getReferByUser() != null ? invoice.getReferByUser().getUsername() : null)
                .subtotal(scale(invoice.getSubtotal()))
                .taxableAmount(scale(invoice.getTaxableAmount()))
                .cgstTotal(scale(invoice.getCgstTotal()))
                .sgstTotal(scale(invoice.getSgstTotal()))
                .igstTotal(scale(invoice.getIgstTotal()))
                .taxAmount(scale(invoice.getTaxAmount()))
                .discountAmount(scale(invoice.getDiscountAmount()))
                .roundOff(scale(invoice.getRoundOff()))
                .grandTotal(scale(invoice.getGrandTotal()))
                .totalAmount(scale(invoice.getTotalAmount()))
                .paidAmount(scale(invoice.getPaidAmount()))
                .balanceAmount(scale(invoice.getBalanceAmount()))
                .paymentStatus(invoice.getPaymentStatus().name())
                .invoiceDate(invoice.getInvoiceDate())
                .deleted(invoice.isDeleted())
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
                        .discountAmount(scale(item.getDiscountAmount()))
                        .taxMasterId(item.getTaxMaster() != null ? item.getTaxMaster().getId() : null)
                        .taxName(item.getTaxName())
                        .taxRate(scale(item.getTaxRate()))
                        .hsnCode(item.getHsnCode())
                        .taxableAmount(scale(item.getTaxableAmount()))
                        .cgstRate(scale(item.getCgstRate()))
                        .cgstAmount(scale(item.getCgstAmount()))
                        .sgstRate(scale(item.getSgstRate()))
                        .sgstAmount(scale(item.getSgstAmount()))
                        .igstRate(scale(item.getIgstRate()))
                        .igstAmount(scale(item.getIgstAmount()))
                        .taxPercent(scale(item.getTaxPercent()))
                        .netAmount(scale(item.getNetAmount()))
                        .grandAmount(scale(item.getGrandAmount()))
                        .lineTotal(scale(item.getLineTotal()))
                        .build()).toList())
                .build();
    }

    // Map endpoints par @Valid nahi chalta: null/negative values pehle NPE/500 ya galat data dete the.
    private void validateInvoiceRequest(InvoiceRequest request) {
        if (request.getCustomerId() == null) {
            throw new BadRequestException("Customer is required");
        }
        if (request.getInvoiceDate() == null) {
            throw new BadRequestException("Invoice date is required");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BadRequestException("Add at least one item to the invoice");
        }
        for (InvoiceItemRequest item : request.getItems()) {
            if (item.getProductId() == null) {
                throw new BadRequestException("Product is required for every invoice item");
            }
            if (item.getQty() == null || item.getQty() < 1) {
                throw new BadRequestException("Quantity must be at least 1 for every invoice item");
            }
            if (item.getPrice() == null || item.getPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Price cannot be empty or negative");
            }
            if (item.getDiscountValue() != null && item.getDiscountValue().compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Item discount cannot be negative");
            }
            if (item.getDiscountPercent() != null
                    && (item.getDiscountPercent().compareTo(BigDecimal.ZERO) < 0 || item.getDiscountPercent().compareTo(BigDecimal.valueOf(100)) > 0)) {
                throw new BadRequestException("Item discount percent must be between 0 and 100");
            }
        }
        if (request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Invoice discount cannot be negative");
        }
        if (request.getPaidAmount() != null && request.getPaidAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Paid amount cannot be negative");
        }
    }

    private Map<String, Object> snapshot(Invoice invoice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceNo", invoice.getInvoiceNo());
        data.put("customerId", invoice.getCustomer().getId());
        data.put("customerName", invoice.getCustomer().getName());
        data.put("customerState", invoice.getCustomer().getState());
        data.put("customerGstin", invoice.getCustomer().getGstin() != null ? invoice.getCustomer().getGstin() : invoice.getCustomer().getGstNo());
        data.put("referByUserId", invoice.getReferByUser() != null ? invoice.getReferByUser().getId() : null);
        data.put("referByUserName", invoice.getReferByUser() != null ? invoice.getReferByUser().getFullName() : null);
        data.put("referByUsername", invoice.getReferByUser() != null ? invoice.getReferByUser().getUsername() : null);
        data.put("invoiceDate", invoice.getInvoiceDate());
        data.put("subtotal", scale(invoice.getSubtotal()));
        data.put("taxableAmount", scale(invoice.getTaxableAmount()));
        data.put("cgstTotal", scale(invoice.getCgstTotal()));
        data.put("sgstTotal", scale(invoice.getSgstTotal()));
        data.put("igstTotal", scale(invoice.getIgstTotal()));
        data.put("taxAmount", scale(invoice.getTaxAmount()));
        data.put("discountAmount", scale(invoice.getDiscountAmount()));
        data.put("roundOff", scale(invoice.getRoundOff()));
        data.put("grandTotal", scale(invoice.getGrandTotal()));
        data.put("totalAmount", scale(invoice.getTotalAmount()));
        data.put("paidAmount", scale(invoice.getPaidAmount()));
        data.put("balanceAmount", scale(invoice.getBalanceAmount()));
        data.put("paymentStatus", invoice.getPaymentStatus() != null ? invoice.getPaymentStatus().name() : null);
        data.put("deleted", invoice.isDeleted());
        data.put("items", invoice.getItems().stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productId", item.getProduct().getId());
            row.put("productName", item.getProduct().getName());
            row.put("qty", item.getQty());
            row.put("price", scale(item.getPrice()));
            row.put("discountPercent", scale(item.getDiscountPercent()));
            row.put("discountAmount", scale(item.getDiscountAmount()));
            row.put("taxMasterId", item.getTaxMaster() != null ? item.getTaxMaster().getId() : null);
            row.put("taxName", item.getTaxName());
            row.put("taxRate", scale(item.getTaxRate()));
            row.put("hsnCode", item.getHsnCode());
            row.put("taxableAmount", scale(item.getTaxableAmount()));
            row.put("cgstRate", scale(item.getCgstRate()));
            row.put("cgstAmount", scale(item.getCgstAmount()));
            row.put("sgstRate", scale(item.getSgstRate()));
            row.put("sgstAmount", scale(item.getSgstAmount()));
            row.put("igstRate", scale(item.getIgstRate()));
            row.put("igstAmount", scale(item.getIgstAmount()));
            row.put("lineTotal", scale(item.getLineTotal()));
            return row;
        }).toList());
        return data;
    }

    private Map<String, Object> referBySnapshot(Invoice invoice) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("invoiceNo", invoice.getInvoiceNo());
        data.put("referByUserId", invoice.getReferByUser() != null ? invoice.getReferByUser().getId() : null);
        data.put("referByUserName", invoice.getReferByUser() != null ? invoice.getReferByUser().getFullName() : null);
        data.put("referByUsername", invoice.getReferByUser() != null ? invoice.getReferByUser().getUsername() : null);
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
        return value == null ? zero() : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private InvoiceRequest mapToRequest(Map<String, Object> param) {
        InvoiceRequest invoiceRequest = new InvoiceRequest();
        Long customerIdValue = DataTypeUtility.getForeignKeyValue(param.get("customerId"));
        if (customerIdValue == null) {
            customerIdValue = DataTypeUtility.getForeignKeyValue(param.get("customer_id"));
        }
        invoiceRequest.setCustomerId(customerIdValue);
        String invoiceDateString = DataTypeUtility.stringValue(param.get("invoiceDate"));
        if (invoiceDateString.length() == 0) {
            invoiceDateString = DataTypeUtility.stringValue(param.get("invoice_date"));
        }
        LocalDate invoiceDateValue = null;
        if (invoiceDateString.length() > 0) {
            invoiceDateValue = DataTypeUtility.parseLocalDate(invoiceDateString, "yyyy-MM-dd");
            if (invoiceDateValue == null) {
                invoiceDateValue = DataTypeUtility.parseLocalDate(invoiceDateString, "dd-MM-yyyy");
            }
            if (invoiceDateValue == null) {
                invoiceDateValue = DataTypeUtility.parseLocalDate(invoiceDateString, "yyyy/MM/dd");
            }
        }
        invoiceRequest.setInvoiceDate(invoiceDateValue);
        Long referByUserIdValue = DataTypeUtility.getForeignKeyValue(param.get("referByUserId"));
        if (referByUserIdValue == null) {
            referByUserIdValue = DataTypeUtility.getForeignKeyValue(param.get("refer_by_user_id"));
        }
        if (referByUserIdValue == null) {
            referByUserIdValue = DataTypeUtility.getForeignKeyValue(param.get("referByUser"));
        }
        invoiceRequest.setReferByUserId(referByUserIdValue);
        BigDecimal discountAmountValue = DataTypeUtility.bigDecimalObjectValue(param.get("discountAmount"));
        if (discountAmountValue == null) {
            discountAmountValue = DataTypeUtility.bigDecimalObjectValue(param.get("discount_amount"));
        }
        invoiceRequest.setDiscountAmount(discountAmountValue);
        BigDecimal paidAmountValue = DataTypeUtility.bigDecimalObjectValue(param.get("paidAmount"));
        if (paidAmountValue == null) {
            paidAmountValue = DataTypeUtility.bigDecimalObjectValue(param.get("paid_amount"));
        }
        invoiceRequest.setPaidAmount(paidAmountValue);
        String paymentModeValue = DataTypeUtility.stringValue(param.get("paymentMode"));
        if (paymentModeValue.length() == 0) {
            paymentModeValue = DataTypeUtility.stringValue(param.get("payment_mode"));
        }
        if (paymentModeValue.length() == 0) {
            paymentModeValue = null;
        }
        invoiceRequest.setPaymentMode(paymentModeValue);
        String referenceNoValue = DataTypeUtility.stringValue(param.get("referenceNo"));
        if (referenceNoValue.length() == 0) {
            referenceNoValue = null;
        }
        invoiceRequest.setReferenceNo(referenceNoValue);
        List<InvoiceItemRequest> invoiceItemList = new ArrayList<>();
        Object itemsObject = param.get("items");
        if (itemsObject instanceof List) {
            for (Object itemObject : (List<?>) itemsObject) {
                if (itemObject instanceof Map) {
                    Map<String, Object> itemMap = (Map<String, Object>) itemObject;
                    InvoiceItemRequest invoiceItemRequest = new InvoiceItemRequest();
                    Long productIdValue = DataTypeUtility.getForeignKeyValue(itemMap.get("productId"));
                    if (productIdValue == null) {
                        productIdValue = DataTypeUtility.getForeignKeyValue(itemMap.get("product_id"));
                    }
                    invoiceItemRequest.setProductId(productIdValue);
                    Integer qtyValue = DataTypeUtility.integerNullValue(itemMap.get("qty"));
                    if (qtyValue == null) {
                        qtyValue = DataTypeUtility.integerNullValue(itemMap.get("quantity"));
                    }
                    invoiceItemRequest.setQty(qtyValue);
                    BigDecimal priceValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("price"));
                    if (priceValue == null) {
                        priceValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("unitPrice"));
                    }
                    invoiceItemRequest.setPrice(priceValue);
                    BigDecimal taxPercentValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("taxPercent"));
                    if (taxPercentValue == null) {
                        taxPercentValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("tax_percent"));
                    }
                    invoiceItemRequest.setTaxPercent(taxPercentValue);
                    BigDecimal discountPercentValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("discountPercent"));
                    if (discountPercentValue == null) {
                        discountPercentValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("discount_percent"));
                    }
                    invoiceItemRequest.setDiscountPercent(discountPercentValue);
                    String discountTypeValue = DataTypeUtility.stringValue(itemMap.get("discountType"));
                    if (discountTypeValue.length() == 0) {
                        discountTypeValue = DataTypeUtility.stringValue(itemMap.get("discount_type"));
                    }
                    if (discountTypeValue.length() == 0) {
                        discountTypeValue = null;
                    }
                    invoiceItemRequest.setDiscountType(discountTypeValue);
                    BigDecimal discountValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("discountValue"));
                    if (discountValue == null) {
                        discountValue = DataTypeUtility.bigDecimalObjectValue(itemMap.get("discount_value"));
                    }
                    invoiceItemRequest.setDiscountValue(discountValue);
                    invoiceItemList.add(invoiceItemRequest);
                }
            }
        }
        invoiceRequest.setItems(invoiceItemList);
        return invoiceRequest;
    }

    private User resolveReferByUser(Company company, Long referByUserId) {
        if (referByUserId == null) {
            return null;
        }
        User user = userRepository.findById(referByUserId)
                .orElseThrow(() -> new BadRequestException("Refer By user not found in this company"));
        if (!user.isActive()) {
            throw new BadRequestException("Refer By user must be active");
        }
        return user;
    }
}
