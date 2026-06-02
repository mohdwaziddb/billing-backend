package com.billing.saas.dto.customer;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CustomerLedgerResponse {
    private Long customerId;
    private String customerName;
    private BigDecimal openingBalance;
    private BigDecimal currentBalance;
    private List<CustomerLedgerEntryResponse> entries;
}
