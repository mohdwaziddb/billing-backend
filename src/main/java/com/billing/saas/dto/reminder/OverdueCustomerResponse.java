package com.billing.saas.dto.reminder;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class OverdueCustomerResponse {
    private Long customerId;
    private String customerName;
    private String mobile;
    private String email;
    private BigDecimal currentBalance;
    private Integer overdueDays;
    private LocalDate oldestOutstandingInvoiceDate;
    private LocalDateTime lastReminderAt;
    private String lastReminderStatus;
}
