package com.billing.service;

import com.billing.dto.paymentmode.PaymentModeRequest;
import com.billing.entity.Company;
import com.billing.entity.PaymentModeMaster;
import com.billing.entity.enums.PaymentModeType;
import com.billing.exception.BadRequestException;
import com.billing.repository.PaymentModeMasterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentModeRulesTest {

    @Mock
    private PaymentModeMasterRepository paymentModeMasterRepository;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AuditNameResolver auditNameResolver;

    @InjectMocks
    private PaymentModeMasterService paymentModeMasterService;

    private final Company company = Company.builder().id(1L).build();

    @Test
    void onlineModeCannotBeRecordedManually() {
        when(paymentModeMasterRepository.findByCompanyAndModeCodeIgnoreCaseAndActiveTrue(company, "ONLINE"))
                .thenReturn(Optional.of(mode("ONLINE", PaymentModeType.ONLINE)));

        assertThrows(BadRequestException.class, () -> paymentModeMasterService.requireActiveOfflineModeCode(company, "Online"));
    }

    @Test
    void offlineAndLegacyModesAreAllowed() {
        when(paymentModeMasterRepository.findByCompanyAndModeCodeIgnoreCaseAndActiveTrue(company, "UPI"))
                .thenReturn(Optional.of(mode("UPI", PaymentModeType.OFFLINE)));
        // Purani rows: mode_type null = OFFLINE.
        when(paymentModeMasterRepository.findByCompanyAndModeCodeIgnoreCaseAndActiveTrue(company, "CASH"))
                .thenReturn(Optional.of(mode("CASH", null)));

        assertEquals("UPI", paymentModeMasterService.requireActiveOfflineModeCode(company, "upi"));
        assertEquals("CASH", paymentModeMasterService.requireActiveOfflineModeCode(company, "Cash"));
    }

    @Test
    void onlyOnlineCodeCanBeOnlineType() {
        when(accessControlService.requireOwnerCompany("owner")).thenReturn(company);
        PaymentModeRequest cashAsOnline = request("Cash Counter", "ONLINE");

        assertThrows(BadRequestException.class, () -> paymentModeMasterService.create("owner", cashAsOnline));
        verify(paymentModeMasterRepository, never()).save(any());
    }

    @Test
    void typeIsDerivedFromCodeWhenNotSent() {
        when(accessControlService.requireOwnerCompany("owner")).thenReturn(company);
        when(paymentModeMasterRepository.save(any(PaymentModeMaster.class))).thenAnswer(call -> call.getArgument(0));

        assertEquals("OFFLINE", paymentModeMasterService.create("owner", request("PhonePe QR", null)).getModeType());
        assertEquals("ONLINE", paymentModeMasterService.create("owner", request("Online", null)).getModeType());
    }

    @Test
    void onlineModeCannotBeRenamedOrMadeOffline() {
        PaymentModeMaster online = mode("ONLINE", PaymentModeType.ONLINE);
        online.setId(9L);
        when(accessControlService.requireOwnerCompany("owner")).thenReturn(company);
        when(paymentModeMasterRepository.findById(9L)).thenReturn(Optional.of(online));

        assertThrows(BadRequestException.class, () -> paymentModeMasterService.update("owner", 9L, request("Razorpay", null)));
        assertThrows(BadRequestException.class, () -> paymentModeMasterService.update("owner", 9L, request("Online", "OFFLINE")));
        verify(paymentModeMasterRepository, never()).save(any());
    }

    private static PaymentModeRequest request(String name, String modeType) {
        PaymentModeRequest request = new PaymentModeRequest();
        request.setModeName(name);
        request.setActive(true);
        request.setModeType(modeType);
        return request;
    }

    private static PaymentModeMaster mode(String code, PaymentModeType type) {
        return PaymentModeMaster.builder().modeName(code).modeCode(code).active(true).modeType(type).build();
    }
}
