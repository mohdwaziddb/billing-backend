package com.billing.service.payment;

import com.billing.dto.notification.NotificationStatus;
import com.billing.dto.payment.gateway.PaymentTransactionResponse;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.Payment;
import com.billing.entity.PaymentGatewaySetting;
import com.billing.entity.PaymentTransaction;
import com.billing.entity.enums.PaymentGatewayType;
import com.billing.entity.enums.PaymentSource;
import com.billing.entity.enums.PaymentTransactionChannel;
import com.billing.entity.enums.PaymentTransactionStatus;
import com.billing.exception.BadRequestException;
import com.billing.exception.ResourceNotFoundException;
import com.billing.repository.PaymentTransactionRepository;
import com.billing.service.AccessControlService;
import com.billing.service.AuditLogService;
import com.billing.service.AuditNameResolver;
import com.billing.service.InvoiceService;
import com.billing.service.PaymentModeMasterService;
import com.billing.service.payment.gateway.PaymentGatewayFactory;
import com.billing.service.payment.gateway.PaymentGatewayProvider;
import com.billing.service.payment.gateway.PaymentGatewayResolvedSettings;
import com.billing.service.payment.gateway.dto.GatewayLinkRequest;
import com.billing.service.payment.gateway.dto.GatewayLinkResult;
import com.billing.service.payment.gateway.dto.GatewayOrderRequest;
import com.billing.service.payment.gateway.dto.GatewayOrderResult;
import com.billing.service.payment.gateway.dto.GatewayPaymentResult;
import com.billing.service.payment.gateway.dto.GatewayWebhookEvent;
import com.billing.service.sms.CommonSmsService;
import com.billing.service.sms.SmsSendResult;
import com.billing.service.whatsapp.CommonWhatsAppService;
import com.billing.service.whatsapp.WhatsAppSendResult;
import com.billing.util.DataTypeUtility;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Online payment ka common flow — kisi bhi gateway ke liye same:
 * initiate (order / link) → verify (checkout callback) / webhook / refresh /
 * reconcile → settle (idempotent, row lock) → PaymentRecordingService.
 */
@Service
@RequiredArgsConstructor
public class OnlinePaymentService {

    private static final Logger log = LoggerFactory.getLogger(OnlinePaymentService.class);

    private static final String CURRENCY = "INR";
    private static final int DEFAULT_LINK_EXPIRY_DAYS = 7;
    private static final List<PaymentTransactionStatus> OPEN_STATUSES = List.of(
            PaymentTransactionStatus.CREATED, PaymentTransactionStatus.PENDING, PaymentTransactionStatus.CANCELLED);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter TXN_REF_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentGatewaySettingService paymentGatewaySettingService;
    private final PaymentGatewayFactory paymentGatewayFactory;
    private final PaymentRecordingService paymentRecordingService;
    private final PaymentModeMasterService paymentModeMasterService;
    private final InvoiceService invoiceService;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final AuditNameResolver auditNameResolver;
    private final CommonWhatsAppService commonWhatsAppService;
    private final CommonSmsService commonSmsService;

    @PersistenceContext
    private EntityManager entityManager;

    // Self proxy: reconcile me har transaction alag @Transactional me settle ho.
    @Autowired
    @Lazy
    private OnlinePaymentService self;

    @Transactional
    public PaymentTransactionResponse initiate(Map<String, Object> param, String email) {
        Company company = accessControlService.getCurrentCompany(email);
        Long invoiceId = DataTypeUtility.getForeignKeyValue(param.get("invoiceId"));
        if (invoiceId == null) {
            throw new BadRequestException("Select an invoice to collect online payment");
        }
        Invoice invoice = invoiceService.getInvoiceOrThrow(company, invoiceId);
        if (invoice.getCustomer() != null && !invoice.getCustomer().isActive()) {
            throw new BadRequestException("Customer is inactive. Activate the customer before collecting payment.");
        }
        BigDecimal balance = scale(invoice.getBalanceAmount());
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Invoice is already fully paid");
        }
        BigDecimal amount = DataTypeUtility.bigDecimalObjectValue(param.get("amount"));
        amount = amount == null ? balance : scale(amount);
        if (amount.compareTo(BigDecimal.ONE) < 0) {
            throw new BadRequestException("Online payment amount must be at least 1.00");
        }
        if (amount.compareTo(balance) > 0) {
            throw new BadRequestException("Amount cannot exceed invoice outstanding amount");
        }
        PaymentTransactionChannel channel = parseChannel(DataTypeUtility.stringValue(param.get("channel")));

