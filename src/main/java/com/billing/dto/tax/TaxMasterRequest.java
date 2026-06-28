package com.billing.dto.tax;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class TaxMasterRequest {

    @NotBlank
    private String taxName;

    @NotBlank
    private String taxCode;

    @NotBlank
    private String taxType;

    @NotNull
    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal rate;

    private String description;

    @NotNull
    private Boolean defaultTax;

    @NotNull
    private Boolean active;
}
