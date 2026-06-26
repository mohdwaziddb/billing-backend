package com.billing.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponse {
    private String message;
    private String intent;
    private String action;
    private boolean requiresConfirmation;
    private AiDraftAction draft;
    private AiChart chart;
    private Object data;
}
