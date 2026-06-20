package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.payment.PaymentRequest;
import com.billing.dto.payment.PaymentResponse;
import com.billing.entity.enums.RoleName;
import com.billing.security.RequirePermission;
import com.billing.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @RequirePermission(menu = "PAYMENTS", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> list(Authentication authentication,
                                                                          @RequestParam(required = false) String search,
                                                                          @RequestParam(required = false) String paymentStatus,
                                                                          @RequestParam(required = false) LocalDate startDate,
                                                                          @RequestParam(required = false) LocalDate endDate,
                                                                          @RequestParam(required = false) BigDecimal minAmount,
                                                                          @RequestParam(required = false) BigDecimal maxAmount,
                                                                          @RequestParam(required = false) String mode,
                                                                          @RequestParam(required = false) Boolean invoiceLinked,
                                                                          @RequestParam(required = false) RoleName createdByRole,
                                                                          @RequestParam(required = false, defaultValue = "ACTIVE") String recordStatus,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Payments fetched successfully", paymentService.page(authentication.getName(),
                search, paymentStatus, startDate, endDate, minAmount, maxAmount, mode, invoiceLinked, createdByRole, recordStatus, page, size)));
    }

    @GetMapping("/{paymentId}")
    @RequirePermission(menu = "PAYMENTS", action = "VIEW")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(Authentication authentication, @PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Payment fetched successfully", paymentService.get(authentication.getName(), paymentId)));
    }

    @PostMapping
    @RequirePermission(menu = "PAYMENTS", action = "ADD")
    public ResponseEntity<ApiResponse<PaymentResponse>> create(Authentication authentication,
                                                               @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment added successfully", paymentService.create(authentication.getName(), request)));
    }

    @PutMapping("/{paymentId}")
    @RequirePermission(menu = "PAYMENTS", action = "EDIT")
    public ResponseEntity<ApiResponse<PaymentResponse>> update(Authentication authentication,
                                                               @PathVariable Long paymentId,
                                                               @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment updated successfully", paymentService.update(authentication.getName(), paymentId, request)));
    }

    @DeleteMapping("/{paymentId}")
    @RequirePermission(menu = "PAYMENTS", action = "DELETE")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long paymentId) {
        paymentService.delete(authentication.getName(), paymentId);
        return ResponseEntity.ok(ApiResponse.success("Payment deleted successfully", Map.of("status", "ok")));
    }

    @PostMapping("/{paymentId}/restore")
    @RequirePermission(menu = "PAYMENTS", action = "RESTORE")
    public ResponseEntity<ApiResponse<PaymentResponse>> restore(Authentication authentication, @PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Payment restored successfully", paymentService.restore(authentication.getName(), paymentId)));
    }
}
