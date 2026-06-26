package com.billing.dto.notification;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class SmsProviderTestRequest {
    private String mobileNumber;
    private String providerName;
    private String providerType;
    private String apiUrl;
    private Map<String, String> configValues;
}
