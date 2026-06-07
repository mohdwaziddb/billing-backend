package com.billing.dto.customer;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class CustomerResponse {
    private Long id;
    private String name;
    private String mobile;
    private String email;
    private String address;
    private String gstNo;
    private BigDecimal currentBalance;
    private BigDecimal totalPurchaseAmount;
    private BigDecimal totalPaidAmount;
    private BigDecimal totalDiscountGiven;
    private BigDecimal outstandingBalance;
    private LocalDate lastPurchaseDate;
    private boolean hasPurchaseHistory;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
