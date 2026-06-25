package com.billing.ai.parser;

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
public class AiIntent {
    @Builder.Default
    private AiOperation operation = AiOperation.UNKNOWN;

    @Builder.Default
    private Map<String, Object> slots = new LinkedHashMap<>();
}
