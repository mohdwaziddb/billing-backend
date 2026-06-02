package com.billing.saas.dto.customer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CustomerRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String mobile;

    @Email
    private String email;

    private String address;

    private String gstNo;

    @DecimalMin(value = "0.00")
    private BigDecimal openingBalance;

    @DecimalMin(value = "0.00")
    private BigDecimal creditLimit;

    @NotNull
    private Boolean active;
}
