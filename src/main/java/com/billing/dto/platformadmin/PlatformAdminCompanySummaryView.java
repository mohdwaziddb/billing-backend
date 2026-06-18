package com.billing.dto.platformadmin;

import java.time.LocalDateTime;

public interface PlatformAdminCompanySummaryView {
    Long getId();
    String getName();
    String getOwnerName();
    String getEmail();
    String getMobile();
    Boolean getActive();
    LocalDateTime getCreatedAt();
    Long getOwnerCount();
    Long getAdminCount();
    Long getUserCount();
}
