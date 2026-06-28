package com.billing.dto.purchase;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class PurchaseResponse {
    private Long id;
    private String purchaseNo;
    private LocalDate purchaseDate;
    private String supplierName;
    private String remarks;
    private BigDecimal subtotal;
    private BigDecimal totalAmount;
    private boolean active;
    private List<PurchaseItemResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String createdByRef;
    private String updatedBy;
}
