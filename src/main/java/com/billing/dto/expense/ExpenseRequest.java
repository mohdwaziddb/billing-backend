package com.billing.dto.expense;

import com.billing.entity.enums.ExpenseType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ExpenseRequest {
    @NotNull
    private ExpenseType expenseType;
    @NotNull
    private Long categoryId;
    private Long customerId;
    private Long invoiceId;
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    @NotNull
    private LocalDate expenseDate;
    private String description;
    private String attachmentUrl;
}
