package com.billing.saas.dto.user;

import com.billing.saas.entity.enums.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyUserRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    @Email
    private String email;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotNull
    private RoleName role;

    private Boolean active = true;
}