        PaymentGatewaySetting setting = paymentGatewaySettingService.requireActiveGateway();
        PaymentGatewayProvider provider = paymentGatewayFactory.getProvider(setting.getGatewayType());
        PaymentGatewayResolvedSettings resolved = paymentGatewaySettingService.resolved(setting);
        if (channel == PaymentTransactionChannel.CHECKOUT && !provider.metadata().isSupportsCheckout()) {
            throw new BadRequestException("This gateway does not support checkout");
        }
        if (channel == PaymentTransactionChannel.PAYMENT_LINK && !provider.metadata().isSupportsPaymentLink()) {
            throw new BadRequestException("This gateway does not support payment links");
        }

        Customer customer = invoice.getCustomer();
        String txnRef = newTxnRef();
        PaymentTransaction transaction = PaymentTransaction.builder()
                .company(company)
                .txnRef(txnRef)
                .gatewayType(setting.getGatewayType())
                .channel(channel)
                .status(PaymentTransactionStatus.CREATED)
                .invoice(invoice)
                .customer(customer)
                .amount(amount)
                .currency(CURRENCY)
                .initiatedBy(email)
                .build();

        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("invoice_no", invoice.getInvoiceNo());
        notes.put("company", company.getName());
        Map<String, Object> checkout = null;
        if (channel == PaymentTransactionChannel.CHECKOUT) {
            GatewayOrderResult order = provider.createOrder(resolved, new GatewayOrderRequest(txnRef, amount, CURRENCY, invoice.getInvoiceNo(), notes));
            transaction.setGatewayOrderId(order.gatewayOrderId());
            transaction.setLastGatewayResponse(order.rawResponse());
            checkout = new LinkedHashMap<>(order.checkoutOptions());
            checkout.put("name", company.getName());
            checkout.put("description", "Invoice " + invoice.getInvoiceNo());
            if (company.getLogoUrl() != null && company.getLogoUrl().startsWith("https://")) {
                checkout.put("image", company.getLogoUrl());
            }
            Map<String, Object> prefill = new LinkedHashMap<>();
            putIfPresent(prefill, "name", customer.getName());
            putIfPresent(prefill, "contact", customer.getMobile());
            putIfPresent(prefill, "email", customer.getEmail());
            checkout.put("prefill", prefill);
            checkout.put("notes", Map.of("txn_ref", txnRef, "invoice_no", invoice.getInvoiceNo()));
        } else {
            int expiryDays = DataTypeUtility.integerValue(param.get("expiryDays"));
            if (expiryDays <= 0 || expiryDays > 30) {
                expiryDays = DEFAULT_LINK_EXPIRY_DAYS;
            }
            LocalDateTime expiresAt = LocalDateTime.now().plusDays(expiryDays);
            GatewayLinkResult link = provider.createPaymentLink(resolved, new GatewayLinkRequest(
                    txnRef, amount, CURRENCY,
                    "Payment for invoice " + invoice.getInvoiceNo() + " - " + company.getName(),
                    blankToNull(customer.getName()), blankToNull(customer.getMobile()), blankToNull(customer.getEmail()),
                    expiresAt, notes));
            transaction.setGatewayLinkId(link.gatewayLinkId());
            transaction.setLinkUrl(link.linkUrl());
            transaction.setExpiresAt(expiresAt);
            transaction.setLastGatewayResponse(link.rawResponse());
        }
        PaymentTransaction saved = paymentTransactionRepository.save(transaction);
        auditLogService.logEvent(email, company, "Invoice", "Invoice", invoice.getId(),
                channel == PaymentTransactionChannel.CHECKOUT ? "ONLINE_PAYMENT_STARTED" : "PAYMENT_LINK_CREATED", auditData(saved));
        return toResponse(saved, checkout);
    }

    /** Checkout popup success callback (signature verify + status). */
    @Transactional
    public PaymentTransactionResponse verify(String txnRef, Map<String, Object> param, String email) {
        Company company = accessControlService.getCurrentCompany(email);
        PaymentTransaction transaction = getTransactionOrThrow(txnRef);
        if (transaction.getStatus() == PaymentTransactionStatus.SUCCESS) {
            return toResponse(transaction, null);
        }
        PaymentGatewaySetting setting = paymentGatewaySettingService.requireGateway(transaction.getGatewayType());
        GatewayPaymentResult result = paymentGatewayFactory.getProvider(transaction.getGatewayType())
                .verifyCheckout(paymentGatewaySettingService.resolved(setting), transaction, param);
        return toResponse(settle(company, txnRef, result, email, null), null);
    }

    /** Popup band / user ne link cancel kiya. Webhook baad me SUCCESS bhi kar sakta hai. */
    @Transactional
    public PaymentTransactionResponse cancel(String txnRef, String email) {
        Company company = accessControlService.getCurrentCompany(email);
        PaymentTransaction transaction = paymentTransactionRepository.lockByTxnRef(txnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found"));
        if (transaction.getStatus() == PaymentTransactionStatus.CREATED || transaction.getStatus() == PaymentTransactionStatus.PENDING) {
            if (transaction.getChannel() == PaymentTransactionChannel.PAYMENT_LINK) {
                PaymentGatewaySetting setting = paymentGatewaySettingService.requireGateway(transaction.getGatewayType());
                paymentGatewayFactory.getProvider(transaction.getGatewayType())
                        .cancelPaymentLink(paymentGatewaySettingService.resolved(setting), transaction);
            }
            transaction.setStatus(PaymentTransactionStatus.CANCELLED);
            paymentTransactionRepository.save(transaction);
            auditLogService.logEvent(email, company, "Invoice", "Invoice", transaction.getInvoice().getId(), "ONLINE_PAYMENT_CANCELLED", auditData(transaction));
        }
        return toResponse(transaction, null);
    }

    /** Gateway se live status (webhook na pahuncha ho to bhi screen update ho). */
    @Transactional
    public PaymentTransactionResponse refresh(String txnRef, String email) {
        Company company = accessControlService.getCurrentCompany(email);
        PaymentTransaction transaction = getTransactionOrThrow(txnRef);
        if (transaction.getStatus() == PaymentTransactionStatus.SUCCESS) {
            return toResponse(transaction, null);
        }
        PaymentGatewaySetting setting = paymentGatewaySettingService.requireGateway(transaction.getGatewayType());
        GatewayPaymentResult result = paymentGatewayFactory.getProvider(transaction.getGatewayType())
                .fetchStatus(paymentGatewaySettingService.resolved(setting), transaction);
        return toResponse(settle(company, txnRef, result, email, null), null);
    }

    /** Webhook: signature verify → transaction dhoondo → settle. Return = log ke liye outcome. */
    @Transactional
    public String handleWebhook(String gatewayCode, String rawBody, Map<String, String> headers) {
        PaymentGatewayType gatewayType = paymentGatewayFactory.parseType(gatewayCode);
        PaymentGatewaySetting setting = paymentGatewaySettingService.requireGateway(gatewayType);
        GatewayWebhookEvent event = paymentGatewayFactory.getProvider(gatewayType)
                .parseWebhook(paymentGatewaySettingService.resolved(setting), rawBody, headers);
        if (event.result() == null) {
            return "ignored:" + event.eventType();
        }
        PaymentTransaction transaction = findForWebhook(event);
        if (transaction == null) {
            log.warn("[PaymentWebhook] {} event {} has no matching transaction (order={}, link={}, ref={})",
                    gatewayType, event.eventType(), event.gatewayOrderId(), event.gatewayLinkId(), event.txnRef());
            return "unmatched:" + event.eventType();
        }
        Company company = accessControlService.getTenantCompany();
        PaymentTransaction settled = settle(company, transaction.getTxnRef(), event.result(), null, gatewayName(gatewayType) + " Webhook");
        return "processed:" + event.eventType() + ":" + settled.getStatus().name();
    }

    @Transactional(readOnly = true)
    public PaymentTransactionResponse get(String txnRef, String email) {
        accessControlService.getCurrentCompany(email);
        return toResponse(getTransactionOrThrow(txnRef), null);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> list(Map<String, Object> param, String email) {
        Company company = accessControlService.getCurrentCompany(email);
        Long invoiceId = DataTypeUtility.getForeignKeyValue(param.get("invoiceId"));
        if (invoiceId == null) {
            throw new BadRequestException("Invoice is required");
        }
        Invoice invoice = invoiceService.getInvoiceOrThrow(company, invoiceId);
        return paymentTransactionRepository.findByInvoiceOrderByIdDesc(invoice).stream()
                .map(transaction -> toResponse(transaction, null))
                .toList();
    }

    /** Payment link customer ko WhatsApp / SMS (company ke configured provider se). */
    @Transactional
    public Map<String, Object> sendLink(String txnRef, Map<String, Object> param, String email) {
        Company company = accessControlService.getCurrentCompany(email);
        PaymentTransaction transaction = getTransactionOrThrow(txnRef);
        if (transaction.getChannel() != PaymentTransactionChannel.PAYMENT_LINK || transaction.getLinkUrl() == null) {
            throw new BadRequestException("This transaction has no payment link");
        }
        if (transaction.getStatus() != PaymentTransactionStatus.CREATED && transaction.getStatus() != PaymentTransactionStatus.PENDING) {
            throw new BadRequestException("Payment link is no longer active");
        }
        String channel = DataTypeUtility.stringValue(param.get("channel")).toUpperCase(Locale.ENGLISH);
        String mobile = DataTypeUtility.stringValue(param.get("mobile"));
        if (mobile.length() == 0) {
            mobile = DataTypeUtility.stringValue(transaction.getCustomer().getMobile());
        }
        if (mobile.length() == 0) {
            throw new BadRequestException("Customer mobile number is required to send the link");
        }
        String message = linkMessage(company, transaction);
        boolean sent;
        String failure;
        if ("WHATSAPP".equals(channel)) {
            List<WhatsAppSendResult> results = commonWhatsAppService.sendMessage(company, List.of(mobile), message);
            sent = results.stream().anyMatch(result -> result.status() == NotificationStatus.SENT);
            failure = results.isEmpty() ? "Invalid mobile number" : firstNonBlank(results.get(0).failureReason(), results.get(0).providerResponse());
        } else if ("SMS".equals(channel)) {
            List<SmsSendResult> results = commonSmsService.sendSms(company, List.of(mobile), message);
            sent = results.stream().anyMatch(result -> result.status() == NotificationStatus.SENT);
            failure = results.isEmpty() ? "Invalid mobile number" : results.get(0).providerResponse();
        } else {
            throw new BadRequestException("Select WhatsApp or SMS");
        }
        if (!sent) {
            throw new BadRequestException(failure == null ? "Unable to send payment link" : failure);
        }
        auditLogService.logEvent(email, company, "Invoice", "Invoice", transaction.getInvoice().getId(), "PAYMENT_LINK_SENT",
                Map.of("txnRef", txnRef, "channel", channel, "mobile", mobile));
        return Map.of("status", "sent", "channel", channel);
    }

    /** Scheduler: open transactions gateway se check (webhook miss ho gaya ho to). */
    public int reconcileOpenTransactions(LocalDateTime createdFrom, LocalDateTime createdTo) {
        int settled = 0;
        for (PaymentTransaction transaction : paymentTransactionRepository
                .findByStatusInAndCreatedAtBetweenOrderByIdAsc(OPEN_STATUSES, createdFrom, createdTo)) {
            try {
                PaymentTransactionStatus status = self.reconcileOne(transaction.getTxnRef());
                if (status == PaymentTransactionStatus.SUCCESS) {
                    settled++;
                }
            } catch (Exception exception) {
                log.warn("[PaymentReconcile] {} failed: {}", transaction.getTxnRef(), exception.getMessage());
            }
        }
        return settled;
    }

    @Transactional
    public PaymentTransactionStatus reconcileOne(String txnRef) {
        PaymentTransaction transaction = getTransactionOrThrow(txnRef);
        PaymentGatewaySetting setting = paymentGatewaySettingService.requireGateway(transaction.getGatewayType());
        GatewayPaymentResult result = paymentGatewayFactory.getProvider(transaction.getGatewayType())
                .fetchStatus(paymentGatewaySettingService.resolved(setting), transaction);
        // Purana checkout jo kabhi pay nahi hua: 24h baad EXPIRED.
        if (result.status() == PaymentTransactionStatus.PENDING
                && transaction.getChannel() == PaymentTransactionChannel.CHECKOUT
                && transaction.getCreatedAt() != null
                && transaction.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
            result = new GatewayPaymentResult(PaymentTransactionStatus.EXPIRED, null, null, null, "Checkout not completed", result.rawResponse());
        }
        Company company = accessControlService.getTenantCompany();
        return settle(company, txnRef, result, null, gatewayName(transaction.getGatewayType()) + " Reconciliation").getStatus();
    }

    /**
     * Idempotent: transaction row lock; SUCCESS ho chuka ho to kuch nahi. SUCCESS
     * par Payment(s) PaymentRecordingService se — invoice balance se zyada aaya
     * (link + checkout dono pay) to baaki customer ke dusre open invoices / advance me.
     */
    PaymentTransaction settle(Company company, String txnRef, GatewayPaymentResult result, String actorEmail, String actorName) {
        PaymentTransaction transaction = paymentTransactionRepository.findByTxnRef(txnRef)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found"));
        // Row lock + DB se taaza state: isi transaction me pehle load hua entity stale ho sakta hai
        // (verify + webhook ek saath) — bina refresh ke dono SUCCESS dekh kar do Payment bana dete.
        entityManager.refresh(transaction, LockModeType.PESSIMISTIC_WRITE);
        if (transaction.getStatus() == PaymentTransactionStatus.SUCCESS || result == null) {
            return transaction;
        }
        if (result.rawResponse() != null) {
            transaction.setLastGatewayResponse(result.rawResponse());
        }
        switch (result.status()) {
            case SUCCESS -> applySuccess(company, transaction, result, actorEmail, actorName);
            case FAILED, EXPIRED, CANCELLED -> {
                if (transaction.getStatus() != result.status()) {
                    transaction.setStatus(result.status());
                    transaction.setFailureReason(truncate(result.failureReason(), 500));
                    if (result.gatewayPaymentId() != null) {
                        transaction.setGatewayPaymentId(result.gatewayPaymentId());
                    }
                    if (result.method() != null) {
                        transaction.setMethod(result.method());
                    }
                    logTransactionEvent(company, transaction, actorEmail, actorName, "ONLINE_PAYMENT_" + result.status().name());
                }
            }
            default -> {
                if (transaction.getStatus() == PaymentTransactionStatus.CREATED && result.gatewayPaymentId() != null) {
                    transaction.setStatus(PaymentTransactionStatus.PENDING);
                    transaction.setGatewayPaymentId(result.gatewayPaymentId());
                }
            }
        }
        return paymentTransactionRepository.save(transaction);
    }

    private void applySuccess(Company company, PaymentTransaction transaction, GatewayPaymentResult result, String actorEmail, String actorName) {
        BigDecimal received = result.amount() != null ? scale(result.amount()) : scale(transaction.getAmount());
        if (received.compareTo(scale(transaction.getAmount())) != 0) {
            log.warn("[OnlinePayment] {} amount mismatch: expected {} received {}", transaction.getTxnRef(), transaction.getAmount(), received);
        }
        String gatewayName = gatewayName(transaction.getGatewayType());
        String remarks = "Online payment via " + gatewayName
                + (result.method() != null ? " (" + result.method().toUpperCase(Locale.ENGLISH) + ")" : "");
        paymentModeMasterService.ensureDefaults(company);
        PaymentEntry entry = new PaymentEntry(company, actorEmail, actorName, transaction.getCustomer(), transaction.getInvoice(),
                received, LocalDate.now(), paymentModeMasterService.onlineModeCode(company), remarks,
                result.gatewayPaymentId(), PaymentSource.GATEWAY, transaction);
        List<Payment> payments = paymentRecordingService.recordAllocated(entry);

        transaction.setStatus(PaymentTransactionStatus.SUCCESS);
        transaction.setGatewayPaymentId(result.gatewayPaymentId());
        transaction.setMethod(result.method());
        transaction.setFailureReason(null);
        transaction.setSettledAt(LocalDateTime.now());
        transaction.setPayment(payments.isEmpty() ? null : payments.get(0));
        logTransactionEvent(company, transaction, actorEmail, actorName, "ONLINE_PAYMENT_SUCCESS");
    }

    private void logTransactionEvent(Company company, PaymentTransaction transaction, String actorEmail, String actorName, String action) {
        Long invoiceId = transaction.getInvoice().getId();
        if (actorEmail == null) {
            auditLogService.logEventAsActor(actorName, company, "Invoice", "Invoice", invoiceId, action, auditData(transaction));
        } else {
            auditLogService.logEvent(actorEmail, company, "Invoice", "Invoice", invoiceId, action, auditData(transaction));
        }
    }

    private PaymentTransaction findForWebhook(GatewayWebhookEvent event) {
        if (event.txnRef() != null) {
            PaymentTransaction byRef = paymentTransactionRepository.findByTxnRef(event.txnRef()).orElse(null);
            if (byRef != null) {
                return byRef;
            }
        }
        if (event.gatewayLinkId() != null) {
            PaymentTransaction byLink = paymentTransactionRepository.findFirstByGatewayLinkId(event.gatewayLinkId()).orElse(null);
            if (byLink != null) {
                return byLink;
            }
        }
        if (event.gatewayOrderId() != null) {
            return paymentTransactionRepository.findFirstByGatewayOrderId(event.gatewayOrderId()).orElse(null);
        }
        return null;
    }

    private PaymentTransaction getTransactionOrThrow(String txnRef) {
        String ref = DataTypeUtility.stringValue(txnRef);
        if (ref.length() == 0) {
            throw new ResourceNotFoundException("Payment transaction not found");
        }
        return paymentTransactionRepository.findByTxnRef(ref)
                .orElseThrow(() -> new ResourceNotFoundException("Payment transaction not found"));
    }

    private PaymentTransactionChannel parseChannel(String value) {
        if (value.length() == 0) {
            return PaymentTransactionChannel.CHECKOUT;
        }
        try {
            return PaymentTransactionChannel.valueOf(value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid payment channel");
        }
    }

    private String linkMessage(Company company, PaymentTransaction transaction) {
        String customerName = DataTypeUtility.stringValue(transaction.getCustomer().getName());
        return "Dear " + (customerName.length() == 0 ? "Customer" : customerName)
                + ", please pay Rs. " + scale(transaction.getAmount()).toPlainString()
                + " for invoice " + transaction.getInvoice().getInvoiceNo()
                + " to " + company.getName() + ": " + transaction.getLinkUrl();
    }

    private String gatewayName(PaymentGatewayType gatewayType) {
        return paymentGatewayFactory.getProvider(gatewayType).metadata().getGatewayName();
    }

    private PaymentTransactionResponse toResponse(PaymentTransaction transaction, Map<String, Object> checkout) {
        Invoice invoice = transaction.getInvoice();
        Customer customer = transaction.getCustomer();
        return PaymentTransactionResponse.builder()
                .id(transaction.getId())
                .txnRef(transaction.getTxnRef())
                .gatewayType(transaction.getGatewayType().name())
                .channel(transaction.getChannel().name())
                .status(transaction.getStatus().name())
                .invoiceId(invoice.getId())
                .invoiceNo(invoice.getInvoiceNo())
                .customerId(customer.getId())
                .customerName(customer.getName())
                .customerMobile(customer.getMobile())
                .amount(scale(transaction.getAmount()))
                .currency(transaction.getCurrency())
                .gatewayOrderId(transaction.getGatewayOrderId())
                .gatewayPaymentId(transaction.getGatewayPaymentId())
                .linkUrl(transaction.getLinkUrl())
                .method(transaction.getMethod())
                .failureReason(transaction.getFailureReason())
                .paymentId(transaction.getPayment() != null ? transaction.getPayment().getId() : null)
                .settledAt(transaction.getSettledAt())
                .expiresAt(transaction.getExpiresAt())
                .createdAt(transaction.getCreatedAt())
                .createdBy(auditNameResolver.displayName(transaction.getCreatedBy()))
                .checkout(checkout)
                .build();
    }

    private Map<String, Object> auditData(PaymentTransaction transaction) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("txnRef", transaction.getTxnRef());
        data.put("gateway", transaction.getGatewayType().name());
        data.put("channel", transaction.getChannel().name());
        data.put("status", transaction.getStatus().name());
        data.put("amount", scale(transaction.getAmount()));
        data.put("gatewayOrderId", transaction.getGatewayOrderId());
        data.put("gatewayPaymentId", transaction.getGatewayPaymentId());
        data.put("method", transaction.getMethod());
        data.put("failureReason", transaction.getFailureReason());
        return data;
    }

    private String newTxnRef() {
        StringBuilder suffix = new StringBuilder();
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < 6; i++) {
            suffix.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return "PTX" + LocalDateTime.now().format(TXN_REF_TIME) + suffix;
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
