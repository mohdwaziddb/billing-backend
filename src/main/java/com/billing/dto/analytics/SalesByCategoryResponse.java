package com.billing.dto.analytics;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SalesByCategoryResponse {
    private Long categoryId;
    private String categoryName;
    private BigDecimal totalAmount;
    private BigDecimal percentage;
}
