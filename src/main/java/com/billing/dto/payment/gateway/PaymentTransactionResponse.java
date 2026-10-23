package com.billing.dto.payment.gateway;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class PaymentTransactionResponse {
    private Long id;
    private String txnRef;
    private String gatewayType;
    private String channel;
    private String status;
    private Long invoiceId;
    private String invoiceNo;
    private Long customerId;
    private String customerName;
    private String customerMobile;
    private BigDecimal amount;
    private String currency;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String linkUrl;
    private String method;
    private String failureReason;
    private Long paymentId;
    private LocalDateTime settledAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private String createdBy;
    // Sirf initiate (CHECKOUT) response me: frontend checkout adapter ke options.
    private Map<String, Object> checkout;
}
