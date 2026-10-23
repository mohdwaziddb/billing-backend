package com.billing.service;

import com.billing.dto.invoice.InvoiceItemRequest;
import com.billing.dto.invoice.InvoiceRequest;
import com.billing.dto.payment.PaymentRequest;
import com.billing.entity.Company;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.Payment;
import com.billing.exception.BadRequestException;
import com.billing.repository.InvoiceRepository;
import com.billing.repository.PaymentRepository;
import com.billing.service.payment.PaymentRecordingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAndInvoiceRulesTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private CustomerService customerService;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PaymentModeMasterService paymentModeMasterService;
    @Mock
    private AuditNameResolver auditNameResolver;
    @Mock
    private InvoiceRepository invoiceRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        // Common recording path real chalao (mocks ke upar) taaki split/balance rules test hon.
        PaymentRecordingService paymentRecordingService = new PaymentRecordingService(
                paymentRepository, customerService, invoiceService, invoiceRepository, auditLogService);
        paymentService = new PaymentService(paymentRepository, accessControlService, customerService, invoiceService,
                auditLogService, paymentModeMasterService, auditNameResolver, invoiceRepository, paymentRecordingService);
    }

    @Test
    void unlinkedPaymentIsSplitAcrossOldestOpenInvoices() {
        Company company = Company.builder().id(1L).build();
        Customer customer = Customer.builder().id(5L).name("Cust").build();
        Invoice oldest = Invoice.builder().id(10L).customer(customer).balanceAmount(new BigDecimal("100.00")).build();
        Invoice newer = Invoice.builder().id(11L).customer(customer).balanceAmount(new BigDecimal("50.00")).build();
        when(accessControlService.getCurrentCompany("u")).thenReturn(company);
        when(customerService.getCustomerOrThrow(company, 5L)).thenReturn(customer);
        when(paymentModeMasterService.requireActiveOfflineModeCode(eq(company), anyString())).thenReturn("CASH");
        when(invoiceRepository.lockOpenInvoicesOldestFirst(customer)).thenReturn(List.of(oldest, newer));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));

        PaymentRequest request = new PaymentRequest();
        request.setCustomerId(5L);
        request.setAmount(new BigDecimal("120.00"));
        request.setPaymentDate(LocalDate.of(2026, 10, 22));
        request.setMode("CASH");

        paymentService.create("u", request);

        // Pehle invoice par poora 100, doosre par baaki 20; kuch bhi unlinked nahi.
        verify(invoiceService).applyPayment(oldest, new BigDecimal("100.00"));
        verify(invoiceService).applyPayment(newer, new BigDecimal("20.00"));
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).save(saved.capture());
        assertEquals(oldest, saved.getAllValues().get(0).getInvoice());
        assertEquals(newer, saved.getAllValues().get(1).getInvoice());
        // Customer balance har invoice part par ghatta hai (total 120).
        verify(customerService).decreaseBalance(customer, new BigDecimal("100.00"));
        verify(customerService).decreaseBalance(customer, new BigDecimal("20.00"));
    }

    @Test
    void amountBeyondOpenInvoicesStaysUnlinked() {
        Company company = Company.builder().id(1L).build();
        Customer customer = Customer.builder().id(5L).name("Cust").build();
        Invoice only = Invoice.builder().id(10L).customer(customer).balanceAmount(new BigDecimal("30.00")).build();
        when(accessControlService.getCurrentCompany("u")).thenReturn(company);
        when(customerService.getCustomerOrThrow(company, 5L)).thenReturn(customer);
        when(paymentModeMasterService.requireActiveOfflineModeCode(eq(company), anyString())).thenReturn("CASH");
        when(invoiceRepository.lockOpenInvoicesOldestFirst(customer)).thenReturn(List.of(only));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));

        PaymentRequest request = new PaymentRequest();
        request.setCustomerId(5L);
        request.setAmount(new BigDecimal("50.00"));
        request.setPaymentDate(LocalDate.of(2026, 10, 22));
        request.setMode("CASH");

        paymentService.create("u", request);

        // 30 invoice par, 20 opening balance ke liye unlinked (pehle jaisa).
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).save(saved.capture());
        assertEquals(new BigDecimal("30.00"), saved.getAllValues().get(0).getAmount());
        assertEquals(new BigDecimal("20.00"), saved.getAllValues().get(1).getAmount());
        assertNull(saved.getAllValues().get(1).getInvoice());
    }

    @Test
    void paymentWithoutDateIsRejected() {
        Company company = Company.builder().id(1L).build();
        Customer customer = Customer.builder().id(5L).name("Cust").build();
        when(accessControlService.getCurrentCompany("u")).thenReturn(company);
        when(customerService.getCustomerOrThrow(company, 5L)).thenReturn(customer);
        PaymentRequest request = new PaymentRequest();
        request.setCustomerId(5L);
        request.setAmount(new BigDecimal("10.00"));

        assertThrows(BadRequestException.class, () -> paymentService.create("u", request));
    }

    @Test
    void invalidInvoiceRequestsAreRejectedBeforeAnyWrite() {
        InvoiceService service = new InvoiceService(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThrows(BadRequestException.class, () -> service.create("u", invoice(null, 1, "10.00")));
        assertThrows(BadRequestException.class, () -> service.create("u", invoice(LocalDate.now(), 0, "10.00")));
        assertThrows(BadRequestException.class, () -> service.create("u", invoice(LocalDate.now(), null, "10.00")));
        assertThrows(BadRequestException.class, () -> service.create("u", invoice(LocalDate.now(), 1, "-1.00")));
        InvoiceRequest emptyItems = invoice(LocalDate.now(), 1, "10.00");
        emptyItems.setItems(List.of());
        assertThrows(BadRequestException.class, () -> service.create("u", emptyItems));
        InvoiceRequest negativeDiscount = invoice(LocalDate.now(), 1, "10.00");
        negativeDiscount.setDiscountAmount(new BigDecimal("-5"));
        assertThrows(BadRequestException.class, () -> service.create("u", negativeDiscount));
    }

    private static InvoiceRequest invoice(LocalDate date, Integer qty, String price) {
        InvoiceItemRequest item = new InvoiceItemRequest();
        item.setProductId(1L);
        item.setQty(qty);
        item.setPrice(new BigDecimal(price));
        InvoiceRequest request = new InvoiceRequest();
        request.setCustomerId(5L);
        request.setInvoiceDate(date);
        request.setItems(List.of(item));
        return request;
    }
}
