package com.billing.ai.config;

public record AiRuntimeConfig(
        String provider,
        String model,
        String baseUrl,
        String completionsPath,
        String apiKey
) {
    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
