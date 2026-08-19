package com.billing.ai.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AiChartPoint {

    private String label;
    private BigDecimal sales;
    private BigDecimal collection;
}
