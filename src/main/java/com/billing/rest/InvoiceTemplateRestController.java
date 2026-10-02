package com.billing.rest;

import com.billing.service.invoice.CompanyInvoiceSettingsService;
import com.billing.service.invoice.InvoiceTemplateRenderService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/invoice-templates")
@RequiredArgsConstructor
public class InvoiceTemplateRestController {

    private final InvoiceTemplateRenderService invoiceTemplateRenderService;
    private final CompanyInvoiceSettingsService companyInvoiceSettingsService;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "INVOICE_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> list(Authentication authentication) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", invoiceTemplateRenderService.list(authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/settings")
    @com.billing.security.RequiresPermission(menu = "INVOICE_TEMPLATES", action = "VIEW")
    public ResponseEntity<?> settings(Authentication authentication) {
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", companyInvoiceSettingsService.get(authentication.getName())), HttpStatus.OK);
    }

    @PutMapping("/settings")
    @com.billing.security.RequiresPermission(menu = "INVOICE_TEMPLATES", action = "CHANGE_DEFAULT")
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        param = SanitizeData.sanitizeMapObj(param);
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", companyInvoiceSettingsService.update(param, authentication.getName())), HttpStatus.OK);
    }

    @GetMapping("/{templateId}/preview")
    @com.billing.security.RequiresPermission(menu = "INVOICE_TEMPLATES", action = "PREVIEW")
    public ResponseEntity<?> preview(Authentication authentication, @PathVariable String templateId, @RequestParam(required = false) Map<String, Object> param, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        String sanitizedTemplateId = DataTypeUtility.stringValue(templateId);
        if (sanitizedTemplateId.length() == 0) {
            sanitizedTemplateId = null;
        }
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", invoiceTemplateRenderService.previewTemplate(param, authentication.getName(), sanitizedTemplateId)), HttpStatus.OK);
    }

    @PostMapping("/{templateId}/preview")
    @com.billing.security.RequiresPermission(menu = "INVOICE_TEMPLATES", action = "PREVIEW")
    public ResponseEntity<?> previewWithOverrides(Authentication authentication, @PathVariable String templateId, @RequestBody(required = false) Map<String, Object> param, HttpServletRequest req) {
        param = SanitizeData.sanitizeMapObj(param);
        String sanitizedTemplateId = DataTypeUtility.stringValue(templateId);
        if (sanitizedTemplateId.length() == 0) {
            sanitizedTemplateId = null;
        }
        return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", invoiceTemplateRenderService.previewWithOverrides(param, authentication.getName(), sanitizedTemplateId)), HttpStatus.OK);
    }
}
