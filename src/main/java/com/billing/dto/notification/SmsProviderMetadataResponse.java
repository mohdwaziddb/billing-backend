package com.billing.dto.notification;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SmsProviderMetadataResponse {
    private String providerType;
    private String providerName;
    private List<SmsProviderFieldResponse> fields;
}
