package com.billing.dto.notification;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SmsProviderFieldResponse {
    private String key;
    private String label;
    private String type;
    private boolean required;
    private boolean sensitive;
    private boolean encrypted;
    private String placeholder;
    private String helpText;
    private String defaultValue;
}
