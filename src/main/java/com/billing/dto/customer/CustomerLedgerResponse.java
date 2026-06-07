package com.billing.dto.customer;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class CustomerLedgerResponse {
    private Long customerId;
    private String customerName;
    private BigDecimal currentBalance;
    private List<CustomerLedgerEntryResponse> entries;
    private int page;
    private int size;
    private long totalRecords;
    private int totalPages;
}
