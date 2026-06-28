package com.billing.dto.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductRequest {

    @NotBlank
    private String name;

    @NotNull
    private Long categoryId;

    @NotNull
    private Long subCategoryId;

    private String brand;

    @NotBlank
    private String sku;

    private String hsnCode;

    private Long taxMasterId;

    @Min(0)
    private Integer minStockQty;

    @NotNull
    private Boolean taxable;

    @NotNull
    private Boolean active;
}
