package com.billing.service.payment.gateway;

import com.billing.dto.payment.gateway.PaymentGatewayFieldResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentGatewayFieldDefinition {
    private String key;
    private String label;
    private String type;
    private boolean required;
    private boolean sensitive;
    private String placeholder;
    private String helpText;

    public PaymentGatewayFieldResponse toResponse() {
        return PaymentGatewayFieldResponse.builder()
                .key(key)
                .label(label)
                .type(type)
                .required(required)
                .sensitive(sensitive)
                .placeholder(placeholder)
                .helpText(helpText)
                .build();
    }
}
