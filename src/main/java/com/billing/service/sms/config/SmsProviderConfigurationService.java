package com.billing.service.sms.config;

import com.billing.dto.notification.SmsProviderMetadataResponse;
import com.billing.entity.SmsProviderSetting;
import com.billing.entity.enums.SmsProviderType;
import com.billing.exception.BadRequestException;
import com.billing.service.SecretEncryptionService;
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
public class SmsProviderConfigurationService {

    private static final String MSG91_DEFAULT_URL = "https://api.msg91.com/api/v2/sendsms";

    private final ObjectMapper objectMapper;
    private final SecretEncryptionService secretEncryptionService;

    public List<SmsProviderMetadataResponse> metadata() {
        return List.of(metadataFor(SmsProviderType.MSG91), metadataFor(SmsProviderType.NSTECH));
    }

    public SmsProviderMetadataResponse metadataFor(SmsProviderType providerType) {
        return SmsProviderMetadataResponse.builder()
                .providerType(providerType.name())
                .providerName(providerType == SmsProviderType.MSG91 ? "MSG91" : "NSTech")
                .fields(fields(providerType).stream().map(SmsProviderFieldDefinition::toResponse).toList())
                .build();
    }

    public Map<String, String> stored(SmsProviderSetting setting) {
        Map<String, String> values = new LinkedHashMap<>();
        if (setting == null) {
            return values;
        }
        if (setting.getProviderConfig() != null && !setting.getProviderConfig().isBlank()) {
            try {
                values.putAll(objectMapper.readValue(setting.getProviderConfig(), new TypeReference<Map<String, String>>() {}));
            } catch (Exception ex) {
                throw new BadRequestException("Invalid SMS provider configuration");
            }
        }
        mergeLegacy(setting, values);
        return values;
    }

    public Map<String, String> decrypted(SmsProviderSetting setting) {
        if (setting == null || setting.getProviderType() == null) {
            return Map.of();
        }
        Map<String, String> stored = stored(setting);
        Map<String, String> values = new LinkedHashMap<>();
        for (SmsProviderFieldDefinition field : fields(setting.getProviderType())) {
            String value = stored.get(field.getKey());
            if (value == null || value.isBlank()) {
                if (field.getDefaultValue() != null) {
                    values.put(field.getKey(), field.getDefaultValue());
                }
                continue;
            }
            values.put(field.getKey(), field.isEncrypted() ? secretEncryptionService.decrypt(value) : value);
        }
        return values;
    }

    public Map<String, String> masked(SmsProviderSetting setting) {
        if (setting == null || setting.getProviderType() == null) {
            return Map.of();
        }
        Map<String, String> stored = stored(setting);
        Map<String, String> masked = new LinkedHashMap<>();
        for (SmsProviderFieldDefinition field : fields(setting.getProviderType())) {
            String value = stored.get(field.getKey());
            if (value == null || value.isBlank()) {
                if (field.getDefaultValue() != null && !field.isSensitive()) {
                    masked.put(field.getKey(), field.getDefaultValue());
                }
                continue;
            }
            masked.put(field.getKey(), field.isSensitive() ? maskSecret(field.isEncrypted() ? secretEncryptionService.decrypt(value) : value) : value);
        }
        return masked;
    }

    public String serializeForStorage(SmsProviderType providerType, Map<String, String> rawValues, Map<String, String> existingStoredValues) {
        Map<String, String> next = new LinkedHashMap<>();
        Map<String, String> incoming = rawValues == null ? Map.of() : rawValues;
        for (SmsProviderFieldDefinition field : fields(providerType)) {
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
            throw new BadRequestException("Unable to store SMS provider configuration");
        }
    }

    public List<SmsProviderFieldDefinition> fields(SmsProviderType providerType) {
        if (providerType == SmsProviderType.NSTECH) {
            return List.of(
                    field("apiUrl", "API URL", "url", true, false, false, "", "NSTech SMS endpoint such as /api/mt/SendSMS", null),
                    field("username", "Username", "text", true, true, true, "", "NSTech API username", null),
                    field("password", "Password", "password", true, true, true, "", "NSTech API password", null),
                    field("senderId", "Sender ID", "text", true, false, false, "", "NSTech sender ID", null),
                    field("channel", "Channel", "text", true, false, false, "TRANS", "NSTech channel value", "TRANS"),
                    field("dcs", "DCS", "text", false, false, false, "0", "Data coding scheme", "0"),
                    field("flashSms", "Flash SMS", "boolean", false, false, false, "", "Enable flash SMS mode", "false"),
                    field("unicode", "Unicode", "boolean", false, false, false, "", "Enable unicode payload", "false"),
                    field("timeout", "Timeout", "text", false, false, false, "15", "HTTP timeout in seconds", "15"),
                    field("retryCount", "Retry Count", "text", false, false, false, "0", "Retry count for failed requests", "0")
            );
        }
        return List.of(
                field("apiUrl", "API URL", "url", true, false, false, MSG91_DEFAULT_URL, "MSG91 SMS API URL", MSG91_DEFAULT_URL),
                field("authKey", "Auth Key", "password", true, true, true, "", "MSG91 auth key", null),
                field("senderId", "Sender ID", "text", true, false, false, "", "MSG91 sender ID", null),
                field("templateId", "Template ID", "text", true, false, false, "", "MSG91 DLT template ID", null)
        );
    }

    private void mergeLegacy(SmsProviderSetting setting, Map<String, String> values) {
        if (setting.getProviderType() == SmsProviderType.MSG91) {
            values.putIfAbsent("apiUrl", setting.getApiUrl());
            values.putIfAbsent("authKey", setting.getAuthKey());
            values.putIfAbsent("senderId", setting.getSenderId());
            values.putIfAbsent("templateId", setting.getTemplateId());
            return;
        }
        if (setting.getApiUrl() != null && !values.containsKey("apiUrl")) {
            values.put("apiUrl", setting.getApiUrl());
        }
        if (setting.getSenderId() != null && !values.containsKey("senderId")) {
            values.put("senderId", setting.getSenderId());
        }
    }

    private SmsProviderFieldDefinition field(String key, String label, String type, boolean required, boolean sensitive, boolean encrypted, String placeholder, String helpText, String defaultValue) {
        return SmsProviderFieldDefinition.builder()
                .key(key)
                .label(label)
                .type(type)
                .required(required)
                .sensitive(sensitive)
                .encrypted(encrypted)
                .placeholder(placeholder)
                .helpText(helpText)
                .defaultValue(defaultValue)
                .build();
    }

    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
