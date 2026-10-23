package com.billing.service.payment;

import com.billing.dto.payment.gateway.PaymentGatewayMetadataResponse;
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
import com.billing.repository.PaymentTransactionRepository;
import com.billing.service.AccessControlService;
import com.billing.service.AuditLogService;
import com.billing.service.AuditNameResolver;
import com.billing.service.InvoiceService;
import com.billing.service.PaymentModeMasterService;
import com.billing.service.payment.gateway.PaymentGatewayFactory;
import com.billing.service.payment.gateway.PaymentGatewayProvider;
import com.billing.service.payment.gateway.dto.GatewayPaymentResult;
import com.billing.service.sms.CommonSmsService;
import com.billing.service.whatsapp.CommonWhatsAppService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnlinePaymentServiceTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PaymentGatewaySettingService paymentGatewaySettingService;
    @Mock
    private PaymentGatewayFactory paymentGatewayFactory;
    @Mock
    private PaymentRecordingService paymentRecordingService;
    @Mock
    private PaymentModeMasterService paymentModeMasterService;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AuditNameResolver auditNameResolver;
    @Mock
    private CommonWhatsAppService commonWhatsAppService;
    @Mock
    private CommonSmsService commonSmsService;
    @Mock
    private PaymentGatewayProvider provider;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private OnlinePaymentService onlinePaymentService;

    private final Company company = Company.builder().id(1L).name("Acme").build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(onlinePaymentService, "entityManager", entityManager);
    }
    private final Customer customer = Customer.builder().id(5L).name("Cust").mobile("9999999999").build();

    @Test
    void successSettlesOnceEvenWhenWebhookRepeats() {
        PaymentTransaction transaction = transaction(PaymentTransactionStatus.CREATED);
        when(paymentTransactionRepository.findByTxnRef("PTX1")).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(call -> call.getArgument(0));
        when(paymentModeMasterService.onlineModeCode(company)).thenReturn("ONLINE");
        when(paymentRecordingService.recordAllocated(any(PaymentEntry.class))).thenReturn(List.of(Payment.builder().id(77L).build()));
        stubGatewayName();
        GatewayPaymentResult captured = new GatewayPaymentResult(PaymentTransactionStatus.SUCCESS, "pay_1", "upi", new BigDecimal("500.00"), null, "{}");

        onlinePaymentService.settle(company, "PTX1", captured, null, "Razorpay Webhook");
        onlinePaymentService.settle(company, "PTX1", captured, null, "Razorpay Webhook");

        ArgumentCaptor<PaymentEntry> entry = ArgumentCaptor.forClass(PaymentEntry.class);
        verify(paymentRecordingService, times(1)).recordAllocated(entry.capture());
        assertEquals(PaymentTransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals("pay_1", transaction.getGatewayPaymentId());
        assertEquals(77L, transaction.getPayment().getId());
        assertEquals(new BigDecimal("500.00"), entry.getValue().amount());
        assertEquals("ONLINE", entry.getValue().modeCode());
        assertEquals("pay_1", entry.getValue().referenceNo());
        assertEquals(PaymentSource.GATEWAY, entry.getValue().source());
        assertEquals(transaction.getInvoice(), entry.getValue().invoice());
    }

    @Test
    void failedAttemptCanStillBecomeSuccessOnRetry() {
        PaymentTransaction transaction = transaction(PaymentTransactionStatus.CREATED);
        when(paymentTransactionRepository.findByTxnRef("PTX1")).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(call -> call.getArgument(0));
        when(paymentModeMasterService.onlineModeCode(company)).thenReturn("ONLINE");
        when(paymentRecordingService.recordAllocated(any(PaymentEntry.class))).thenReturn(List.of(Payment.builder().id(1L).build()));
        stubGatewayName();

        onlinePaymentService.settle(company, "PTX1",
                new GatewayPaymentResult(PaymentTransactionStatus.FAILED, "pay_bad", "card", null, "Declined", "{}"), null, "Webhook");
        assertEquals(PaymentTransactionStatus.FAILED, transaction.getStatus());
        verify(paymentRecordingService, never()).recordAllocated(any());

        onlinePaymentService.settle(company, "PTX1",
                new GatewayPaymentResult(PaymentTransactionStatus.SUCCESS, "pay_ok", "upi", new BigDecimal("500.00"), null, "{}"), null, "Webhook");
        assertEquals(PaymentTransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(null, transaction.getFailureReason());
        verify(paymentRecordingService, times(1)).recordAllocated(any());
    }

    @Test
    void concurrentSettleSeesFreshLockedStateAndSkips() {
        // Verify ne CREATED padha; lock milne tak webhook SUCCESS commit kar chuka (refresh DB state laata hai).
        PaymentTransaction transaction = transaction(PaymentTransactionStatus.CREATED);
        when(paymentTransactionRepository.findByTxnRef("PTX1")).thenReturn(Optional.of(transaction));
        doAnswer(call -> {
            transaction.setStatus(PaymentTransactionStatus.SUCCESS);
            return null;
        }).when(entityManager).refresh(eq(transaction), eq(LockModeType.PESSIMISTIC_WRITE));

        onlinePaymentService.settle(company, "PTX1",
                new GatewayPaymentResult(PaymentTransactionStatus.SUCCESS, "pay_1", "upi", new BigDecimal("500.00"), null, "{}"), "u", null);

        verify(paymentRecordingService, never()).recordAllocated(any());
    }

    @Test
    void lateFailureNeverDowngradesSuccess() {
        PaymentTransaction transaction = transaction(PaymentTransactionStatus.SUCCESS);
        when(paymentTransactionRepository.findByTxnRef("PTX1")).thenReturn(Optional.of(transaction));

        onlinePaymentService.settle(company, "PTX1",
                new GatewayPaymentResult(PaymentTransactionStatus.FAILED, "pay_x", null, null, "late", "{}"), null, "Webhook");

        assertEquals(PaymentTransactionStatus.SUCCESS, transaction.getStatus());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void initiateRejectsAmountAboveOutstanding() {
        Invoice invoice = invoice(new BigDecimal("300.00"));
        when(accessControlService.getCurrentCompany("u")).thenReturn(company);
        when(invoiceService.getInvoiceOrThrow(company, 10L)).thenReturn(invoice);

        assertThrows(BadRequestException.class, () -> onlinePaymentService.initiate(Map.of("invoiceId", 10, "amount", "300.01"), "u"));
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void initiateRejectsPaidInvoiceAndMissingGateway() {
        when(accessControlService.getCurrentCompany("u")).thenReturn(company);
        when(invoiceService.getInvoiceOrThrow(company, 10L)).thenReturn(invoice(BigDecimal.ZERO));
        assertThrows(BadRequestException.class, () -> onlinePaymentService.initiate(Map.of("invoiceId", 10), "u"));

        when(invoiceService.getInvoiceOrThrow(company, 11L)).thenReturn(invoice(new BigDecimal("100.00")));
        when(paymentGatewaySettingService.requireActiveGateway()).thenThrow(new BadRequestException("Online payment is not enabled"));
        assertThrows(BadRequestException.class, () -> onlinePaymentService.initiate(Map.of("invoiceId", 11), "u"));
    }

    @Test
    void cancelledCheckoutIsUpgradedBySuccessfulWebhook() {
        PaymentTransaction transaction = transaction(PaymentTransactionStatus.CANCELLED);
        when(paymentTransactionRepository.findByTxnRef("PTX1")).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(call -> call.getArgument(0));
        when(paymentModeMasterService.onlineModeCode(company)).thenReturn("ONLINE");
        when(paymentRecordingService.recordAllocated(any(PaymentEntry.class))).thenReturn(List.of(Payment.builder().id(2L).build()));
        stubGatewayName();

        onlinePaymentService.settle(company, "PTX1",
                new GatewayPaymentResult(PaymentTransactionStatus.SUCCESS, "pay_ok", "upi", null, null, "{}"), null, "Webhook");

        assertEquals(PaymentTransactionStatus.SUCCESS, transaction.getStatus());
        ArgumentCaptor<PaymentEntry> entry = ArgumentCaptor.forClass(PaymentEntry.class);
        verify(paymentRecordingService).recordAllocated(entry.capture());
        // Gateway ne amount na bataya to transaction ka amount.
        assertEquals(new BigDecimal("500.00"), entry.getValue().amount());
    }

    private void stubGatewayName() {
        lenient().when(paymentGatewayFactory.getProvider(PaymentGatewayType.RAZORPAY)).thenReturn(provider);
        lenient().when(provider.metadata()).thenReturn(PaymentGatewayMetadataResponse.builder().gatewayName("Razorpay").build());
        lenient().when(paymentGatewaySettingService.requireGateway(PaymentGatewayType.RAZORPAY))
                .thenReturn(PaymentGatewaySetting.builder().gatewayType(PaymentGatewayType.RAZORPAY).build());
    }

    private PaymentTransaction transaction(PaymentTransactionStatus status) {
        return PaymentTransaction.builder()
                .id(3L)
                .txnRef("PTX1")
                .gatewayType(PaymentGatewayType.RAZORPAY)
                .channel(PaymentTransactionChannel.CHECKOUT)
                .status(status)
                .invoice(invoice(new BigDecimal("500.00")))
                .customer(customer)
                .amount(new BigDecimal("500.00"))
                .currency("INR")
                .gatewayOrderId("order_1")
                .build();
    }

    private Invoice invoice(BigDecimal balance) {
        return Invoice.builder().id(10L).invoiceNo("INV-1").customer(customer).balanceAmount(balance).build();
    }
}
