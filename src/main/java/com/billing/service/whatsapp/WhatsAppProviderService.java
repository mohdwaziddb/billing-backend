package com.billing.service.whatsapp;

import com.billing.dto.notification.NotificationAttachmentRequest;
import com.billing.entity.Company;
import com.billing.entity.WhatsAppProviderSetting;

import java.util.List;
import java.util.Map;

public interface WhatsAppProviderService {

    String providerType();

    List<WhatsAppSendResult> sendMessage(Company company, WhatsAppProviderSetting settings, List<String> mobileNumbers, String message);

    List<WhatsAppSendResult> sendTemplate(Company company, WhatsAppProviderSetting settings, List<String> mobileNumbers, String templateName, Map<String, Object> variables);

    List<WhatsAppSendResult> sendMedia(Company company, WhatsAppProviderSetting settings, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments);

    List<WhatsAppSendResult> sendDocument(Company company, WhatsAppProviderSetting settings, List<String> mobileNumbers, String message, List<NotificationAttachmentRequest> attachments);

    WhatsAppSendResult testConnection(Company company, WhatsAppProviderSetting settings, String mobileNumber, String message);
}
