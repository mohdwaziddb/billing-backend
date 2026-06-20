package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.payment.PaymentHierarchyResponse;
import com.billing.security.RequirePermission;
import com.billing.service.PaymentHierarchyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/payment-hierarchy")
@RequiredArgsConstructor
public class PaymentHierarchyController {

    private final PaymentHierarchyService paymentHierarchyService;

    @GetMapping("/children")
    @RequirePermission(menu = "PAYMENT_HIERARCHY", action = "VIEW")
    public ResponseEntity<ApiResponse<PaymentHierarchyResponse>> children(Authentication authentication,
                                                                          @RequestParam(required = false) String nodeType,
                                                                          @RequestParam(required = false) String nodeId,
                                                                          @RequestParam(required = false) String mode,
                                                                          @RequestParam(required = false) Integer year,
                                                                          @RequestParam(required = false) Integer month,
                                                                          @RequestParam(required = false) LocalDate day,
                                                                          @RequestParam(required = false) LocalDate startDate,
                                                                          @RequestParam(required = false) LocalDate endDate,
                                                                          @RequestParam(required = false) Integer financialYear,
                                                                          @RequestParam(required = false) Long customerId,
                                                                          @RequestParam(required = false) String collectedBy) {
        return ResponseEntity.ok(ApiResponse.success("Payment hierarchy fetched successfully",
                paymentHierarchyService.children(authentication.getName(), nodeType, nodeId, mode, year, month, day, startDate, endDate, financialYear, customerId, collectedBy)));
    }

    @GetMapping("/summary")
    @RequirePermission(menu = "PAYMENT_HIERARCHY", action = "VIEW")
    public ResponseEntity<ApiResponse<PaymentHierarchyResponse>> summary(Authentication authentication,
                                                                         @RequestParam(required = false) LocalDate startDate,
                                                                         @RequestParam(required = false) LocalDate endDate,
                                                                         @RequestParam(required = false) Integer financialYear,
                                                                         @RequestParam(required = false) String mode,
                                                                         @RequestParam(required = false) Long customerId,
                                                                         @RequestParam(required = false) String collectedBy) {
        return ResponseEntity.ok(ApiResponse.success("Payment hierarchy summary fetched successfully",
                paymentHierarchyService.summary(authentication.getName(), startDate, endDate, financialYear, mode, customerId, collectedBy)));
    }
}
