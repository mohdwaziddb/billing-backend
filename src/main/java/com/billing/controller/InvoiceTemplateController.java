package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.invoice.CompanyInvoiceSettingsRequest;
import com.billing.dto.invoice.CompanyInvoiceSettingsResponse;
import com.billing.dto.invoice.InvoiceRenderResponse;
import com.billing.dto.invoice.InvoiceTemplateMetadataResponse;
import com.billing.dto.invoice.InvoiceTemplatePreviewRequest;
import com.billing.security.RequiresPermission;
import com.billing.service.invoice.CompanyInvoiceSettingsService;
import com.billing.service.invoice.InvoiceTemplateRenderService;
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
@RequestMapping("/api/v1/invoice-templates")
@RequiredArgsConstructor
public class InvoiceTemplateController {

    private final InvoiceTemplateRenderService invoiceTemplateRenderService;
    private final CompanyInvoiceSettingsService companyInvoiceSettingsService;

    @GetMapping
    @RequiresPermission(menu = "INVOICE_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<List<InvoiceTemplateMetadataResponse>>> list(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Invoice templates fetched successfully", invoiceTemplateRenderService.list(authentication.getName())));
    }

    @GetMapping("/settings")
    @RequiresPermission(menu = "INVOICE_TEMPLATES", action = "VIEW")
    public ResponseEntity<ApiResponse<CompanyInvoiceSettingsResponse>> settings(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Invoice template settings fetched successfully", companyInvoiceSettingsService.get(authentication.getName())));
    }

    @PutMapping("/settings")
    @RequiresPermission(menu = "INVOICE_TEMPLATES", action = "CHANGE_DEFAULT")
    public ResponseEntity<ApiResponse<CompanyInvoiceSettingsResponse>> updateSettings(Authentication authentication,
                                                                                      @RequestBody CompanyInvoiceSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Invoice template settings updated successfully", companyInvoiceSettingsService.update(authentication.getName(), request)));
    }

    @GetMapping("/{templateId}/preview")
    @RequiresPermission(menu = "INVOICE_TEMPLATES", action = "PREVIEW")
    public ResponseEntity<ApiResponse<InvoiceRenderResponse>> preview(Authentication authentication,
                                                                      @PathVariable String templateId,
                                                                      @RequestParam(required = false) Long invoiceId) {
        return ResponseEntity.ok(ApiResponse.success("Invoice template preview rendered successfully", invoiceTemplateRenderService.previewTemplate(authentication.getName(), templateId, invoiceId)));
    }

    @PostMapping("/{templateId}/preview")
    @RequiresPermission(menu = "INVOICE_TEMPLATES", action = "PREVIEW")
    public ResponseEntity<ApiResponse<InvoiceRenderResponse>> previewWithOverrides(Authentication authentication,
                                                                                   @PathVariable String templateId,
                                                                                   @RequestBody(required = false) InvoiceTemplatePreviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Invoice template preview rendered successfully",
                invoiceTemplateRenderService.previewTemplate(authentication.getName(), templateId, request)));
    }
}
