package com.billing.dto.customer;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class CustomerLedgerEntryResponse {
    private String type;
    private Long referenceId;
    private String referenceNo;
    private LocalDate entryDate;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal runningBalance;
    private String remarks;
}
