package com.billing.dto.notification;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ProviderSettingsResponse {
    private Long id;
    private String providerName;
    private String senderEmail;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private boolean smtpTlsEnabled;
    private String awsAccessKey;
    private String awsRegion;
    private String sendgridApiKey;
    private String apiUrl;
    private String providerType;
    private String authKey;
    private String senderId;
    private String templateId;
    private String whatsappNumber;
    private String senderName;
    private Map<String, String> configValues;
    private boolean active;
}
