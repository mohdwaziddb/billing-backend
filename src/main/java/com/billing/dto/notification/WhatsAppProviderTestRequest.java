package com.billing.dto.notification;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class WhatsAppProviderTestRequest {
    private String mobileNumber;
    private String message;
    private String providerName;
    private String providerType;
    private String apiUrl;
    private Map<String, String> configValues;
}
