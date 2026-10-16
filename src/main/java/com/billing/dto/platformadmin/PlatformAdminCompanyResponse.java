package com.billing.dto.platformadmin;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PlatformAdminCompanyResponse {
    private Long id;
    private String name;
    private String code;
    private String ownerName;
    private String email;
    private String mobile;
    private boolean active;
    // Nullable: tenant DB unreachable hone par fallback row me chatbot state
    // UNKNOWN hoti hai — primitive false "OFF" ka jhootha badge dikhata tha.
    private Boolean chatbotEnabled;
    private LocalDateTime createdAt;
    private long ownerCount;
    private long adminCount;
    private long userCount;
    private long totalUsers;
}
