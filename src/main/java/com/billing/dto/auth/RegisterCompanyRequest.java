package com.billing.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterCompanyRequest {

    @NotBlank
    private String companyName;

    private String companyCode;

    private String databaseName;

    @NotBlank
    @Email
    private String companyEmail;

    @NotBlank
    private String companyPhone;

    @NotBlank
    private String companyAddress;

    @NotBlank
    private String taxId;

    @NotBlank
    private String adminFullName;

    @NotBlank(message = "Mobile Number is required")
    private String adminMobileNumber;

    @NotBlank(message = "Email ID is required")
    @Email
    private String adminEmail;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String adminPassword;
}
