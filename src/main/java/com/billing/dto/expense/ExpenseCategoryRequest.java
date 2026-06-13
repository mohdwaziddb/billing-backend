package com.billing.dto.expense;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExpenseCategoryRequest {
    @NotBlank
    private String categoryName;
    private String description;
    private Boolean active;
}
