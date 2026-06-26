package com.billing.dto.notification;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class ProviderSettingsRequest {
    private Long id;
    private String providerName;
    private String senderEmail;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpPassword;
    private Boolean smtpTlsEnabled;
    private String awsAccessKey;
    private String awsSecretKey;
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
    private Boolean active;
}
