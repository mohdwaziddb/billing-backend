package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.notification.EmailProviderTestRequest;
import com.billing.dto.notification.NotificationLogResponse;
import com.billing.dto.notification.NotificationSendRequest;
import com.billing.dto.notification.ProviderSettingsRequest;
import com.billing.dto.notification.ProviderSettingsResponse;
import com.billing.dto.notification.SmsProviderTestRequest;
import com.billing.dto.notification.WhatsAppProviderTestRequest;
import com.billing.security.RequirePermission;
import com.billing.service.NotificationService;
import com.billing.service.NotificationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSettingsService notificationSettingsService;

    @PostMapping("/send")
    @RequirePermission(menu = "EMAIL_TEMPLATES", action = "EMAIL_SEND")
    public ResponseEntity<ApiResponse<List<NotificationLogResponse>>> send(Authentication authentication, @RequestBody NotificationSendRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Notification processed successfully", notificationService.sendNotification(authentication.getName(), request)));
    }

    @PostMapping("/send-email")
    @RequirePermission(menu = "EMAIL_TEMPLATES", action = "EMAIL_SEND")
    public ResponseEntity<ApiResponse<List<NotificationLogResponse>>> sendEmail(Authentication authentication, @RequestBody NotificationSendRequest request) {
        request.setChannel(com.billing.dto.notification.NotificationChannelType.EMAIL);
        return ResponseEntity.ok(ApiResponse.success("Email notification processed successfully", notificationService.sendNotification(authentication.getName(), request)));
    }

    @PostMapping("/send-sms")
    @RequirePermission(menu = "SMS_TEMPLATES", action = "SMS_SEND")
    public ResponseEntity<ApiResponse<List<NotificationLogResponse>>> sendSms(Authentication authentication, @RequestBody NotificationSendRequest request) {
        request.setChannel(com.billing.dto.notification.NotificationChannelType.SMS);
        return ResponseEntity.ok(ApiResponse.success("SMS notification processed successfully", notificationService.sendNotification(authentication.getName(), request)));
    }

    @PostMapping("/send-whatsapp")
    @RequirePermission(menu = "COMMUNICATION", action = "WHATSAPP_SEND")
    public ResponseEntity<ApiResponse<List<NotificationLogResponse>>> sendWhatsApp(Authentication authentication, @RequestBody NotificationSendRequest request) {
        request.setChannel(com.billing.dto.notification.NotificationChannelType.WHATSAPP);
        return ResponseEntity.ok(ApiResponse.success("WhatsApp notification processed successfully", notificationService.sendNotification(authentication.getName(), request)));
    }

    @GetMapping("/logs")
    @RequirePermission(menu = "COMMUNICATION", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<NotificationLogResponse>>> logs(Authentication authentication,
                                                                                   @RequestParam(defaultValue = "0") int page,
                                                                                   @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Notification logs fetched successfully", notificationService.logs(authentication.getName(), page, size)));
    }

    @GetMapping("/email-settings")
    @RequirePermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<List<ProviderSettingsResponse>>> emailSettings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Email settings fetched successfully", notificationSettingsService.emailSettings(authentication.getName())));
    }

    @PostMapping("/email-settings")
    @RequirePermission(menu = "COMMUNICATION", action = "ADD")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> createEmailSettings(Authentication authentication, @RequestBody ProviderSettingsRequest request) {
        request.setId(null);
        return ResponseEntity.ok(ApiResponse.success("Email settings saved successfully", notificationSettingsService.saveEmailSettings(authentication.getName(), request)));
    }

    @PutMapping("/email-settings/{id}")
    @RequirePermission(menu = "COMMUNICATION", action = "EDIT")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> saveEmailSettings(Authentication authentication, @PathVariable Long id, @RequestBody ProviderSettingsRequest request) {
        request.setId(id);
        return ResponseEntity.ok(ApiResponse.success("Email settings saved successfully", notificationSettingsService.saveEmailSettings(authentication.getName(), request)));
    }

    @PostMapping("/email-settings/test")
    @RequirePermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> sendTestEmail(Authentication authentication, @RequestBody(required = false) EmailProviderTestRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Test email sent successfully", notificationSettingsService.sendTestEmail(authentication.getName(), request)));
    }

    @GetMapping("/sms-settings")
    @RequirePermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<List<ProviderSettingsResponse>>> smsSettings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("SMS settings fetched successfully", notificationSettingsService.smsSettings(authentication.getName())));
    }

    @PostMapping("/sms-settings")
    @RequirePermission(menu = "COMMUNICATION", action = "ADD")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> createSmsSettings(Authentication authentication, @RequestBody ProviderSettingsRequest request) {
        request.setId(null);
        return ResponseEntity.ok(ApiResponse.success("SMS settings saved successfully", notificationSettingsService.saveSmsSettings(authentication.getName(), request)));
    }

    @PutMapping("/sms-settings/{id}")
    @RequirePermission(menu = "COMMUNICATION", action = "EDIT")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> saveSmsSettings(Authentication authentication, @PathVariable Long id, @RequestBody ProviderSettingsRequest request) {
        request.setId(id);
        return ResponseEntity.ok(ApiResponse.success("SMS settings saved successfully", notificationSettingsService.saveSmsSettings(authentication.getName(), request)));
    }

    @PostMapping("/sms-settings/test")
    @RequirePermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> sendTestSms(Authentication authentication, @RequestBody(required = false) SmsProviderTestRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Test SMS sent successfully", notificationSettingsService.sendTestSms(authentication.getName(), request)));
    }

    @GetMapping("/whatsapp-settings")
    @RequirePermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<List<ProviderSettingsResponse>>> whatsAppSettings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("WhatsApp settings fetched successfully", notificationSettingsService.whatsAppSettings(authentication.getName())));
    }

    @PostMapping("/whatsapp-settings")
    @RequirePermission(menu = "COMMUNICATION", action = "ADD")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> createWhatsAppSettings(Authentication authentication, @RequestBody ProviderSettingsRequest request) {
        request.setId(null);
        return ResponseEntity.ok(ApiResponse.success("WhatsApp settings saved successfully", notificationSettingsService.saveWhatsAppSettings(authentication.getName(), request)));
    }

    @PutMapping("/whatsapp-settings/{id}")
    @RequirePermission(menu = "COMMUNICATION", action = "EDIT")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> saveWhatsAppSettings(Authentication authentication, @PathVariable Long id, @RequestBody ProviderSettingsRequest request) {
        request.setId(id);
        return ResponseEntity.ok(ApiResponse.success("WhatsApp settings saved successfully", notificationSettingsService.saveWhatsAppSettings(authentication.getName(), request)));
    }

    @PostMapping("/whatsapp-settings/test")
    @RequirePermission(menu = "COMMUNICATION", action = "VIEW")
    @PreAuthorize("denyAll()")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> sendTestWhatsApp(Authentication authentication, @RequestBody(required = false) WhatsAppProviderTestRequest request) {
        return ResponseEntity.ok(ApiResponse.success("WhatsApp message sent successfully", notificationSettingsService.sendTestWhatsApp(authentication.getName(), request)));
    }
}
