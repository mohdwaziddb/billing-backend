package com.billing.dto.expense;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class ExpenseResponse {
    private Long id;
    private String expenseType;
    private Long categoryId;
    private String categoryName;
    private Long customerId;
    private String customerName;
    private Long invoiceId;
    private String invoiceNo;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private String description;
    private String attachmentUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
