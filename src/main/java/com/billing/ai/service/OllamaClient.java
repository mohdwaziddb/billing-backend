package com.billing.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class OllamaClient {

    private final RestTemplate restTemplate;

    @Value("${ollama.enabled:true}")
    private boolean enabled;

    @Value("${ollama.base-url:}")
    private String baseUrl;

    @Value("${ollama.model:qwen3:8b}")
    private String model;

    public OllamaClient(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(4))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    public Optional<String> generate(String prompt) {
        String resolvedBaseUrl = normalizeBaseUrl(baseUrl);
        if (!enabled || resolvedBaseUrl == null) {
            return Optional.empty();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        body.put("format", "json");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            ResponseEntity<OllamaGenerateResponse> response = restTemplate.postForEntity(
                    resolvedBaseUrl + "/api/generate",
                    new HttpEntity<>(body, headers),
                    OllamaGenerateResponse.class
            );
            return Optional.ofNullable(response.getBody()).map(OllamaGenerateResponse::getResponse);
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    public OllamaStatus status() {
        String resolvedBaseUrl = normalizeBaseUrl(baseUrl);
        if (!enabled) {
            return new OllamaStatus(false, false, null, model, "Disabled in configuration");
        }
        if (resolvedBaseUrl == null) {
            return new OllamaStatus(true, false, null, model, "Base URL not configured");
        }

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    resolvedBaseUrl + "/api/tags",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    Map.class
            );
            boolean active = response.getStatusCode().is2xxSuccessful();
            return new OllamaStatus(true, active, resolvedBaseUrl, model, active ? "Connected" : "Unavailable");
        } catch (RestClientException ex) {
            return new OllamaStatus(true, false, resolvedBaseUrl, model, "Unavailable");
        }
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OllamaGenerateResponse {
        private String response;
    }

    @Getter
    public static class OllamaStatus {
        private final boolean enabled;
        private final boolean active;
        private final String baseUrl;
        private final String model;
        private final String message;

        public OllamaStatus(boolean enabled, boolean active, String baseUrl, String model, String message) {
            this.enabled = enabled;
            this.active = active;
            this.baseUrl = baseUrl;
            this.model = model;
            this.message = message;
        }
    }
}
