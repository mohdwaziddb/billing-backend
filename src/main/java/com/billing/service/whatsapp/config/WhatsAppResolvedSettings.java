package com.billing.service.whatsapp.config;

import com.billing.entity.WhatsAppProviderSetting;
import com.billing.exception.BadRequestException;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class WhatsAppResolvedSettings {

    private final WhatsAppProviderSetting setting;
    private final Map<String, String> configValues;

    public WhatsAppResolvedSettings(WhatsAppProviderSetting setting, Map<String, String> configValues) {
        this.setting = setting;
        this.configValues = configValues == null ? Map.of() : new LinkedHashMap<>(configValues);
    }

    public String required(String key, String message) {
        String value = optional(key);
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value;
    }

    public String optional(String key) {
        String value = configValues.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public boolean flag(String key) {
        String value = optional(key);
        return value != null && ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value));
    }
}
