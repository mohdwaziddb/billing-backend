package com.billing.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
}
