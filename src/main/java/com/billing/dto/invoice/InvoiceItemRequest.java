package com.billing.dto.invoice;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class InvoiceItemRequest {

    @NotNull
    private Long productId;

    @NotNull
    @Min(1)
    private Integer qty;

    @DecimalMin(value = "0.00")
    private BigDecimal price;

    @DecimalMin(value = "0.00")
    private BigDecimal taxPercent;

    @DecimalMin(value = "0.00")
    private BigDecimal discountPercent;

    private String discountType;

    @DecimalMin(value = "0.00")
    private BigDecimal discountValue;
}
