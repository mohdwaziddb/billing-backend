package com.billing.saas.controller;

import com.billing.saas.dto.ApiResponse;
import com.billing.saas.dto.payment.PaymentRequest;
import com.billing.saas.dto.payment.PaymentResponse;
import com.billing.saas.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> list(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Payments fetched successfully", paymentService.list(authentication.getName())));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(Authentication authentication, @PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Payment fetched successfully", paymentService.get(authentication.getName(), paymentId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> create(Authentication authentication,
                                                               @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment added successfully", paymentService.create(authentication.getName(), request)));
    }

    @PutMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> update(Authentication authentication,
                                                               @PathVariable Long paymentId,
                                                               @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment updated successfully", paymentService.update(authentication.getName(), paymentId, request)));
    }

    @DeleteMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long paymentId) {
        paymentService.delete(authentication.getName(), paymentId);
        return ResponseEntity.ok(ApiResponse.success("Payment deleted successfully", Map.of("status", "ok")));
    }
}
