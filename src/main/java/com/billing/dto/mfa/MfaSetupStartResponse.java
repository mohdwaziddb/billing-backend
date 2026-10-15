package com.billing.dto.mfa;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MfaSetupStartResponse {
    private String deviceName;
    private String otpAuthUrl;
    private String qrBase64;
    private String secret;
}
