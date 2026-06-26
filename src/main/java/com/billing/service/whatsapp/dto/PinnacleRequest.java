package com.billing.service.whatsapp.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class PinnacleRequest {
    private String senderId;
    private String businessNumber;
    private String mobileNumber;
    private String type;
    private String message;
    private String templatePrefix;
    private boolean sandboxMode;
    private String status;
    private Map<String, Object> variables;
}
