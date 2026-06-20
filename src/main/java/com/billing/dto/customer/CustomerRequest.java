package com.billing.dto.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerRequest {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "^\\d{10}$", message = "Enter a 10-digit mobile number")
    private String mobile;

    @Email
    private String email;

    private String address;

    private String gstNo;

    @NotNull
    private Boolean active;
}
