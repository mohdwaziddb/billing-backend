package com.billing.dto.platformadmin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformAdminDashboardResponse {
    private long totalCompanies;
    private long activeCompanies;
    private long inactiveCompanies;
}
