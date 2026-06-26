package com.billing.service.whatsapp.config;

import com.billing.entity.WhatsAppProviderSetting;
import com.billing.entity.enums.WhatsAppProviderType;
import com.billing.exception.BadRequestException;
import com.billing.service.SecretEncryptionService;
import com.billing.service.whatsapp.WhatsAppProviderFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WhatsAppProviderConfigurationService {

    private final ObjectMapper objectMapper;
    private final SecretEncryptionService secretEncryptionService;
    private final WhatsAppProviderFactory whatsAppProviderFactory;

    public WhatsAppResolvedSettings resolved(WhatsAppProviderSetting setting) {
        return new WhatsAppResolvedSettings(setting, decrypted(setting));
    }

    public Map<String, String> masked(WhatsAppProviderSetting setting) {
        if (setting == null || setting.getProviderType() == null) {
            return Map.of();
        }
        Map<String, String> stored = stored(setting);
        Map<String, String> masked = new LinkedHashMap<>();
        for (var field : fields(setting.getProviderType())) {
            String value = stored.get(field.getKey());
            if (value == null || value.isBlank()) {
                if (field.getDefaultValue() != null && !field.isSensitive()) {
                    masked.put(field.getKey(), field.getDefaultValue());
                }
                continue;
            }
            masked.put(field.getKey(), field.isSensitive() ? maskSecretValue(field.isEncrypted() ? secretEncryptionService.decrypt(value) : value) : value);
        }
        return masked;
    }

    public String serializeForStorage(WhatsAppProviderType providerType, Map<String, String> rawValues, Map<String, String> existingStoredValues) {
        Map<String, String> next = new LinkedHashMap<>();
        Map<String, String> incoming = rawValues == null ? Map.of() : rawValues;
        for (var field : fields(providerType)) {
            String value = incoming.get(field.getKey());
            String existing = existingStoredValues == null ? null : existingStoredValues.get(field.getKey());
            if (value == null || value.isBlank()) {
                if (existing != null) {
                    next.put(field.getKey(), existing);
                } else if (field.getDefaultValue() != null) {
                    next.put(field.getKey(), field.isEncrypted() ? secretEncryptionService.encrypt(field.getDefaultValue()) : field.getDefaultValue());
                }
                continue;
            }
            if (field.isSensitive() && value.startsWith("****")) {
                if (existing != null) {
                    next.put(field.getKey(), existing);
                }
                continue;
            }
            next.put(field.getKey(), field.isEncrypted() ? secretEncryptionService.encrypt(value.trim()) : value.trim());
        }
        try {
            return objectMapper.writeValueAsString(next);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Unable to store WhatsApp provider configuration");
        }
    }

    public Map<String, String> decrypted(WhatsAppProviderSetting setting) {
        if (setting == null || setting.getProviderType() == null) {
            return Map.of();
        }
        Map<String, String> stored = stored(setting);
        Map<String, String> values = new LinkedHashMap<>();
        for (var field : fields(setting.getProviderType())) {
            String value = stored.get(field.getKey());
            if (value == null || value.isBlank()) {
                if (field.getDefaultValue() != null) {
                    values.put(field.getKey(), field.getDefaultValue());
                }
                continue;
            }
            values.put(field.getKey(), field.isEncrypted() ? safeDecrypt(value) : value);
        }
        return values;
    }

    public Map<String, String> stored(WhatsAppProviderSetting setting) {
        Map<String, String> values = new LinkedHashMap<>();
        if (setting == null) {
            return values;
        }
        if (setting.getProviderConfig() != null && !setting.getProviderConfig().isBlank()) {
            try {
                values.putAll(objectMapper.readValue(setting.getProviderConfig(), new TypeReference<Map<String, String>>() {}));
            } catch (Exception ex) {
                throw new BadRequestException("Invalid WhatsApp provider configuration");
            }
        }
        mergeLegacy(setting, values);
        return values;
    }

    private void mergeLegacy(WhatsAppProviderSetting setting, Map<String, String> values) {
        if (setting.getProviderType() == WhatsAppProviderType.MSG91) {
            values.putIfAbsent("apiUrl", setting.getApiUrl());
            values.putIfAbsent("authKey", setting.getAuthKey());
            values.putIfAbsent("whatsappNumber", setting.getWhatsappNumber());
            values.putIfAbsent("senderName", setting.getSenderName());
        } else if (setting.getApiUrl() != null && !values.containsKey("apiUrl")) {
            values.put("apiUrl", setting.getApiUrl());
        }
    }

    private List<WhatsAppProviderFieldDefinition> fields(WhatsAppProviderType providerType) {
        return whatsAppProviderFactory.getProvider(providerType).metadata().getFields().stream()
                .map(field -> WhatsAppProviderFieldDefinition.builder()
                        .key(field.getKey())
                        .label(field.getLabel())
                        .type(field.getType())
                        .required(field.isRequired())
                        .sensitive(field.isSensitive())
                        .encrypted(field.isEncrypted())
                        .placeholder(field.getPlaceholder())
                        .helpText(field.getHelpText())
                        .defaultValue(field.getDefaultValue())
                        .build())
                .toList();
    }

    private String maskSecretValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private String safeDecrypt(String value) {
        return value == null ? null : secretEncryptionService.decrypt(value);
    }
}
