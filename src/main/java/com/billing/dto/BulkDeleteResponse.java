package com.billing.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class BulkDeleteResponse {
    private int deleted;
    private int failed;
    private Map<Long, String> failures;
}
