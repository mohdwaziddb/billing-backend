package com.billing.dto.analytics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CustomerDueResponse {
    private Long customerId;
    private String customerName;
    private String mobile;
    private String email;
    private BigDecimal currentBalance;
}
