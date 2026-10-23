package com.billing.ai.config;

import java.util.Locale;
import java.util.Map;

public final class AiProviderDefaults {

    public static final String DEFAULT_PROVIDER = "GEMINI";
    public static final String DEFAULT_MODEL = "gemini-2.5-flash";
    public static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    public static final String DEFAULT_COMPLETIONS_PATH = "/v1beta/openai/chat/completions";

    public record Preset(String model, String baseUrl, String completionsPath, boolean keyRequired) {
    }

    private static final Map<String, Preset> PRESETS = Map.of(
            "GEMINI", new Preset(DEFAULT_MODEL, DEFAULT_BASE_URL, DEFAULT_COMPLETIONS_PATH, true),
            "OPENAI", new Preset("gpt-4.1-mini", "https://api.openai.com", "/v1/chat/completions", true),
            "OPENROUTER", new Preset("google/gemini-2.5-flash", "https://openrouter.ai/api", "/v1/chat/completions", true),
            "GROQ", new Preset("llama-3.3-70b-versatile", "https://api.groq.com/openai", "/v1/chat/completions", true),
            "OLLAMA", new Preset("qwen3:8b", "https://ai-server.edubac.com/ollama", "/v1/chat/completions", false)
    );

    private static final Preset CUSTOM = new Preset("", "", "/v1/chat/completions", false);

    private AiProviderDefaults() {
    }

    public static String normalizeProvider(String provider) {
        String value = provider == null ? "" : provider.trim().toUpperCase(Locale.ROOT);
        return value.isEmpty() ? DEFAULT_PROVIDER : value;
    }

    public static Preset preset(String provider) {
        return PRESETS.getOrDefault(normalizeProvider(provider), CUSTOM);
    }

    public static boolean keyRequired(String provider) {
        return preset(provider).keyRequired();
    }
}
