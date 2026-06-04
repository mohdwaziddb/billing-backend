package com.billing.saas.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginRequest {

    private String username;

    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @AssertTrue(message = "Mobile Number/Email ID is required")
    public boolean isUsernameProvided() {
        return getLoginIdentifier() != null;
    }

    public String getLoginIdentifier() {
        String value = username != null && !username.isBlank() ? username : email;
        return value == null || value.isBlank() ? null : value.trim();
    }
}
