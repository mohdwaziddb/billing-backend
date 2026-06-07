package com.billing.dto.analytics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SalesChartPointResponse {
    private String label;
    private int index;
    private BigDecimal salesAmount;
}
