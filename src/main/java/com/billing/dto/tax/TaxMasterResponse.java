package com.billing.dto.tax;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class TaxMasterResponse {
    private Long id;
    private String taxName;
    private String taxCode;
    private String taxType;
    private BigDecimal rate;
    private String description;
    private boolean defaultTax;
    private boolean active;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
