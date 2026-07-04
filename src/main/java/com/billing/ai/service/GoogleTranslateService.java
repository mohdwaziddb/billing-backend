package com.billing.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class GoogleTranslateService {

    private static final Logger log = LoggerFactory.getLogger(GoogleTranslateService.class);

    private final RestTemplate restTemplate;

    @Value("${google.translate.enabled:true}")
    private boolean enabled;

    @Value("${google.translate.base-url:https://translate.googleapis.com/translate_a/single}")
    private String baseUrl;

    public GoogleTranslateService(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(8))
                .build();
    }

    public Optional<String> toEnglish(String text) {
        if (!enabled || text == null || text.isBlank()) {
            return Optional.empty();
        }

        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("client", "gtx")
                .queryParam("sl", "auto")
                .queryParam("tl", "en")
                .queryParam("dt", "t")
                .queryParam("q", text)
                .build()
                .encode()
                .toUriString();
        try {
            ResponseEntity<Object> response = restTemplate.getForEntity(url, Object.class);
            return extractTranslatedText(response.getBody())
                    .map(String::trim)
                    .filter(value -> !value.isBlank());
        } catch (RestClientException ex) {
            log.warn("Google Translate request failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> extractTranslatedText(Object body) {
        if (!(body instanceof List<?> root) || root.isEmpty() || !(root.get(0) instanceof List<?> sentences)) {
            return Optional.empty();
        }
        StringBuilder translated = new StringBuilder();
        for (Object sentence : sentences) {
            if (sentence instanceof List<?> row && !row.isEmpty() && row.get(0) != null) {
                translated.append(row.get(0));
            }
        }
        return translated.isEmpty() ? Optional.empty() : Optional.of(translated.toString());
    }
}
