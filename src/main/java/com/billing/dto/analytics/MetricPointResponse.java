package com.billing.dto.analytics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class MetricPointResponse {
    private String label;
    private int index;
    private BigDecimal value;
}
