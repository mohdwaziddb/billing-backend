package com.billing.dto.inventory;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class ProductBatchSummaryResponse {
    private Long id;
    private String batchNo;
    private LocalDate batchDate;
    private Integer purchaseQty;
    private Integer remainingQty;
    private BigDecimal purchaseRate;
    private BigDecimal sellingRate;
    private BigDecimal stockValue;
    private String batchStatus;
    private String sourceType;
    private Long purchaseId;
    private String purchaseNo;
}
