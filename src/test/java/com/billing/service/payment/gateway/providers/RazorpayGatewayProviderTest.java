package com.billing.service.payment.gateway.providers;

import com.billing.entity.PaymentGatewaySetting;
import com.billing.entity.PaymentTransaction;
import com.billing.entity.enums.PaymentGatewayType;
import com.billing.entity.enums.PaymentTransactionStatus;
import com.billing.exception.BadRequestException;
import com.billing.service.payment.gateway.PaymentGatewayResolvedSettings;
import com.billing.service.payment.gateway.dto.GatewayWebhookEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RazorpayGatewayProviderTest {

    private static final String KEY_SECRET = "test_key_secret";
    private static final String WEBHOOK_SECRET = "whsec_123";

    private final RazorpayGatewayProvider provider = new RazorpayGatewayProvider(new ObjectMapper());

    @Test
    void checkoutSignatureMatchesRazorpayFormula() {
        String expected = hmac("order_ABC|pay_XYZ", KEY_SECRET);

        assertTrue(RazorpayGatewayProvider.isValidCheckoutSignature("order_ABC", "pay_XYZ", expected, KEY_SECRET));
        assertTrue(RazorpayGatewayProvider.isValidCheckoutSignature("order_ABC", "pay_XYZ", expected.toUpperCase(), KEY_SECRET));
        assertFalse(RazorpayGatewayProvider.isValidCheckoutSignature("order_ABC", "pay_OTHER", expected, KEY_SECRET));
        assertFalse(RazorpayGatewayProvider.isValidCheckoutSignature("order_ABC", "pay_XYZ", expected, "wrong_secret"));
        assertFalse(RazorpayGatewayProvider.isValidCheckoutSignature("order_ABC", "pay_XYZ", null, KEY_SECRET));
    }

    @Test
    void verifyCheckoutRejectsTamperedSignatureAndForeignOrder() {
        PaymentTransaction transaction = PaymentTransaction.builder().txnRef("PTX1").gatewayOrderId("order_ABC").build();
        PaymentGatewayResolvedSettings settings = settings(true);

        assertThrows(BadRequestException.class, () -> provider.verifyCheckout(settings, transaction, Map.of(
                "razorpay_order_id", "order_ABC",
                "razorpay_payment_id", "pay_XYZ",
                "razorpay_signature", "deadbeef")));
        // Doosre transaction ka order (sahi signature ke saath bhi) reject.
        assertThrows(BadRequestException.class, () -> provider.verifyCheckout(settings, transaction, Map.of(
                "razorpay_order_id", "order_OTHER",
                "razorpay_payment_id", "pay_XYZ",
                "razorpay_signature", hmac("order_OTHER|pay_XYZ", KEY_SECRET))));
    }

    @Test
    void webhookPaymentCapturedIsNormalized() {
        String body = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_1","order_id":"order_1","status":"captured","method":"upi","amount":12550,"notes":{"txn_ref":"PTX9"}}}}}""";

        GatewayWebhookEvent event = provider.parseWebhook(settings(true), body, Map.of("X-Razorpay-Signature", hmac(body, WEBHOOK_SECRET)));

        assertEquals("order_1", event.gatewayOrderId());
        assertEquals("PTX9", event.txnRef());
        assertEquals(PaymentTransactionStatus.SUCCESS, event.result().status());
        assertEquals("pay_1", event.result().gatewayPaymentId());
        assertEquals("upi", event.result().method());
        assertEquals(new BigDecimal("125.50"), event.result().amount());
    }

    @Test
    void webhookPaymentLinkPaidUsesReferenceId() {
        String body = """
                {"event":"payment_link.paid","payload":{"payment_link":{"entity":{"id":"plink_1","reference_id":"PTX5","status":"paid"}},"payment":{"entity":{"id":"pay_2","status":"captured","method":"card","amount":100000}}}}""";

        GatewayWebhookEvent event = provider.parseWebhook(settings(true), body, Map.of("x-razorpay-signature", hmac(body, WEBHOOK_SECRET)));

        assertEquals("plink_1", event.gatewayLinkId());
        assertEquals("PTX5", event.txnRef());
        assertEquals(PaymentTransactionStatus.SUCCESS, event.result().status());
        assertEquals(new BigDecimal("1000.00"), event.result().amount());
    }

    @Test
    void webhookFailedAndUnknownEvents() {
        String failed = """
                {"event":"payment.failed","payload":{"payment":{"entity":{"id":"pay_3","order_id":"order_3","status":"failed","error_description":"Bank declined","amount":500}}}}""";
        GatewayWebhookEvent failedEvent = provider.parseWebhook(settings(true), failed, Map.of("X-Razorpay-Signature", hmac(failed, WEBHOOK_SECRET)));
        assertEquals(PaymentTransactionStatus.FAILED, failedEvent.result().status());
        assertEquals("Bank declined", failedEvent.result().failureReason());

        String unknown = "{\"event\":\"refund.created\",\"payload\":{}}";
        GatewayWebhookEvent unknownEvent = provider.parseWebhook(settings(true), unknown, Map.of("X-Razorpay-Signature", hmac(unknown, WEBHOOK_SECRET)));
        assertNull(unknownEvent.result());
    }

    @Test
    void webhookWithBadOrMissingSignatureIsRejected() {
        String body = "{\"event\":\"payment.captured\",\"payload\":{}}";

        assertThrows(BadRequestException.class, () -> provider.parseWebhook(settings(true), body, Map.of("X-Razorpay-Signature", hmac(body, "other"))));
        assertThrows(BadRequestException.class, () -> provider.parseWebhook(settings(true), body, Map.of()));
        // Body ek byte bhi badla to signature fail.
        String signature = hmac(body, WEBHOOK_SECRET);
        assertThrows(BadRequestException.class, () -> provider.parseWebhook(settings(true), body + " ", Map.of("X-Razorpay-Signature", signature)));
    }

    @Test
    void keyModeMustMatchTestMode() {
        assertThrows(BadRequestException.class, () -> provider.validateConfiguration(new PaymentGatewayResolvedSettings(
                setting(true), Map.of("keyId", "rzp_live_abc", "keySecret", KEY_SECRET))));
        assertThrows(BadRequestException.class, () -> provider.validateConfiguration(new PaymentGatewayResolvedSettings(
                setting(false), Map.of("keyId", "rzp_test_abc", "keySecret", KEY_SECRET))));
        assertThrows(BadRequestException.class, () -> provider.validateConfiguration(new PaymentGatewayResolvedSettings(
                setting(true), Map.of("keyId", "rzp_test_abc"))));
        provider.validateConfiguration(settings(true));
    }

    @Test
    void amountIsConvertedToPaise() {
        assertEquals(12550L, RazorpayGatewayProvider.toPaise(new BigDecimal("125.5")));
        assertEquals(100L, RazorpayGatewayProvider.toPaise(new BigDecimal("1")));
        assertEquals(99999L, RazorpayGatewayProvider.toPaise(new BigDecimal("999.99")));
    }

    private static PaymentGatewayResolvedSettings settings(boolean testMode) {
        return new PaymentGatewayResolvedSettings(setting(testMode), Map.of(
                "keyId", testMode ? "rzp_test_abc" : "rzp_live_abc",
                "keySecret", KEY_SECRET,
                "webhookSecret", WEBHOOK_SECRET));
    }

    private static PaymentGatewaySetting setting(boolean testMode) {
        return PaymentGatewaySetting.builder().gatewayType(PaymentGatewayType.RAZORPAY).testMode(testMode).active(true).build();
    }

    // Test ka apna HMAC (provider ke helper par depend nahi).
    private static String hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
