package com.billing.dto.platformadmin;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PlatformAdminUserResponse {
    private Long id;
    private Long companyId;
    private String companyName;
    private String fullName;
    private String username;
    private String email;
    private String mobileNumber;
    private String role;
    private boolean active;
    private LocalDateTime createdAt;
}
