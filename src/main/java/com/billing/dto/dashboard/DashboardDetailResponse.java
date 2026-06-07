package com.billing.dto.dashboard;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class DashboardDetailResponse {
    private String card;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private String sortBy;
    private String sortDirection;
    private String search;
    private List<Map<String, Object>> rows;
    private List<Map<String, Object>> productSummary;
}
