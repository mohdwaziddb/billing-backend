package com.billing.dto.notification;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProviderSettingsResponse {
    private Long id;
    private String providerName;
    private String senderEmail;
    private String awsAccessKey;
    private String awsRegion;
    private String apiUrl;
    private String username;
    private String senderId;
    private String channelName;
    private boolean active;
}
