package com.billing.dto.platformadmin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformAdminAiSettingsResponse {
    private String provider;
    private String model;
    private String baseUrl;
    private String completionsPath;
    private boolean configured;
    private String apiKeyHint;
}
