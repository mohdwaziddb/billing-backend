package com.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

@Service
public class RevenueCalculationService {

    public BigDecimal netRevenue(BigDecimal totalCollection, BigDecimal totalExpense) {
        return scale(scale(totalCollection).subtract(scale(totalExpense)));
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
