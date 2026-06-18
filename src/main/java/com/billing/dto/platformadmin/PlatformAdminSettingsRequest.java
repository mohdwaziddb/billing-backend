package com.billing.dto.platformadmin;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlatformAdminSettingsRequest {
    private String platformName;
    private String platformTagline;
    private String username;
    private String password;
}
