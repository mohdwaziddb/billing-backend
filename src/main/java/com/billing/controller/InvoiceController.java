package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.invoice.InvoiceRequest;
import com.billing.dto.invoice.InvoiceResponse;
import com.billing.entity.enums.RoleName;
import com.billing.security.RequirePermission;
import com.billing.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    @RequirePermission(menu = "INVOICES", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<InvoiceResponse>>> list(Authentication authentication,
                                                                          @RequestParam(required = false) Long customerId,
                                                                          @RequestParam(required = false) String search,
                                                                          @RequestParam(required = false) String invoiceStatus,
                                                                          @RequestParam(required = false) String paymentStatus,
                                                                          @RequestParam(required = false) LocalDate startDate,
                                                                          @RequestParam(required = false) LocalDate endDate,
                                                                          @RequestParam(required = false) String outstandingFilter,
                                                                          @RequestParam(required = false) BigDecimal minAmount,
                                                                          @RequestParam(required = false) BigDecimal maxAmount,
                                                                          @RequestParam(required = false) Long categoryId,
                                                                          @RequestParam(required = false) RoleName createdByRole,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Invoices fetched successfully", invoiceService.page(
                authentication.getName(),
                customerId,
                search,
                invoiceStatus,
                paymentStatus,
                startDate,
                endDate,
                outstandingFilter,
                minAmount,
                maxAmount,
                categoryId,
                createdByRole,
                page,
                size
        )));
    }

    @GetMapping("/{invoiceId}")
    @RequirePermission(menu = "INVOICES", action = "VIEW")
    public ResponseEntity<ApiResponse<InvoiceResponse>> get(Authentication authentication, @PathVariable Long invoiceId) {
        return ResponseEntity.ok(ApiResponse.success("Invoice fetched successfully", invoiceService.get(authentication.getName(), invoiceId)));
    }

    @PostMapping
    @RequirePermission(menu = "CREATE_INVOICE", action = "ADD")
    public ResponseEntity<ApiResponse<InvoiceResponse>> create(Authentication authentication,
                                                               @Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Invoice created successfully", invoiceService.create(authentication.getName(), request)));
    }

    @PutMapping("/{invoiceId}")
    @RequirePermission(menu = "INVOICES", action = "EDIT")
    public ResponseEntity<ApiResponse<InvoiceResponse>> update(Authentication authentication,
                                                               @PathVariable Long invoiceId,
                                                               @Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Invoice updated successfully", invoiceService.update(authentication.getName(), invoiceId, request)));
    }

    @DeleteMapping("/{invoiceId}")
    @RequirePermission(menu = "INVOICES", action = "DELETE")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> delete(Authentication authentication, @PathVariable Long invoiceId) {
        invoiceService.delete(authentication.getName(), invoiceId);
        return ResponseEntity.ok(ApiResponse.success("Invoice deleted successfully", java.util.Map.of("status", "ok")));
    }
}
