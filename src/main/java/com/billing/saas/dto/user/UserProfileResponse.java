package com.billing.saas.dto.user;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserProfileResponse {
    private Long id;
    private String fullName;
    private String email;
    private String role;
    private CompanySummary company;
}
