package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.productsubcategory.ProductSubCategoryRequest;
import com.billing.dto.productsubcategory.ProductSubCategoryResponse;
import com.billing.security.RequirePermission;
import com.billing.service.ProductSubCategoryService;
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
@RequestMapping("/api/v1/product-sub-categories")
@RequiredArgsConstructor
public class ProductSubCategoryController {

    private final ProductSubCategoryService productSubCategoryService;

    @GetMapping
    @RequirePermission(menu = "PRODUCT_SUB_CATEGORIES", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<ProductSubCategoryResponse>>> list(Authentication authentication,
                                                                                      @RequestParam(required = false) Long categoryId,
                                                                                      @RequestParam(required = false) String search,
                                                                                      @RequestParam(required = false) Boolean active,
                                                                                      @RequestParam(defaultValue = "0") int page,
                                                                                      @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Product sub categories fetched successfully",
                productSubCategoryService.page(authentication.getName(), categoryId, search, active, page, size)));
    }

    @GetMapping("/{subCategoryId}")
    @RequirePermission(menu = "PRODUCT_SUB_CATEGORIES", action = "VIEW")
    public ResponseEntity<ApiResponse<ProductSubCategoryResponse>> get(Authentication authentication, @PathVariable Long subCategoryId) {
        return ResponseEntity.ok(ApiResponse.success("Product sub category fetched successfully",
                productSubCategoryService.get(authentication.getName(), subCategoryId)));
    }

    @PostMapping
    @RequirePermission(menu = "PRODUCT_SUB_CATEGORIES", action = "ADD")
    public ResponseEntity<ApiResponse<ProductSubCategoryResponse>> create(Authentication authentication,
                                                                          @Valid @RequestBody ProductSubCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product sub category created successfully",
                productSubCategoryService.create(authentication.getName(), request)));
    }

    @PutMapping("/{subCategoryId}")
    @RequirePermission(menu = "PRODUCT_SUB_CATEGORIES", action = "EDIT")
    public ResponseEntity<ApiResponse<ProductSubCategoryResponse>> update(Authentication authentication,
                                                                          @PathVariable Long subCategoryId,
                                                                          @Valid @RequestBody ProductSubCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Product sub category updated successfully",
                productSubCategoryService.update(authentication.getName(), subCategoryId, request)));
    }

    @DeleteMapping("/{subCategoryId}")
    @RequirePermission(menu = "PRODUCT_SUB_CATEGORIES", action = "DELETE")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long subCategoryId) {
        productSubCategoryService.delete(authentication.getName(), subCategoryId);
        return ResponseEntity.ok(ApiResponse.success("Product sub category deleted successfully", Map.of("status", "ok")));
    }
}
