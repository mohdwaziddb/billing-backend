package com.billing.dto.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformAdminAuthResponse {
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String username;
}
