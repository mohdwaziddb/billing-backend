package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.product.ProductRequest;
import com.billing.dto.product.ProductResponse;
import com.billing.security.RequiresPermission;
import com.billing.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @RequiresPermission(menu = "PRODUCTS", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> list(Authentication authentication,
                                                                          @RequestParam(required = false) Long categoryId,
                                                                          @RequestParam(required = false) Long subCategoryId,
                                                                          @RequestParam(required = false) String search,
                                                                          @RequestParam(required = false) Boolean active,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Products fetched successfully", productService.page(authentication.getName(), categoryId, subCategoryId, search, active, page, size)));
    }

    @GetMapping("/{productId}")
    @RequiresPermission(menu = "PRODUCTS", action = "VIEW")
    public ResponseEntity<ApiResponse<ProductResponse>> get(Authentication authentication, @PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Product fetched successfully", productService.get(authentication.getName(), productId)));
    }

    @PostMapping
    @RequiresPermission(menu = "PRODUCTS", action = "ADD")
    public ResponseEntity<ApiResponse<ProductResponse>> create(Authentication authentication,
                                                               @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product created successfully", productService.create(authentication.getName(), request)));
    }

    @PutMapping("/{productId}")
    @RequiresPermission(menu = "PRODUCTS", action = "EDIT")
    public ResponseEntity<ApiResponse<ProductResponse>> update(Authentication authentication,
                                                               @PathVariable Long productId,
                                                               @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", productService.update(authentication.getName(), productId, request)));
    }

    @DeleteMapping("/{productId}")
    @RequiresPermission(menu = "PRODUCTS", action = "DELETE")
    public ResponseEntity<ApiResponse<java.util.Map<String, String>>> delete(Authentication authentication, @PathVariable Long productId) {
        productService.delete(authentication.getName(), productId);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", java.util.Map.of("status", "ok")));
    }

    @DeleteMapping("/bulk-delete")
    @RequiresPermission(menu = "PRODUCTS", action = "DELETE")
    public ResponseEntity<ApiResponse<com.billing.dto.BulkDeleteResponse>> bulkDelete(Authentication authentication, @RequestBody com.billing.dto.BulkDeleteRequest request) {
        com.billing.dto.BulkDeleteResponse response = productService.deleteBulk(authentication.getName(), request.getIds());
        return ResponseEntity.ok(ApiResponse.success("Products bulk delete completed", response));
    }
}
