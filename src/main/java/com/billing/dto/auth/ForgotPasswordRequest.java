package com.billing.dto.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForgotPasswordRequest {

    private String username;

    private String email;

    @NotBlank(message = "New password is required")
    @Size(min = 6, message = "New password must be at least 6 characters")
    private String newPassword;

    @AssertTrue(message = "Mobile Number/Email ID is required")
    public boolean isUsernameProvided() {
        return getLoginIdentifier() != null;
    }

    public String getLoginIdentifier() {
        String value = username != null && !username.isBlank() ? username : email;
        return value == null || value.isBlank() ? null : value.trim();
    }
}
