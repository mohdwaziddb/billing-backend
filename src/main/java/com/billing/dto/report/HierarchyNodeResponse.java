package com.billing.dto.report;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class HierarchyNodeResponse {
    private Long id;
    private String name;
    private String role;
    private String department;
    private String status;
    private String email;
    private String mobile;
    private String reportingManager;
    private LocalDateTime createdAt;
    private boolean hasChildren;
    private HierarchyMetricsResponse metrics;
}
