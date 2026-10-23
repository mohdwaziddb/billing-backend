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
    /** Cash view: collections − expense (purana field, compatibility ke liye). */
    private BigDecimal netRevenue;
    /** Mahine ki batch-cost (COGS). */
    private BigDecimal costOfGoodsSold;
    /** Profit view: revenue − COGS − expense (P&L cards wala hi formula). */
    private BigDecimal netProfit;
    private BigDecimal value;
}
