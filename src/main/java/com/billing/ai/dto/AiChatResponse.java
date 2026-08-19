package com.billing.ai.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiChatResponse {

    private String reply;
    private String model;
    private AiChartData chart;
    private AiTableData table;
}