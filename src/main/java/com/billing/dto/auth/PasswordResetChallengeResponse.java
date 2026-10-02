package com.billing.dto.auth;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PasswordResetChallengeResponse {
    private Long challengeId;
    private String channel;
    private String maskedDestination;
    private long expiresInSeconds;
}
