package com.billing.dto.notification;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WhatsAppProviderMetadataResponse {
    private String providerType;
    private String providerName;
    private boolean supportsBalance;
    private boolean supportsTemplates;
    private boolean supportsMedia;
    private List<WhatsAppProviderFieldResponse> fields;
}
