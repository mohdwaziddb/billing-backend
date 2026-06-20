package com.billing.dto.notification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WhatsAppProviderTestRequest {
    private String mobileNumber;
    private String message;
}
