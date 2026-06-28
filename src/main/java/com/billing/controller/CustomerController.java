package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.customer.CustomerLedgerResponse;
import com.billing.dto.customer.CustomerPurchaseHistoryResponse;
import com.billing.dto.customer.CustomerRequest;
import com.billing.dto.customer.CustomerResponse;
import com.billing.security.RequiresPermission;
import com.billing.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @RequiresPermission(menu = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> list(Authentication authentication,
                                                                           @RequestParam(required = false) String search,
                                                                           @RequestParam(required = false) Boolean active,
                                                                           @RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Customers fetched successfully", customerService.page(authentication.getName(), search, active, page, size)));
    }

    @GetMapping("/{customerId}")
    @RequiresPermission(menu = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<ApiResponse<CustomerResponse>> get(Authentication authentication, @PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Customer fetched successfully", customerService.get(authentication.getName(), customerId)));
    }

    @GetMapping("/by-mobile")
    @RequiresPermission(menu = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<ApiResponse<CustomerResponse>> getByMobile(Authentication authentication,
                                                                     @RequestParam String mobile) {
        return ResponseEntity.ok(ApiResponse.success("Customer fetched successfully", customerService.getByMobile(authentication.getName(), mobile)));
    }

    @PostMapping
    @RequiresPermission(menu = "CUSTOMERS", action = "ADD")
    public ResponseEntity<ApiResponse<CustomerResponse>> create(Authentication authentication,
                                                                @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Customer created successfully", customerService.create(authentication.getName(), request)));
    }

    @PutMapping("/{customerId}")
    @RequiresPermission(menu = "CUSTOMERS", action = "EDIT")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(Authentication authentication,
                                                                @PathVariable Long customerId,
                                                                @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Customer updated successfully", customerService.update(authentication.getName(), customerId, request)));
    }

    @DeleteMapping("/{customerId}")
    @RequiresPermission(menu = "CUSTOMERS", action = "DELETE")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long customerId) {
        customerService.delete(authentication.getName(), customerId);
        return ResponseEntity.ok(ApiResponse.success("Customer deleted successfully", Map.of("status", "ok")));
    }

    @GetMapping("/{customerId}/ledger")
    @RequiresPermission(menu = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<ApiResponse<CustomerLedgerResponse>> ledger(Authentication authentication,
                                                                      @PathVariable Long customerId,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Customer ledger fetched successfully", customerService.ledger(authentication.getName(), customerId, page, size)));
    }

    @GetMapping("/{customerId}/purchase-history")
    @RequiresPermission(menu = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<ApiResponse<CustomerPurchaseHistoryResponse>> purchaseHistory(Authentication authentication,
                                                                                        @PathVariable Long customerId,
                                                                                        @RequestParam(defaultValue = "0") int page,
                                                                                        @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Customer purchase history fetched successfully",
                customerService.purchaseHistory(authentication.getName(), customerId, page, size)));
    }

    @GetMapping("/outstanding")
    @RequiresPermission(menu = "OUTSTANDING", action = "VIEW")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> outstanding(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Outstanding customers fetched successfully", customerService.outstanding(authentication.getName())));
    }
}

