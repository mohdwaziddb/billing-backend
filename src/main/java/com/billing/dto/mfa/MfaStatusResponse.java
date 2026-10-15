package com.billing.dto.mfa;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class MfaStatusResponse {
    private boolean applyMfaByCompany;
    private boolean mfaEnabled;
    private List<MfaDeviceResponse> devices;

    @Getter
    @Builder
    public static class MfaDeviceResponse {
        private Long id;
        private String deviceName;
        private LocalDateTime enrolledAt;
        private LocalDateTime lastUsedAt;
    }
}
