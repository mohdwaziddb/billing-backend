package com.billing.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChart {
    private String type;
    private String title;
    private String labelKey;
    private String valueKey;
    private List<AiChartSeries> series;
    private List<Map<String, Object>> data;
}
