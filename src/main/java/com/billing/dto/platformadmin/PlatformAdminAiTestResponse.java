package com.billing.dto.platformadmin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformAdminAiTestResponse {
    private boolean success;
    private String message;
    private String model;
}
