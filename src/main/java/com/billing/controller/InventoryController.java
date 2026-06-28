package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.PageResponse;
import com.billing.dto.inventory.InventoryLedgerEntryResponse;
import com.billing.entity.Company;
import com.billing.security.RequiresPermission;
import com.billing.service.AccessControlService;
import com.billing.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;
    private final AccessControlService accessControlService;

    @GetMapping("/ledger")
    @RequiresPermission(menu = "STOCK_LEDGER", action = "VIEW")
    public ResponseEntity<ApiResponse<PageResponse<InventoryLedgerEntryResponse>>> ledger(Authentication authentication,
                                                                                           @RequestParam(required = false) Long productId,
                                                                                           @RequestParam(required = false) LocalDate startDate,
                                                                                           @RequestParam(required = false) LocalDate endDate,
                                                                                           @RequestParam(required = false) String search,
                                                                                           @RequestParam(defaultValue = "0") int page,
                                                                                           @RequestParam(defaultValue = "20") int size) {
        Company company = accessControlService.getCurrentCompany(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Inventory ledger fetched successfully", inventoryService.ledgerPage(authentication.getName(), company, productId, startDate, endDate, search, page, size)));
    }
}
