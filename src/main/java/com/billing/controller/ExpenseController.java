package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.expense.ExpenseRequest;
import com.billing.dto.expense.ExpenseResponse;
import com.billing.dto.expense.ProfitLossReportResponse;
import com.billing.dto.expense.ProfitabilityResponse;
import com.billing.entity.enums.ExpenseType;
import com.billing.entity.enums.RoleName;
import com.billing.security.RequiresPermission;
import com.billing.service.ExpenseService;
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

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    @RequiresPermission(menu = "EXPENSES", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<ExpenseResponse>>> list(Authentication authentication,
                                                                          @RequestParam(required = false) String search,
                                                                          @RequestParam(required = false) ExpenseType expenseType,
                                                                          @RequestParam(required = false) Long categoryId,
                                                                          @RequestParam(required = false) Long customerId,
                                                                          @RequestParam(required = false) Long invoiceId,
                                                                          @RequestParam(required = false) LocalDate startDate,
                                                                          @RequestParam(required = false) LocalDate endDate,
                                                                          @RequestParam(required = false) RoleName createdByRole,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Expenses fetched successfully",
                expenseService.page(authentication.getName(), search, expenseType, categoryId, customerId, invoiceId, startDate, endDate, createdByRole, page, size)));
    }

    @GetMapping("/{expenseId}")
    @RequiresPermission(menu = "EXPENSES", action = "VIEW")
    public ResponseEntity<ApiResponse<ExpenseResponse>> get(Authentication authentication, @PathVariable Long expenseId) {
        return ResponseEntity.ok(ApiResponse.success("Expense fetched successfully",
                expenseService.get(authentication.getName(), expenseId)));
    }

    @PostMapping
    @RequiresPermission(menu = "EXPENSES", action = "ADD")
    public ResponseEntity<ApiResponse<ExpenseResponse>> create(Authentication authentication,
                                                               @Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Expense created successfully",
                expenseService.create(authentication.getName(), request)));
    }

    @PutMapping("/{expenseId}")
    @RequiresPermission(menu = "EXPENSES", action = "EDIT")
    public ResponseEntity<ApiResponse<ExpenseResponse>> update(Authentication authentication,
                                                               @PathVariable Long expenseId,
                                                               @Valid @RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Expense updated successfully",
                expenseService.update(authentication.getName(), expenseId, request)));
    }

    @DeleteMapping("/{expenseId}")
    @RequiresPermission(menu = "EXPENSES", action = "DELETE")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long expenseId) {
        expenseService.delete(authentication.getName(), expenseId);
        return ResponseEntity.ok(ApiResponse.success("Expense deleted successfully", Map.of("status", "ok")));
    }

    @GetMapping("/profitability/customer/{customerId}")
    @RequiresPermission(menu = "EXPENSES", action = "VIEW")
    public ResponseEntity<ApiResponse<ProfitabilityResponse>> customerProfitability(Authentication authentication,
                                                                                   @PathVariable Long customerId,
                                                                                   @RequestParam(required = false) LocalDate startDate,
                                                                                   @RequestParam(required = false) LocalDate endDate) {
        return ResponseEntity.ok(ApiResponse.success("Customer profitability fetched successfully",
                expenseService.customerProfitability(authentication.getName(), customerId, startDate, endDate)));
    }

    @GetMapping("/profitability/invoice/{invoiceId}")
    @RequiresPermission(menu = "EXPENSES", action = "VIEW")
    public ResponseEntity<ApiResponse<ProfitabilityResponse>> invoiceProfitability(Authentication authentication,
                                                                                  @PathVariable Long invoiceId) {
        return ResponseEntity.ok(ApiResponse.success("Invoice profitability fetched successfully",
                expenseService.invoiceProfitability(authentication.getName(), invoiceId)));
    }

    @GetMapping("/reports/profit-loss")
    @RequiresPermission(menu = "PROFIT_LOSS", action = "VIEW")
    public ResponseEntity<ApiResponse<ProfitLossReportResponse>> profitLossReport(Authentication authentication,
                                                                                 @RequestParam(required = false) ExpenseType expenseType,
                                                                                 @RequestParam(required = false) Long categoryId,
                                                                                 @RequestParam(required = false) Long customerId,
                                                                                 @RequestParam(required = false) Long invoiceId,
                                                                                 @RequestParam(required = false) LocalDate startDate,
                                                                                 @RequestParam(required = false) LocalDate endDate,
                                                                                 @RequestParam(required = false) RoleName createdByRole) {
        return ResponseEntity.ok(ApiResponse.success("Profit and loss report fetched successfully",
                expenseService.profitLossReport(authentication.getName(), expenseType, categoryId, customerId, invoiceId, startDate, endDate, createdByRole)));
    }
}
