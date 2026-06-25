package com.billing.ai.dto;

import com.billing.ai.parser.AiOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDraftTokenPayload {
    private Long companyId;
    private Long userId;
    private AiOperation operation;

    @Builder.Default
    private Map<String, Object> payload = new LinkedHashMap<>();
}
