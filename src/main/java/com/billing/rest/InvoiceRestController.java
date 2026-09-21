package com.billing.rest;

import com.billing.service.InvoiceService;
import com.billing.service.invoice.InvoiceTemplateRenderService;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceRestController {

    private final InvoiceService invoiceService;
    private final InvoiceTemplateRenderService invoiceTemplateRenderService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping
    @com.billing.security.RequiresPermission(menu = "INVOICES", action = "VIEW")
    public ResponseEntity<?> list(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", invoiceService.page(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{invoiceId}")
    @com.billing.security.RequiresPermission(menu = "INVOICES", action = "VIEW")
    public ResponseEntity<?> get(Authentication authentication, @PathVariable Long invoiceId) {
        try {
            invoiceId = DataTypeUtility.getForeignKeyValue(invoiceId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", invoiceService.get(authentication.getName(), invoiceId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{invoiceId}/render")
    @com.billing.security.RequiresPermission(menu = "INVOICES", action = "VIEW")
    public ResponseEntity<?> render(Authentication authentication, @PathVariable Long invoiceId, @RequestParam(required = false) Map<String, Object> param, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            invoiceId = DataTypeUtility.getForeignKeyValue(invoiceId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", invoiceTemplateRenderService.renderInvoice(param, authentication.getName(), invoiceId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @GetMapping("/{invoiceId}/pdf")
    @com.billing.security.RequiresPermission(menu = "INVOICES", action = "EXPORT")
    public ResponseEntity<?> pdf(Authentication authentication, @PathVariable Long invoiceId, @RequestParam(required = false) Map<String, Object> param, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            invoiceId = DataTypeUtility.getForeignKeyValue(invoiceId);
            byte[] pdfContent = invoiceTemplateRenderService.pdf(param, authentication.getName(), invoiceId);
            String invoiceNo = invoiceService.get(authentication.getName(), invoiceId).getInvoiceNo();
            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=\"" + invoiceNo + ".pdf\"")
                    .body(pdfContent);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping
    @com.billing.security.RequiresPermission(menu = "CREATE_INVOICE", action = "ADD")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", invoiceService.create(param, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PutMapping("/{invoiceId}")
    @com.billing.security.RequiresPermission(menu = "INVOICES", action = "EDIT")
    public ResponseEntity<?> update(@PathVariable Long invoiceId, @RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            invoiceId = DataTypeUtility.getForeignKeyValue(invoiceId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", invoiceService.update(param, invoiceId, authentication.getName())), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @DeleteMapping("/{invoiceId}")
    @com.billing.security.RequiresPermission(menu = "INVOICES", action = "DELETE")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long invoiceId) {
        try {
            invoiceId = DataTypeUtility.getForeignKeyValue(invoiceId);
            invoiceService.delete(authentication.getName(), invoiceId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", Map.of("status", "ok")), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/{invoiceId}/restore")
    @com.billing.security.RequiresPermission(menu = "INVOICES", action = "RESTORE")
    public ResponseEntity<?> restore(Authentication authentication, @PathVariable Long invoiceId) {
        try {
            invoiceId = DataTypeUtility.getForeignKeyValue(invoiceId);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", invoiceService.restore(authentication.getName(), invoiceId)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

