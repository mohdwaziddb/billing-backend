package com.billing.dto.platform;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformSettingsResponse {
    private String platformName;
    private String platformLogo;
    private String platformTagline;
}
