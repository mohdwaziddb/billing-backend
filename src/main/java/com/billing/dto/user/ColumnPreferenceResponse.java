package com.billing.dto.user;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ColumnPreferenceResponse {
    private Long id;
    private Long companyId;
    private Long userId;
    private String tableName;
    private List<String> visibleColumns;
    private LocalDateTime createdOn;
    private LocalDateTime updatedOn;
}
