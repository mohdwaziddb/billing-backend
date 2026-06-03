package com.billing.saas.controller;

import com.billing.saas.dto.ApiResponse;
import com.billing.saas.dto.customer.CustomerLedgerResponse;
import com.billing.saas.dto.customer.CustomerPurchaseHistoryResponse;
import com.billing.saas.dto.customer.CustomerRequest;
import com.billing.saas.dto.customer.CustomerResponse;
import com.billing.saas.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> list(Authentication authentication,
                                                                    @RequestParam(required = false) String search,
                                                                    @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success("Customers fetched successfully", customerService.list(authentication.getName(), search, active)));
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<CustomerResponse>> get(Authentication authentication, @PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Customer fetched successfully", customerService.get(authentication.getName(), customerId)));
    }

    @GetMapping("/by-mobile")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<CustomerResponse>> getByMobile(Authentication authentication,
                                                                     @RequestParam String mobile) {
        return ResponseEntity.ok(ApiResponse.success("Customer fetched successfully", customerService.getByMobile(authentication.getName(), mobile)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> create(Authentication authentication,
                                                                @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Customer created successfully", customerService.create(authentication.getName(), request)));
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(Authentication authentication,
                                                                @PathVariable Long customerId,
                                                                @Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Customer updated successfully", customerService.update(authentication.getName(), customerId, request)));
    }

    @DeleteMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(Authentication authentication, @PathVariable Long customerId) {
        customerService.delete(authentication.getName(), customerId);
        return ResponseEntity.ok(ApiResponse.success("Customer deleted successfully", Map.of("status", "ok")));
    }

    @GetMapping("/{customerId}/ledger")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<CustomerLedgerResponse>> ledger(Authentication authentication, @PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Customer ledger fetched successfully", customerService.ledger(authentication.getName(), customerId)));
    }

    @GetMapping("/{customerId}/purchase-history")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<CustomerPurchaseHistoryResponse>> purchaseHistory(Authentication authentication,
                                                                                        @PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success("Customer purchase history fetched successfully",
                customerService.purchaseHistory(authentication.getName(), customerId)));
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'USER')")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> outstanding(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success("Outstanding customers fetched successfully", customerService.outstanding(authentication.getName())));
    }
}
