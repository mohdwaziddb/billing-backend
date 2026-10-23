package com.billing.dto.payment.gateway;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class PaymentGatewaySettingResponse {
    private Long id;
    private String gatewayType;
    private String displayName;
    private boolean testMode;
    private boolean active;
    private boolean defaultGateway;
    // Secret fields "****abcd" masked aate hain; wapas "****…" bhejo to purani value rahti hai.
    private Map<String, String> config;
    private String webhookUrl;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
