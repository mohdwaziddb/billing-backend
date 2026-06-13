package com.billing.dto.expense;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class ProfitLossReportResponse {
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal revenue;
    private BigDecimal expense;
    private BigDecimal netProfit;
    private List<ProfitLossPointResponse> expenseByCategory;
    private List<ProfitLossPointResponse> revenueVsExpense;
}
