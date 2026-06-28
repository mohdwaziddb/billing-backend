package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.tax.TaxMasterRequest;
import com.billing.dto.tax.TaxMasterResponse;
import com.billing.security.RequiresPermission;
import com.billing.service.TaxMasterService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tax-master")
@RequiredArgsConstructor
public class TaxMasterController {

    private final TaxMasterService taxMasterService;

    @GetMapping
    @RequiresPermission(menu = "TAX_MASTER", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<TaxMasterResponse>>> page(Authentication authentication,
                                                                             @RequestParam(required = false) String search,
                                                                             @RequestParam(required = false) Boolean active,
                                                                             @RequestParam(required = false) String taxType,
                                                                             @RequestParam(defaultValue = "0") int page,
                                                                             @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Tax masters fetched successfully",
                taxMasterService.page(authentication.getName(), search, active, taxType, page, size)));
    }

    @GetMapping("/list")
    @RequiresPermission(menu = "TAX_MASTER", action = "VIEW")
    public ResponseEntity<ApiResponse<List<TaxMasterResponse>>> list(Authentication authentication,
                                                                     @RequestParam(required = false) String search,
                                                                     @RequestParam(required = false) Boolean active,
                                                                     @RequestParam(required = false) String taxType) {
        return ResponseEntity.ok(ApiResponse.success("Tax masters fetched successfully",
                taxMasterService.list(authentication.getName(), search, active, taxType)));
    }

    @GetMapping("/{taxId}")
    @RequiresPermission(menu = "TAX_MASTER", action = "VIEW")
    public ResponseEntity<ApiResponse<TaxMasterResponse>> get(Authentication authentication, @PathVariable Long taxId) {
        return ResponseEntity.ok(ApiResponse.success("Tax master fetched successfully", taxMasterService.get(authentication.getName(), taxId)));
    }

    @PostMapping
    @RequiresPermission(menu = "TAX_MASTER", action = "ADD")
    public ResponseEntity<ApiResponse<TaxMasterResponse>> create(Authentication authentication, @Valid @RequestBody TaxMasterRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tax master created successfully", taxMasterService.create(authentication.getName(), request)));
    }

    @PutMapping("/{taxId}")
    @RequiresPermission(menu = "TAX_MASTER", action = "EDIT")
    public ResponseEntity<ApiResponse<TaxMasterResponse>> update(Authentication authentication,
                                                                 @PathVariable Long taxId,
                                                                 @Valid @RequestBody TaxMasterRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tax master updated successfully", taxMasterService.update(authentication.getName(), taxId, request)));
    }

    @DeleteMapping("/{taxId}")
    @RequiresPermission(menu = "TAX_MASTER", action = "DELETE")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long taxId) {
        taxMasterService.delete(authentication.getName(), taxId);
        return ResponseEntity.ok(ApiResponse.success("Tax master deleted successfully", Map.of("status", "ok")));
    }
}
