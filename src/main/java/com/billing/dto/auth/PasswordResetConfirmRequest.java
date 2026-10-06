package com.billing.dto.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetConfirmRequest {
    private Long challengeId;
    private String otp;
    private String newPassword;
}
