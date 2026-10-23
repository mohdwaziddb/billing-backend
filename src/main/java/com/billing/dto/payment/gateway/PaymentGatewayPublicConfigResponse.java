package com.billing.dto.payment.gateway;

import lombok.Builder;
import lombok.Getter;

/** Billing screens ke liye: online payment on hai ya nahi, kaunsa gateway. */
@Getter
@Builder
public class PaymentGatewayPublicConfigResponse {
    private boolean onlineEnabled;
    private String gatewayType;
    private String gatewayName;
    private boolean testMode;
    private boolean supportsCheckout;
    private boolean supportsPaymentLink;
    private String onlineModeCode;
}
