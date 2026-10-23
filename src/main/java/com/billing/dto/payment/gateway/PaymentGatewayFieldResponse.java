package com.billing.dto.payment.gateway;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentGatewayFieldResponse {
    private String key;
    private String label;
    private String type;
    private boolean required;
    private boolean sensitive;
    private String placeholder;
    private String helpText;
}
