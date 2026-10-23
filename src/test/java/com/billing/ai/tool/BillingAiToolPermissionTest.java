package com.billing.ai.tool;

import com.billing.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingAiToolPermissionTest {

    @Mock
    private AiToolGuard aiToolGuard;
    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private BillingAiToolService toolService;

    @Test
    void userWithoutPaymentPermissionCannotRecordPaymentViaChat() {
        when(aiToolGuard.allowed("PAYMENTS", "ADD")).thenReturn(false);
        when(aiToolGuard.denied()).thenReturn("DENIED");

        String reply = toolService.createPayment(1L, new BigDecimal("100"), "cash", null, null, null);

        assertEquals("DENIED", reply);
        verifyNoInteractions(paymentService);
    }

    @Test
    void everyToolHasAPermissionRule() {
        // Naya @Tool bina rule ke add hua to wo deny hoga (fail-closed); ye test yaad dilata hai.
        long tools = java.util.Arrays.stream(BillingAiToolService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(org.springframework.ai.tool.annotation.Tool.class))
                .map(method -> method.getAnnotation(org.springframework.ai.tool.annotation.Tool.class).name())
                .peek(name -> assertTrue(AiToolPermissions.BY_TOOL.containsKey(name), "No permission rule for tool " + name))
                .count();
        assertTrue(tools > 0);
    }
}
