package com.billing.saas.dto.customer;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
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
    private BigDecimal openingBalance;
    private BigDecimal currentBalance;
    private BigDecimal creditLimit;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
