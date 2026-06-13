package com.billing.dto.expense;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ProfitabilityResponse {
    private Long referenceId;
    private String referenceName;
    private BigDecimal revenue;
    private BigDecimal expense;
    private BigDecimal netRevenue;
}
