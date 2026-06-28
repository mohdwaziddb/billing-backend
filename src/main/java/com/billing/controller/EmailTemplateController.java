package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.email.EmailLogResponse;
import com.billing.dto.email.EmailPreviewResponse;
import com.billing.dto.email.EmailRenderRequest;
import com.billing.dto.email.EmailSendRequest;
import com.billing.dto.email.EmailTemplateRequest;
import com.billing.dto.email.EmailTemplateResponse;
import com.billing.dto.email.NotificationChannelResponse;
import com.billing.security.RequiresPermission;
import com.billing.service.EmailTemplateService;
import com.billing.service.NotificationChannelService;
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
@RequestMapping("/api/v1/email-templates")
@RequiredArgsConstructor
public class EmailTemplateController {

    private final EmailTemplateService emailTemplateService;
    private final NotificationChannelService notificationChannelService;

    @GetMapping
    @RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<EmailTemplateResponse>>> page(Authentication authentication,
                                                                                 @RequestParam(required = false) String search,
                                                                                 @RequestParam(required = false) Boolean active,
                                                                                 @RequestParam(defaultValue = "0") int page,
                                                                                 @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Email templates fetched successfully",
                emailTemplateService.page(authentication.getName(), search, active, page, size)));
    }

    @GetMapping("/active")
    @RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<List<EmailTemplateResponse>>> activeTemplates(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Active email templates fetched successfully",
                emailTemplateService.activeTemplates(authentication.getName())));
    }

    @GetMapping("/variables")
    @RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<Map<String, String>>> variables() {
        return ResponseEntity.ok(ApiResponse.success("Template variables fetched successfully", emailTemplateService.variables()));
    }

    @GetMapping("/channels")
    @RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<List<NotificationChannelResponse>>> channels(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Notification channels fetched successfully",
                notificationChannelService.activeChannels(authentication.getName())));
    }

    @GetMapping("/{templateId}")
    @RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> get(Authentication authentication, @PathVariable Long templateId) {
        return ResponseEntity.ok(ApiResponse.success("Email template fetched successfully",
                emailTemplateService.get(authentication.getName(), templateId)));
    }

    @PostMapping
    @RequiresPermission(menu = "EMAIL_TEMPLATES", action = "ADD")
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> create(Authentication authentication,
                                                                     @Valid @RequestBody EmailTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Email template created successfully",
                emailTemplateService.create(authentication.getName(), request)));
    }

    @PutMapping("/{templateId}")
    @RequiresPermission(menu = "EMAIL_TEMPLATES", action = "EDIT")
    public ResponseEntity<ApiResponse<EmailTemplateResponse>> update(Authentication authentication,
                                                                     @PathVariable Long templateId,
                                                                     @Valid @RequestBody EmailTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Email template updated successfully",
                emailTemplateService.update(authentication.getName(), templateId, request)));
    }

    @DeleteMapping("/{templateId}")
    @RequiresPermission(menu = "EMAIL_TEMPLATES", action = "DELETE")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long templateId) {
        emailTemplateService.delete(authentication.getName(), templateId);
        return ResponseEntity.ok(ApiResponse.success("Email template deleted successfully", Map.of("status", "ok")));
    }

    @PostMapping("/{templateId}/preview")
    @RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<EmailPreviewResponse>> preview(Authentication authentication,
                                                                     @PathVariable Long templateId,
                                                                     @RequestBody(required = false) EmailRenderRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Email preview rendered successfully",
                emailTemplateService.preview(authentication.getName(), templateId, request)));
    }

    @PostMapping("/send")
    @RequiresPermission(menu = "EMAIL_TEMPLATES", action = "EMAIL_SEND")
    public ResponseEntity<ApiResponse<EmailLogResponse>> send(Authentication authentication,
                                                              @Valid @RequestBody EmailSendRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Email sent successfully",
                emailTemplateService.send(authentication.getName(), request)));
    }

    @GetMapping("/logs")
    @RequiresPermission(menu = "EMAIL_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<EmailLogResponse>>> logs(Authentication authentication,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Email logs fetched successfully",
                emailTemplateService.logs(authentication.getName(), page, size)));
    }
}
