package com.billing.service.sms;

import com.billing.entity.SmsProviderSetting;
import com.billing.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SmsProviderFactory {

    private final Map<String, SmsProviderService> providersByType;

    public SmsProviderFactory(List<SmsProviderService> providers) {
        this.providersByType = providers.stream()
                .collect(Collectors.toMap(service -> service.providerType().toUpperCase(), Function.identity()));
    }

    public SmsProviderService getProvider(SmsProviderSetting settings) {
        if (settings == null || settings.getProviderType() == null) {
            throw new BadRequestException("No active SMS provider configured");
        }
        SmsProviderService provider = providersByType.get(settings.getProviderType().name().toUpperCase());
        if (provider == null) {
            throw new BadRequestException("Unsupported SMS provider: " + settings.getProviderType().name());
        }
        return provider;
    }
}
