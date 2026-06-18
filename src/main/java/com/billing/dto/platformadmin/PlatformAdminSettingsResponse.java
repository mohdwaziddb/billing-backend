package com.billing.dto.platformadmin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformAdminSettingsResponse {
    private String platformName;
    private String platformLogo;
    private String platformTagline;
    private String username;
}
