package com.billing.dto.inventory;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class InventoryLedgerEntryResponse {
    private Long id;
    private LocalDate entryDate;
    private String movementType;
    private Long productId;
    private String productName;
    private Long batchId;
    private String batchNo;
    private Integer qtyIn;
    private Integer qtyOut;
    private Integer balanceAfter;
    private BigDecimal unitCost;
    private BigDecimal unitPrice;
    private String referenceNo;
    private String remarks;
    private LocalDateTime createdAt;
    private String createdBy;
}
