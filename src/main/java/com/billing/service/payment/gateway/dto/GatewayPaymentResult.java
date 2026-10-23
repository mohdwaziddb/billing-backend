package com.billing.service.payment.gateway.dto;

import com.billing.entity.enums.PaymentTransactionStatus;

import java.math.BigDecimal;

/** Gateway se aaya normalized payment status. amount rupees me (null = pata nahi). */
public record GatewayPaymentResult(PaymentTransactionStatus status,
                                   String gatewayPaymentId,
                                   String method,
                                   BigDecimal amount,
                                   String failureReason,
                                   String rawResponse) {

    public static GatewayPaymentResult pending(String rawResponse) {
        return new GatewayPaymentResult(PaymentTransactionStatus.PENDING, null, null, null, null, rawResponse);
    }
}
