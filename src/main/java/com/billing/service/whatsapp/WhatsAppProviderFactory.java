package com.billing.service.whatsapp;

import com.billing.entity.WhatsAppProviderSetting;
import com.billing.entity.enums.WhatsAppProviderType;
import com.billing.exception.BadRequestException;
import com.billing.service.whatsapp.config.WhatsAppResolvedSettings;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WhatsAppProviderFactory {

    private final Map<WhatsAppProviderType, WhatsAppProvider> providersByType;

    public WhatsAppProviderFactory(List<WhatsAppProvider> providers) {
        this.providersByType = providers.stream()
                .collect(Collectors.toMap(WhatsAppProvider::providerType, Function.identity()));
    }

    public WhatsAppProvider getProvider(WhatsAppProviderSetting settings) {
        if (settings == null || settings.getProviderType() == null) {
            throw new BadRequestException("No active WhatsApp provider configured");
        }
        WhatsAppProvider provider = providersByType.get(settings.getProviderType());
        if (provider == null) {
            throw new BadRequestException("Unsupported WhatsApp provider: " + settings.getProviderType().name());
        }
        return provider;
    }

    public WhatsAppProvider getProvider(WhatsAppProviderType providerType) {
        if (providerType == null) {
            throw new BadRequestException("WhatsApp provider type is required");
        }
        WhatsAppProvider provider = providersByType.get(providerType);
        if (provider == null) {
            throw new BadRequestException("Unsupported WhatsApp provider: " + providerType.name());
        }
        return provider;
    }

    public List<com.billing.dto.notification.WhatsAppProviderMetadataResponse> metadata() {
        return providersByType.values().stream()
                .map(WhatsAppProvider::metadata)
                .sorted(java.util.Comparator.comparing(com.billing.dto.notification.WhatsAppProviderMetadataResponse::getProviderName))
                .toList();
    }
}
