package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.productcategory.ProductCategoryRequest;
import com.billing.dto.productcategory.ProductCategoryResponse;
import com.billing.security.RequirePermission;
import com.billing.service.ProductCategoryService;
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
@RequestMapping("/api/v1/product-categories")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    @GetMapping
    @RequirePermission(menu = "PRODUCT_CATEGORY", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<ProductCategoryResponse>>> list(Authentication authentication,
                                                                                  @RequestParam(required = false) String search,
                                                                                  @RequestParam(required = false) Boolean active,
                                                                                  @RequestParam(defaultValue = "0") int page,
                                                                                  @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Product categories fetched successfully",
                productCategoryService.page(authentication.getName(), search, active, page, size)));
    }

    @GetMapping("/{categoryId}")
    @RequirePermission(menu = "PRODUCT_CATEGORY", action = "VIEW")
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> get(Authentication authentication, @PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success("Product category fetched successfully",
                productCategoryService.get(authentication.getName(), categoryId)));
    }

    @PostMapping
    @RequirePermission(menu = "PRODUCT_CATEGORY", action = "ADD")
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> create(Authentication authentication,
                                                                       @Valid @RequestBody ProductCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product category created successfully",
                productCategoryService.create(authentication.getName(), request)));
    }

    @PutMapping("/{categoryId}")
    @RequirePermission(menu = "PRODUCT_CATEGORY", action = "EDIT")
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> update(Authentication authentication,
                                                                       @PathVariable Long categoryId,
                                                                       @Valid @RequestBody ProductCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product category updated successfully",
                productCategoryService.update(authentication.getName(), categoryId, request)));
    }

    @DeleteMapping("/{categoryId}")
    @RequirePermission(menu = "PRODUCT_CATEGORY", action = "DELETE")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long categoryId) {
        productCategoryService.delete(authentication.getName(), categoryId);
        return ResponseEntity.ok(ApiResponse.success("Product category deleted successfully", Map.of("status", "ok")));
    }
}
