package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.notification.EmailProviderTestRequest;
import com.billing.dto.notification.NotificationLogResponse;
import com.billing.dto.notification.NotificationSendRequest;
import com.billing.dto.notification.ProviderSettingsRequest;
import com.billing.dto.notification.ProviderSettingsResponse;
import com.billing.security.RequirePermission;
import com.billing.service.NotificationService;
import com.billing.service.NotificationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/logs")
    @RequirePermission(menu = "EMAIL_SETTINGS", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<NotificationLogResponse>>> logs(Authentication authentication,
                                                                                   @RequestParam(defaultValue = "0") int page,
                                                                                   @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Notification logs fetched successfully", notificationService.logs(authentication.getName(), page, size)));
    }

    @GetMapping("/email-settings")
    @RequirePermission(menu = "EMAIL_SETTINGS", action = "VIEW")
    public ResponseEntity<ApiResponse<List<ProviderSettingsResponse>>> emailSettings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Email settings fetched successfully", notificationSettingsService.emailSettings(authentication.getName())));
    }

    @PostMapping("/email-settings")
    @RequirePermission(menu = "EMAIL_SETTINGS", action = "ADD")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> createEmailSettings(Authentication authentication, @RequestBody ProviderSettingsRequest request) {
        request.setId(null);
        return ResponseEntity.ok(ApiResponse.success("Email settings saved successfully", notificationSettingsService.saveEmailSettings(authentication.getName(), request)));
    }

    @PutMapping("/email-settings/{id}")
    @RequirePermission(menu = "EMAIL_SETTINGS", action = "EDIT")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> saveEmailSettings(Authentication authentication, @PathVariable Long id, @RequestBody ProviderSettingsRequest request) {
        request.setId(id);
        return ResponseEntity.ok(ApiResponse.success("Email settings saved successfully", notificationSettingsService.saveEmailSettings(authentication.getName(), request)));
    }

    @PostMapping("/email-settings/test")
    @RequirePermission(menu = "EMAIL_SETTINGS", action = "VIEW")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> sendTestEmail(Authentication authentication, @RequestBody(required = false) EmailProviderTestRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Test email sent successfully", notificationSettingsService.sendTestEmail(authentication.getName(), request)));
    }

    @GetMapping("/sms-settings")
    @RequirePermission(menu = "SMS_SETTINGS", action = "VIEW")
    public ResponseEntity<ApiResponse<List<ProviderSettingsResponse>>> smsSettings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("SMS settings fetched successfully", notificationSettingsService.smsSettings(authentication.getName())));
    }

    @PostMapping("/sms-settings")
    @RequirePermission(menu = "SMS_SETTINGS", action = "ADD")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> createSmsSettings(Authentication authentication, @RequestBody ProviderSettingsRequest request) {
        request.setId(null);
        return ResponseEntity.ok(ApiResponse.success("SMS settings saved successfully", notificationSettingsService.saveSmsSettings(authentication.getName(), request)));
    }

    @PutMapping("/sms-settings/{id}")
    @RequirePermission(menu = "SMS_SETTINGS", action = "EDIT")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> saveSmsSettings(Authentication authentication, @PathVariable Long id, @RequestBody ProviderSettingsRequest request) {
        request.setId(id);
        return ResponseEntity.ok(ApiResponse.success("SMS settings saved successfully", notificationSettingsService.saveSmsSettings(authentication.getName(), request)));
    }
}
