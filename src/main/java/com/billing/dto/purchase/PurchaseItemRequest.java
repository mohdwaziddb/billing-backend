package com.billing.dto.purchase;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PurchaseItemRequest {

    @NotNull
    private Long productId;

    @NotNull
    @Min(1)
    private Integer qty;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal purchaseRate;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal sellingRate;
}
