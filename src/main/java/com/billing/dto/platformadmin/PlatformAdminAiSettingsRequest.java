package com.billing.dto.platformadmin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlatformAdminAiSettingsRequest {
    private String provider;
    private String model;
    private String baseUrl;
    private String completionsPath;
    private String apiKey;
    private Boolean clearApiKey;
}
