package com.billing.dto.platformadmin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformAdminCompanyOverviewResponse {
    private long companyCount;
    private long ownerCount;
    private long adminCount;
    private long userCount;
}
