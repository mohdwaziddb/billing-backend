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

    private Long stateId;

    private String country;

    private String pincode;

    private String taxId;

    private String gstin;

    private Boolean gstRegistered;

    private Boolean compositionScheme;

    private String panNumber;

    private String cinNumber;

    private String websiteUrl;

    private String databaseName;

    private String bankName;

    private String bankAccountName;

    private String bankAccountNumber;

    private String bankIfscCode;

    private String bankBranch;

    private String upiId;

    private String invoiceNotes;

    private String invoiceTerms;
}
