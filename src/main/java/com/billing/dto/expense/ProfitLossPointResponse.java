package com.billing.dto.expense;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ProfitLossPointResponse {
    private String label;
    private BigDecimal revenue;
    private BigDecimal expense;
    private BigDecimal netRevenue;
    private BigDecimal value;
}
