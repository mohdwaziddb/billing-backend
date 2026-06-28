package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.purchase.PurchaseRequest;
import com.billing.dto.purchase.PurchaseResponse;
import com.billing.security.RequiresPermission;
import com.billing.service.PurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @GetMapping
    @RequiresPermission(menu = "PURCHASES", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<PurchaseResponse>>> page(Authentication authentication,
                                                                            @RequestParam(required = false) Boolean active,
                                                                            @RequestParam(required = false) String search,
                                                                            @RequestParam(required = false) LocalDate startDate,
                                                                            @RequestParam(required = false) LocalDate endDate,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Purchases fetched successfully", purchaseService.page(authentication.getName(), active, search, startDate, endDate, page, size)));
    }

    @GetMapping("/{purchaseId}")
    @RequiresPermission(menu = "PURCHASES", action = "VIEW")
    public ResponseEntity<ApiResponse<PurchaseResponse>> get(Authentication authentication, @PathVariable Long purchaseId) {
        return ResponseEntity.ok(ApiResponse.success("Purchase fetched successfully", purchaseService.get(authentication.getName(), purchaseId)));
    }

    @PostMapping
    @RequiresPermission(menu = "PURCHASES", action = "ADD")
    public ResponseEntity<ApiResponse<PurchaseResponse>> create(Authentication authentication, @Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Purchase created successfully", purchaseService.create(authentication.getName(), request)));
    }

    @DeleteMapping("/{purchaseId}")
    @RequiresPermission(menu = "PURCHASES", action = "DELETE")
    public ResponseEntity<ApiResponse<Void>> delete(Authentication authentication, @PathVariable Long purchaseId) {
        purchaseService.delete(authentication.getName(), purchaseId);
        return ResponseEntity.ok(ApiResponse.success("Purchase deleted successfully", null));
    }
}
