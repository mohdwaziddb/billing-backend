package com.billing.saas.controller;

import com.billing.saas.dto.ApiResponse;
import com.billing.saas.dto.product.ProductRequest;
import com.billing.saas.dto.product.ProductResponse;
import com.billing.saas.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> list(Authentication authentication,
                                                                   @RequestParam(required = false) String search,
                                                                   @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Products fetched successfully", productService.list(authentication.getName(), search, active)));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN', 'STAFF')")
    public ResponseEntity<ApiResponse<ProductResponse>> get(Authentication authentication, @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Product fetched successfully", productService.get(authentication.getName(), productId)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(Authentication authentication,
                                                               @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product created successfully", productService.create(authentication.getName(), request)));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> update(Authentication authentication,
                                                               @PathVariable Long productId,
                                                               @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", productService.update(authentication.getName(), productId, request)));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> delete(Authentication authentication, @PathVariable Long productId) {
        productService.delete(authentication.getName(), productId);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", java.util.Map.of("status", "ok")));
    }
}
