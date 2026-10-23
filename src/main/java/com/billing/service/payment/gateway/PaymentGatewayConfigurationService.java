package com.billing.service.payment.gateway;

import com.billing.entity.PaymentGatewaySetting;
import com.billing.entity.enums.PaymentGatewayType;
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

/**
 * Gateway config JSON: sensitive fields encrypted store hote hain, response me
 * "****abcd" masked jaate hain, aur "****…" wapas aaye to purani value rehti hai
 * (same pattern as WhatsAppProviderConfigurationService).
 */
@Service
@RequiredArgsConstructor
public class PaymentGatewayConfigurationService {

    private static final String MASK_PREFIX = "****";

    private final ObjectMapper objectMapper;
    private final SecretEncryptionService secretEncryptionService;
    private final PaymentGatewayFactory paymentGatewayFactory;

    public PaymentGatewayResolvedSettings resolved(PaymentGatewaySetting setting) {
        return new PaymentGatewayResolvedSettings(setting, decrypted(setting));
    }

    public Map<String, String> masked(PaymentGatewaySetting setting) {
        Map<String, String> masked = new LinkedHashMap<>();
        if (setting == null || setting.getGatewayType() == null) {
            return masked;
        }
        Map<String, String> stored = stored(setting);
        for (PaymentGatewayFieldDefinition field : fields(setting.getGatewayType())) {
            String value = stored.get(field.getKey());
            if (value == null || value.isBlank()) {
                continue;
            }
            masked.put(field.getKey(), field.isSensitive() ? maskSecretValue(secretEncryptionService.decrypt(value)) : value);
        }
        return masked;
    }

    public String serializeForStorage(PaymentGatewayType gatewayType, Map<String, String> rawValues, Map<String, String> existingStoredValues) {
        Map<String, String> next = new LinkedHashMap<>();
        Map<String, String> incoming = rawValues == null ? Map.of() : rawValues;
        for (PaymentGatewayFieldDefinition field : fields(gatewayType)) {
            String value = incoming.get(field.getKey());
            String existing = existingStoredValues == null ? null : existingStoredValues.get(field.getKey());
            if (value == null || value.isBlank()) {
                // Secret blank bheja = purana rakho; non-secret blank = clear.
                if (field.isSensitive() && existing != null) {
                    next.put(field.getKey(), existing);
                }
                continue;
            }
            if (field.isSensitive() && value.startsWith(MASK_PREFIX)) {
                if (existing != null) {
                    next.put(field.getKey(), existing);
                }
                continue;
            }
            next.put(field.getKey(), field.isSensitive() ? secretEncryptionService.encrypt(value.trim()) : value.trim());
        }
        try {
            return objectMapper.writeValueAsString(next);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Unable to store payment gateway configuration");
        }
    }

    public Map<String, String> decrypted(PaymentGatewaySetting setting) {
        Map<String, String> values = new LinkedHashMap<>();
        if (setting == null || setting.getGatewayType() == null) {
            return values;
        }
        Map<String, String> stored = stored(setting);
        for (PaymentGatewayFieldDefinition field : fields(setting.getGatewayType())) {
            String value = stored.get(field.getKey());
            if (value == null || value.isBlank()) {
                continue;
            }
            values.put(field.getKey(), field.isSensitive() ? secretEncryptionService.decrypt(value) : value);
        }
        return values;
    }

    public Map<String, String> stored(PaymentGatewaySetting setting) {
        Map<String, String> values = new LinkedHashMap<>();
        if (setting == null || setting.getProviderConfig() == null || setting.getProviderConfig().isBlank()) {
            return values;
        }
        try {
            values.putAll(objectMapper.readValue(setting.getProviderConfig(), new TypeReference<Map<String, String>>() {}));
        } catch (Exception ex) {
            throw new BadRequestException("Invalid payment gateway configuration");
        }
        return values;
    }

    /** Required fields check (save + activate se pehle). */
    public void requireConfigured(PaymentGatewaySetting setting) {
        Map<String, String> values = decrypted(setting);
        for (PaymentGatewayFieldDefinition field : fields(setting.getGatewayType())) {
            String value = values.get(field.getKey());
            if (field.isRequired() && (value == null || value.isBlank())) {
                throw new BadRequestException(field.getLabel() + " is required");
            }
        }
    }

    private List<PaymentGatewayFieldDefinition> fields(PaymentGatewayType gatewayType) {
        return paymentGatewayFactory.getProvider(gatewayType).metadata().getFields().stream()
                .map(field -> PaymentGatewayFieldDefinition.builder()
                        .key(field.getKey())
                        .label(field.getLabel())
                        .type(field.getType())
                        .required(field.isRequired())
                        .sensitive(field.isSensitive())
                        .placeholder(field.getPlaceholder())
                        .helpText(field.getHelpText())
                        .build())
                .toList();
    }

    private String maskSecretValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() <= 4) {
            return MASK_PREFIX;
        }
        return MASK_PREFIX + value.substring(value.length() - 4);
    }
}
