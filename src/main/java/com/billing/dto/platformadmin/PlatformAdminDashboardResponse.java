package com.billing.dto.platformadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformAdminDashboardResponse {
    private long totalCompanies;
    private long activeCompanies;
    private long inactiveCompanies;
    private OllamaStatus ollama;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class OllamaStatus {
        private boolean enabled;
        private boolean active;
        private String baseUrl;
        private String model;
        private String message;
    }
}
