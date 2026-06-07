package com.billing.dto.auth;

import com.billing.dto.user.UserProfileResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserProfileResponse user;
}
