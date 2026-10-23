package com.billing.rest;

import com.billing.service.payment.OnlinePaymentService;
import com.billing.service.payment.PaymentGatewaySettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayRestControllerTest {

    @Mock
    private PaymentGatewaySettingService paymentGatewaySettingService;
    @Mock
    private OnlinePaymentService onlinePaymentService;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private PaymentGatewayRestController controller;

    @Test
    @SuppressWarnings("unchecked")
    void gatewaySecretsAreNotMangledBySanitizer() {
        when(authentication.getName()).thenReturn("owner");
        Map<String, Object> param = new HashMap<>();
        param.put("gatewayType", "RAZORPAY");
        param.put("displayName", "<b>Razorpay</b>");
        param.put("config", Map.of("keyId", "rzp_test_abc", "webhookSecret", "a<b>&c\"d'e"));

        controller.saveSettings(param, authentication);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(paymentGatewaySettingService).save(captor.capture(), eq("owner"));
        Map<String, Object> config = (Map<String, Object>) captor.getValue().get("config");
        assertEquals("a<b>&c\"d'e", config.get("webhookSecret"));
        assertEquals("rzp_test_abc", config.get("keyId"));
        // Baaki fields pehle jaise sanitize.
        assertEquals("Razorpay", captor.getValue().get("displayName"));
    }
}
