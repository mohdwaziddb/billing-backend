package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.paymentmode.PaymentModeRequest;
import com.billing.dto.paymentmode.PaymentModeResponse;
import com.billing.security.RequiresPermission;
import com.billing.service.PaymentModeMasterService;
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

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment-modes")
@RequiredArgsConstructor
public class PaymentModeController {

    private final PaymentModeMasterService paymentModeMasterService;

    @GetMapping
    @RequiresPermission(menu = "PAYMENT_MODES", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<PaymentModeResponse>>> list(Authentication authentication,
                                                                              @RequestParam(required = false) String search,
                                                                              @RequestParam(required = false) Boolean active,
                                                                              @RequestParam(defaultValue = "0") int page,
                                                                              @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Payment modes fetched successfully",
                paymentModeMasterService.page(authentication.getName(), search, active, page, size)));
    }

    @GetMapping("/{modeId}")
    @RequiresPermission(menu = "PAYMENT_MODES", action = "VIEW")
    public ResponseEntity<ApiResponse<PaymentModeResponse>> get(Authentication authentication, @PathVariable Long modeId) {
        return ResponseEntity.ok(ApiResponse.success("Payment mode fetched successfully",
                paymentModeMasterService.get(authentication.getName(), modeId)));
    }

    @PostMapping
    @RequiresPermission(menu = "PAYMENT_MODES", action = "ADD")
    public ResponseEntity<ApiResponse<PaymentModeResponse>> create(Authentication authentication,
                                                                   @Valid @RequestBody PaymentModeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment mode created successfully",
                paymentModeMasterService.create(authentication.getName(), request)));
    }

    @PutMapping("/{modeId}")
    @RequiresPermission(menu = "PAYMENT_MODES", action = "EDIT")
    public ResponseEntity<ApiResponse<PaymentModeResponse>> update(Authentication authentication,
                                                                   @PathVariable Long modeId,
                                                                   @Valid @RequestBody PaymentModeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment mode updated successfully",
                paymentModeMasterService.update(authentication.getName(), modeId, request)));
    }

    @DeleteMapping("/{modeId}")
    @RequiresPermission(menu = "PAYMENT_MODES", action = "DELETE")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long modeId) {
        paymentModeMasterService.delete(authentication.getName(), modeId);
        return ResponseEntity.ok(ApiResponse.success("Payment mode deleted successfully", Map.of("status", "ok")));
    }
}
