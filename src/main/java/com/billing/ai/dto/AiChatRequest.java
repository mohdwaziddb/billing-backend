package com.billing.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AiChatRequest {

    @NotBlank(message = "Message is required")
    private String message;

    private List<Map<String, String>> history;
}