package com.billing.dto.payment.gateway;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PaymentGatewayMetadataResponse {
    private String gatewayType;
    private String gatewayName;
    private String description;
    private boolean supportsCheckout;
    private boolean supportsPaymentLink;
    private String dashboardUrl;
    private List<String> webhookEvents;
    private List<PaymentGatewayFieldResponse> fields;
}
