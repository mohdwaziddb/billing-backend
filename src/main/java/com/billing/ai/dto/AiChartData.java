package com.billing.ai.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AiChartData {

    private String title;
    private List<AiChartPoint> data;
}
