package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyCreateRequest;
import com.billing.dto.platformadmin.PlatformAdminCompanyDetailsResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyOverviewResponse;
import com.billing.dto.platformadmin.PlatformAdminCompanyResponse;
import com.billing.dto.platformadmin.PlatformAdminDashboardResponse;
import com.billing.dto.platformadmin.PlatformAdminSettingsRequest;
import com.billing.dto.platformadmin.PlatformAdminSettingsResponse;
import com.billing.service.PlatformAdminService;
import com.billing.dto.notification.EmailProviderTestRequest;
import com.billing.dto.notification.ProviderSettingsRequest;
import com.billing.dto.notification.ProviderSettingsResponse;
import com.billing.dto.notification.SmsProviderTestRequest;
import com.billing.dto.notification.WhatsAppProviderTestRequest;
import com.billing.service.NotificationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin")
@RequiredArgsConstructor
@PreAuthorize("principal instanceof T(com.billing.security.PlatformAdminPrincipal)")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;
    private final NotificationSettingsService notificationSettingsService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<PlatformAdminDashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success("Platform dashboard fetched successfully", platformAdminService.dashboard()));
    }

    @GetMapping("/companies")
    public ResponseEntity<ApiResponse<PageResponse<PlatformAdminCompanyResponse>>> companies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Companies fetched successfully",
                platformAdminService.companies(page, size, search, active)));
    }

    @GetMapping("/companies/overview")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyOverviewResponse>> companyOverview(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Company overview fetched successfully",
                platformAdminService.companyOverview(search, active)));
    }

    @PostMapping("/companies")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyResponse>> createCompany(
            @Valid @RequestBody PlatformAdminCompanyCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Company created successfully",
                platformAdminService.createCompany(request)));
    }

    @PostMapping("/companies/{companyId}/activate")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyResponse>> activateCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.success("Company activated successfully",
                platformAdminService.activateCompany(companyId)));
    }

    @PostMapping("/companies/{companyId}/deactivate")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyResponse>> deactivateCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.success("Company deactivated successfully",
                platformAdminService.deactivateCompany(companyId)));
    }

    @PostMapping("/companies/{companyId}/chatbot/enable")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyResponse>> enableCompanyChatbot(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.success("Company chatbot enabled successfully",
                platformAdminService.setCompanyChatbotEnabled(companyId, true)));
    }

    @PostMapping("/companies/{companyId}/chatbot/disable")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyResponse>> disableCompanyChatbot(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.success("Company chatbot disabled successfully",
                platformAdminService.setCompanyChatbotEnabled(companyId, false)));
    }

    @GetMapping("/companies/{companyId}")
    public ResponseEntity<ApiResponse<PlatformAdminCompanyDetailsResponse>> companyDetails(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.success("Company details fetched successfully",
                platformAdminService.companyDetails(companyId)));
    }

    @GetMapping("/companies/{companyId}/communication/email-settings")
    public ResponseEntity<ApiResponse<java.util.List<ProviderSettingsResponse>>> emailSettings(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.success("Email settings fetched successfully",
                notificationSettingsService.emailSettingsForCompany(companyId)));
    }

    @PostMapping("/companies/{companyId}/communication/email-settings")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> createEmailSettings(@PathVariable Long companyId,
                                                                                     @RequestBody ProviderSettingsRequest request,
                                                                                     org.springframework.security.core.Authentication authentication) {
        request.setId(null);
        return ResponseEntity.ok(ApiResponse.success("Email settings saved successfully",
                notificationSettingsService.saveEmailSettingsForCompany(companyId, request, authentication.getName())));
    }

    @PutMapping("/companies/{companyId}/communication/email-settings/{id}")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> updateEmailSettings(@PathVariable Long companyId,
                                                                                     @PathVariable Long id,
                                                                                     @RequestBody ProviderSettingsRequest request,
                                                                                     org.springframework.security.core.Authentication authentication) {
        request.setId(id);
        return ResponseEntity.ok(ApiResponse.success("Email settings saved successfully",
                notificationSettingsService.saveEmailSettingsForCompany(companyId, request, authentication.getName())));
    }

    @PostMapping("/companies/{companyId}/communication/email-settings/test")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> testEmailSettings(@PathVariable Long companyId,
                                                                                   @RequestBody(required = false) EmailProviderTestRequest request,
                                                                                   org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Test email sent successfully",
                notificationSettingsService.sendTestEmailForCompany(companyId, request, authentication.getName())));
    }

    @GetMapping("/companies/{companyId}/communication/sms-settings")
    public ResponseEntity<ApiResponse<java.util.List<ProviderSettingsResponse>>> smsSettings(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.success("SMS settings fetched successfully",
                notificationSettingsService.smsSettingsForCompany(companyId)));
    }

    @PostMapping("/companies/{companyId}/communication/sms-settings")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> createSmsSettings(@PathVariable Long companyId,
                                                                                   @RequestBody ProviderSettingsRequest request,
                                                                                   org.springframework.security.core.Authentication authentication) {
        request.setId(null);
        return ResponseEntity.ok(ApiResponse.success("SMS settings saved successfully",
                notificationSettingsService.saveSmsSettingsForCompany(companyId, request, authentication.getName())));
    }

    @PutMapping("/companies/{companyId}/communication/sms-settings/{id}")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> updateSmsSettings(@PathVariable Long companyId,
                                                                                   @PathVariable Long id,
                                                                                   @RequestBody ProviderSettingsRequest request,
                                                                                   org.springframework.security.core.Authentication authentication) {
        request.setId(id);
        return ResponseEntity.ok(ApiResponse.success("SMS settings saved successfully",
                notificationSettingsService.saveSmsSettingsForCompany(companyId, request, authentication.getName())));
    }

    @PostMapping("/companies/{companyId}/communication/sms-settings/test")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> testSmsSettings(@PathVariable Long companyId,
                                                                                 @RequestBody(required = false) SmsProviderTestRequest request,
                                                                                 org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Test SMS sent successfully",
                notificationSettingsService.sendTestSmsForCompany(companyId, request, authentication.getName())));
    }

    @GetMapping("/companies/{companyId}/communication/whatsapp-settings")
    public ResponseEntity<ApiResponse<java.util.List<ProviderSettingsResponse>>> whatsAppSettings(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.success("WhatsApp settings fetched successfully",
                notificationSettingsService.whatsAppSettingsForCompany(companyId)));
    }

    @PostMapping("/companies/{companyId}/communication/whatsapp-settings")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> createWhatsAppSettings(@PathVariable Long companyId,
                                                                                        @RequestBody ProviderSettingsRequest request,
                                                                                        org.springframework.security.core.Authentication authentication) {
        request.setId(null);
        return ResponseEntity.ok(ApiResponse.success("WhatsApp settings saved successfully",
                notificationSettingsService.saveWhatsAppSettingsForCompany(companyId, request, authentication.getName())));
    }

    @PutMapping("/companies/{companyId}/communication/whatsapp-settings/{id}")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> updateWhatsAppSettings(@PathVariable Long companyId,
                                                                                        @PathVariable Long id,
                                                                                        @RequestBody ProviderSettingsRequest request,
                                                                                        org.springframework.security.core.Authentication authentication) {
        request.setId(id);
        return ResponseEntity.ok(ApiResponse.success("WhatsApp settings saved successfully",
                notificationSettingsService.saveWhatsAppSettingsForCompany(companyId, request, authentication.getName())));
    }

    @PostMapping("/companies/{companyId}/communication/whatsapp-settings/test")
    public ResponseEntity<ApiResponse<ProviderSettingsResponse>> testWhatsAppSettings(@PathVariable Long companyId,
                                                                                      @RequestBody(required = false) WhatsAppProviderTestRequest request,
                                                                                      org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("WhatsApp message sent successfully",
                notificationSettingsService.sendTestWhatsAppForCompany(companyId, request, authentication.getName())));
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<PlatformAdminSettingsResponse>> settings() {
        return ResponseEntity.ok(ApiResponse.success("Platform settings fetched successfully",
                platformAdminService.settings()));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<PlatformAdminSettingsResponse>> updateSettings(
            @RequestBody PlatformAdminSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Platform settings updated successfully",
                platformAdminService.updateSettings(request)));
    }
}
