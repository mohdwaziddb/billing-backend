package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.expense.ExpenseCategoryRequest;
import com.billing.dto.expense.ExpenseCategoryResponse;
import com.billing.security.RequirePermission;
import com.billing.service.ExpenseCategoryService;
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
@RequestMapping("/api/v1/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    @GetMapping
    @RequirePermission(menu = "EXPENSE_CATEGORIES", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<ExpenseCategoryResponse>>> list(Authentication authentication,
                                                                                  @RequestParam(required = false) String search,
                                                                                  @RequestParam(required = false) Boolean active,
                                                                                  @RequestParam(defaultValue = "0") int page,
                                                                                  @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Expense categories fetched successfully",
                expenseCategoryService.page(authentication.getName(), search, active, page, size)));
    }

    @GetMapping("/{categoryId}")
    @RequirePermission(menu = "EXPENSE_CATEGORIES", action = "VIEW")
    public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> get(Authentication authentication, @PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success("Expense category fetched successfully",
                expenseCategoryService.get(authentication.getName(), categoryId)));
    }

    @PostMapping
    @RequirePermission(menu = "EXPENSE_CATEGORIES", action = "ADD")
    public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> create(Authentication authentication,
                                                                       @Valid @RequestBody ExpenseCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Expense category created successfully",
                expenseCategoryService.create(authentication.getName(), request)));
    }

    @PutMapping("/{categoryId}")
    @RequirePermission(menu = "EXPENSE_CATEGORIES", action = "EDIT")
    public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> update(Authentication authentication,
                                                                       @PathVariable Long categoryId,
                                                                       @Valid @RequestBody ExpenseCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Expense category updated successfully",
                expenseCategoryService.update(authentication.getName(), categoryId, request)));
    }

    @DeleteMapping("/{categoryId}")
    @RequirePermission(menu = "EXPENSE_CATEGORIES", action = "DELETE")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long categoryId) {
        expenseCategoryService.delete(authentication.getName(), categoryId);
        return ResponseEntity.ok(ApiResponse.success("Expense category deleted successfully", Map.of("status", "ok")));
    }
}
