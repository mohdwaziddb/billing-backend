package com.billing.dto.platformadmin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlatformAdminCompanyCreateRequest {

    @NotBlank
    private String companyName;

    @NotBlank
    private String address;

    private String gstNumber;

    @NotBlank
    private String mobile;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String ownerName;

    @NotBlank
    private String ownerUsername;

    @NotBlank
    @Email
    private String ownerEmail;

    @NotBlank
    private String ownerMobile;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String ownerPassword;
}
