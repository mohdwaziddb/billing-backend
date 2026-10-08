package com.billing.dto.user;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserProfileResponse {
    private Long id;
    private String fullName;
    private String mobileNumber;
    private String email;
    private String username;
    private String role;
    private boolean active;
    private LocalDateTime createdAt;
    private CompanySummary company;
    /** True only for tenant super-admin sessions (additive; defaults false). */
    private boolean superAdmin;
}
