package com.billing.dto.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordResetRequestRequest {
    private String identifier;
    private String channel;
}
