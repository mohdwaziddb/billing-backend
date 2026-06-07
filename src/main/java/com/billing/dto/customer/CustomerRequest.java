package com.billing.dto.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

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

    @NotNull
    private Boolean active;
}
