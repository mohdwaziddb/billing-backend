package com.billing.ai.config;

import com.billing.entity.Company;
import com.billing.service.SecretEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompanyAiConfigResolver {

    private final SecretEncryptionService secretEncryptionService;

    public AiRuntimeConfig resolve(Company company) {
        if (company == null) {
            return null;
        }
        return resolveForRequest(
                company,
                company.getAiProvider(),
                company.getAiModel(),
                company.getAiBaseUrl(),
                company.getAiCompletionsPath(),
                null,
                false
        );
    }

    public AiRuntimeConfig resolveForRequest(
            Company company,
            String providerValue,
            String modelValue,
            String baseUrlValue,
            String completionsPathValue,
            String apiKeyValue,
            boolean clearApiKey
    ) {
        if (company == null) {
            return null;
        }
        String savedProvider = AiProviderDefaults.normalizeProvider(company.getAiProvider());
        String provider = AiProviderDefaults.normalizeProvider(
                firstNonBlank(providerValue, savedProvider, AiProviderDefaults.DEFAULT_PROVIDER));
        AiProviderDefaults.Preset preset = AiProviderDefaults.preset(provider);
        boolean providerChanged = !provider.equalsIgnoreCase(savedProvider);
        String model = firstNonBlank(modelValue, providerChanged ? null : company.getAiModel(), preset.model());
        String baseUrl = normalizeBaseUrl(firstNonBlank(
                baseUrlValue, providerChanged ? null : company.getAiBaseUrl(), preset.baseUrl()));
        String completionsPath = normalizePath(firstNonBlank(
                completionsPathValue, providerChanged ? null : company.getAiCompletionsPath(), preset.completionsPath()));
        String apiKey = clearApiKey
                ? null
                : firstNonBlank(apiKeyValue, secretEncryptionService.decrypt(company.getAiApiKey()));
        return new AiRuntimeConfig(provider, model, baseUrl, completionsPath, apiKey);
    }

    public boolean isConfigured(Company company) {
        AiRuntimeConfig config = resolve(company);
        return config != null && isComplete(config);
    }

    public boolean isComplete(AiRuntimeConfig config) {
        if (config == null
                || isBlank(config.model())
                || isBlank(config.baseUrl())
                || isBlank(config.completionsPath())) {
            return false;
        }
        return config.hasApiKey() || !AiProviderDefaults.keyRequired(config.provider());
    }

    public String apiKeyHint(Company company) {
        if (company == null) {
            return null;
        }
        String apiKey = secretEncryptionService.decrypt(company.getAiApiKey());
        if (isBlank(apiKey)) {
            return null;
        }
        String trimmed = apiKey.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    public String firstName(String... values) {
        return firstNonBlank(values);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeBaseUrl(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizePath(String value) {
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
