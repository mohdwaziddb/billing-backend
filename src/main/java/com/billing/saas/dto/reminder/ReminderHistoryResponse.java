package com.billing.saas.dto.reminder;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ReminderHistoryResponse {
    private Long id;
    private BigDecimal amount;
    private String message;
    private String channel;
    private String status;
    private LocalDateTime createdAt;
    private String createdBy;
}
