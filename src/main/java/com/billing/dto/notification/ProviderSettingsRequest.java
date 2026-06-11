package com.billing.dto.notification;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProviderSettingsRequest {
    private Long id;
    private String providerName;
    private String senderEmail;
    private String awsAccessKey;
    private String awsSecretKey;
    private String awsRegion;
    private String apiUrl;
    private String username;
    private String password;
    private String senderId;
    private String channelName;
    private Boolean active;
}
