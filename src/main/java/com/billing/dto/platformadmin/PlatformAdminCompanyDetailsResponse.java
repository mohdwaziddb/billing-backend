package com.billing.dto.platformadmin;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PlatformAdminCompanyDetailsResponse {
    private PlatformAdminCompanyResponse company;
    private PlatformAdminUserResponse owner;
    private long ownerCount;
    private long adminCount;
    private long userCount;
    private long auditLogCount;
    private List<PlatformAdminUserResponse> users;
}
