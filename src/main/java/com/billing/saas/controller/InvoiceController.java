package com.billing.saas.controller;

import com.billing.saas.dto.ApiResponse;
import com.billing.saas.dto.invoice.InvoiceRequest;
import com.billing.saas.dto.invoice.InvoiceResponse;
import com.billing.saas.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<InvoiceResponse>>> list(Authentication authentication,
                                                                   @RequestParam(required = false) Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Invoices fetched successfully", invoiceService.list(authentication.getName(), customerId)));
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(Authentication authentication, @PathVariable Long invoiceId) {
        return ResponseEntity.ok(ApiResponse.success("Invoice fetched successfully", invoiceService.get(authentication.getName(), invoiceId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> create(Authentication authentication,
                                                               @Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Invoice created successfully", invoiceService.create(authentication.getName(), request)));
    }

    @PutMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<InvoiceResponse>> update(Authentication authentication,
                                                               @PathVariable Long invoiceId,
                                                               @Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Invoice updated successfully", invoiceService.update(authentication.getName(), invoiceId, request)));
    }

    @DeleteMapping("/{invoiceId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> delete(Authentication authentication, @PathVariable Long invoiceId) {
        invoiceService.delete(authentication.getName(), invoiceId);
        return ResponseEntity.ok(ApiResponse.success("Invoice deleted successfully", java.util.Map.of("status", "ok")));
    }
}
