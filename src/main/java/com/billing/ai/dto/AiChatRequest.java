package com.billing.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AiChatRequest {
    @NotBlank
    @Size(max = 2000)
    private String message;

    @Size(max = 32)
    private String channel;

    @Size(max = 12)
    private List<HistoryMessage> history = new ArrayList<>();

    @Getter
    @Setter
    public static class HistoryMessage {
        @Size(max = 16)
        private String role;

        @Size(max = 1000)
        private String content;
    }
}
