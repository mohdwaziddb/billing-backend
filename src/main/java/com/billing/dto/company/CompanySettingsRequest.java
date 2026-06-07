package com.billing.dto.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanySettingsRequest {

    @NotBlank
    private String name;

    private String legalName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String phone;

    private String alternatePhone;

    private String address;

    private String addressLine1;

    private String addressLine2;

    private String city;

    private String state;

    private String country;

    private String pincode;

    @NotBlank
    private String taxId;

    private String panNumber;

    private String cinNumber;

    private String websiteUrl;

    private String databaseName;
}
