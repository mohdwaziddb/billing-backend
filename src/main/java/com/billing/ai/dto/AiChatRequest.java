package com.billing.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiChatRequest {
    @NotBlank
    @Size(max = 2000)
    private String message;

    @Size(max = 32)
    private String channel;
}
