package com.billing.saas.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductRequest {

    @NotBlank
    private String name;

    private String category;

    private String brand;

    @NotBlank
    private String sku;

    private String hsnCode;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal purchasePrice;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal sellingPrice;

    @Min(0)
    private Integer stockQty;

    @Min(0)
    private Integer minStockQty;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal taxPercent;

    @NotNull
    private Boolean active;
}
