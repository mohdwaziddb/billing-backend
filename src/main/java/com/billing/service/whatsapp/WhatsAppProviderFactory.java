package com.billing.service.whatsapp;

import com.billing.entity.WhatsAppProviderSetting;
import com.billing.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WhatsAppProviderFactory {

    private final Map<String, WhatsAppProviderService> providersByType;

    public WhatsAppProviderFactory(List<WhatsAppProviderService> providers) {
        this.providersByType = providers.stream()
                .collect(Collectors.toMap(service -> service.providerType().toUpperCase(), Function.identity()));
    }

    public WhatsAppProviderService getProvider(WhatsAppProviderSetting settings) {
        if (settings == null || settings.getProviderType() == null) {
            throw new BadRequestException("No active WhatsApp provider configured");
        }
        WhatsAppProviderService provider = providersByType.get(settings.getProviderType().name().toUpperCase());
        if (provider == null) {
            throw new BadRequestException("Unsupported WhatsApp provider: " + settings.getProviderType().name());
        }
        return provider;
    }
}
