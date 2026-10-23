package com.billing.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiClientFactory {

    public ChatClient create(AiRuntimeConfig config, List<ToolCallback> tools) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(config.baseUrl())
                .completionsPath(config.completionsPath())
                .apiKey(config.hasApiKey() ? config.apiKey() : "not-required")
                .build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(config.model())
                .temperature(0.7)
                .maxTokens(2048)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
        ChatClient.Builder builder = ChatClient.builder(model);
        if (tools != null && !tools.isEmpty()) {
            builder.defaultTools(tools);
        }
        return builder.build();
    }

    public String test(AiRuntimeConfig config) {
        String content = create(config, List.of())
                .prompt()
                .user("Reply with the single word OK.")
                .call()
                .content();
        return content == null ? "" : content.trim();
    }
}
