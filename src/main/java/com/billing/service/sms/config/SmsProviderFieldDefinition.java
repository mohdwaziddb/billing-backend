package com.billing.service.sms.config;

import com.billing.dto.notification.SmsProviderFieldResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SmsProviderFieldDefinition {
    private String key;
    private String label;
    private String type;
    private boolean required;
    private boolean sensitive;
    private boolean encrypted;
    private String placeholder;
    private String helpText;
    private String defaultValue;

    public SmsProviderFieldResponse toResponse() {
        return SmsProviderFieldResponse.builder()
                .key(key)
                .label(label)
                .type(type)
                .required(required)
                .sensitive(sensitive)
                .encrypted(encrypted)
                .placeholder(placeholder)
                .helpText(helpText)
                .defaultValue(defaultValue)
                .build();
    }
}
