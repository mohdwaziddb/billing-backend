package com.billing.dto.company;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyOwnerResponse {
    private Long userId;
    private String fullName;
    private String email;
    private String mobileNumber;
    private String role;
    private boolean active;
}
