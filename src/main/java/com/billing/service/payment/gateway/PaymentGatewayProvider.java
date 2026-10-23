package com.billing.service.payment.gateway;

import com.billing.dto.payment.gateway.PaymentGatewayMetadataResponse;
import com.billing.entity.PaymentTransaction;
import com.billing.entity.enums.PaymentGatewayType;
import com.billing.service.payment.gateway.dto.GatewayHealthResult;
import com.billing.service.payment.gateway.dto.GatewayLinkRequest;
import com.billing.service.payment.gateway.dto.GatewayLinkResult;
import com.billing.service.payment.gateway.dto.GatewayOrderRequest;
import com.billing.service.payment.gateway.dto.GatewayOrderResult;
import com.billing.service.payment.gateway.dto.GatewayPaymentResult;
import com.billing.service.payment.gateway.dto.GatewayWebhookEvent;

import java.util.Map;

/**
 * Har payment gateway (Razorpay, aage Cashfree / PhonePe / PayU …) ka common
 * contract. Core flow (OnlinePaymentService) sirf ye interface aur normalized
 * DTOs dekhta hai — gateway-specific JSON adapter ke andar hi rehta hai.
 * Naya gateway = PaymentGatewayType me value + is interface ka ek @Component.
 */
public interface PaymentGatewayProvider {

    PaymentGatewayType gatewayType();

    /** Settings form ke fields + capabilities (frontend isi se form banata hai). */
    PaymentGatewayMetadataResponse metadata();

    void validateConfiguration(PaymentGatewayResolvedSettings settings);

    GatewayHealthResult testConnection(PaymentGatewayResolvedSettings settings);

    /** Checkout (popup) ke liye order banata hai. */
    GatewayOrderResult createOrder(PaymentGatewayResolvedSettings settings, GatewayOrderRequest request);

    /** Customer ko bhejne layak payment link banata hai. */
    GatewayLinkResult createPaymentLink(PaymentGatewayResolvedSettings settings, GatewayLinkRequest request);

    /** Checkout success callback verify (signature) + gateway se actual status. */
    GatewayPaymentResult verifyCheckout(PaymentGatewayResolvedSettings settings, PaymentTransaction transaction, Map<String, Object> callbackPayload);

    /** Webhook signature verify karke normalized event; signature galat ho to exception. */
    GatewayWebhookEvent parseWebhook(PaymentGatewayResolvedSettings settings, String rawBody, Map<String, String> headers);

    /** Reconciliation: gateway se transaction ka current status. */
    GatewayPaymentResult fetchStatus(PaymentGatewayResolvedSettings settings, PaymentTransaction transaction);

    /** Customer ke paas link ho to use band karna (best effort). */
    default void cancelPaymentLink(PaymentGatewayResolvedSettings settings, PaymentTransaction transaction) {
    }
}
