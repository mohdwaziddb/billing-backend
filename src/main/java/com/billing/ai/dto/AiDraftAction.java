package com.billing.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDraftAction {
    private String draftId;
    private String operation;
    private String title;

    @Builder.Default
    private Map<String, Object> fields = new LinkedHashMap<>();

    @Builder.Default
    private List<String> missingFields = List.of();

    private boolean confirmable;
    private LocalDateTime expiresAt;
}
