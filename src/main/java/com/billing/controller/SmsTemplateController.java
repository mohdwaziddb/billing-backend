package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.email.EmailPreviewResponse;
import com.billing.dto.email.EmailRenderRequest;
import com.billing.dto.notification.SmsTemplateRequest;
import com.billing.dto.notification.SmsTemplateResponse;
import com.billing.security.RequiresPermission;
import com.billing.service.EmailTemplateService;
import com.billing.service.SmsTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sms-templates")
@RequiredArgsConstructor
public class SmsTemplateController {

    private final SmsTemplateService smsTemplateService;
    private final EmailTemplateService emailTemplateService;

    @GetMapping
    @RequiresPermission(menu = "SMS_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<SmsTemplateResponse>>> page(Authentication authentication,
                                                                                @RequestParam(required = false) String search,
                                                                                @RequestParam(required = false) Boolean active,
                                                                                @RequestParam(defaultValue = "0") int page,
                                                                                @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("SMS templates fetched successfully", smsTemplateService.page(authentication.getName(), search, active, page, size)));
    }

    @GetMapping("/active")
    @RequiresPermission(menu = "SMS_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<List<SmsTemplateResponse>>> active(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Active SMS templates fetched successfully", smsTemplateService.activeTemplates(authentication.getName())));
    }

    @GetMapping("/variables")
    @RequiresPermission(menu = "SMS_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<Map<String, String>>> variables() {
        return ResponseEntity.ok(ApiResponse.success("Template variables fetched successfully", emailTemplateService.variables()));
    }

    @PostMapping
    @RequiresPermission(menu = "SMS_TEMPLATES", action = "ADD")
    public ResponseEntity<ApiResponse<SmsTemplateResponse>> create(Authentication authentication, @Valid @RequestBody SmsTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SMS template created successfully", smsTemplateService.create(authentication.getName(), request)));
    }

    @PutMapping("/{templateId}")
    @RequiresPermission(menu = "SMS_TEMPLATES", action = "EDIT")
    public ResponseEntity<ApiResponse<SmsTemplateResponse>> update(Authentication authentication, @PathVariable Long templateId, @Valid @RequestBody SmsTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SMS template updated successfully", smsTemplateService.update(authentication.getName(), templateId, request)));
    }

    @DeleteMapping("/{templateId}")
    @RequiresPermission(menu = "SMS_TEMPLATES", action = "DELETE")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long templateId) {
        smsTemplateService.delete(authentication.getName(), templateId);
        return ResponseEntity.ok(ApiResponse.success("SMS template deleted successfully", Map.of("status", "ok")));
    }

    @PostMapping("/{templateId}/preview")
    @RequiresPermission(menu = "SMS_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<EmailPreviewResponse>> preview(Authentication authentication, @PathVariable Long templateId, @RequestBody(required = false) EmailRenderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("SMS preview rendered successfully", smsTemplateService.preview(authentication.getName(), templateId, request)));
    }
}
