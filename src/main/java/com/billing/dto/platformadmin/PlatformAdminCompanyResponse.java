package com.billing.dto.platformadmin;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PlatformAdminCompanyResponse {
    private Long id;
    private String name;
    private String ownerName;
    private String email;
    private String mobile;
    private boolean active;
    private LocalDateTime createdAt;
    private long ownerCount;
    private long adminCount;
    private long userCount;
    private long totalUsers;
}
