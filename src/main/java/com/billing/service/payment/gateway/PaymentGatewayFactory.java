package com.billing.service.payment.gateway;

import com.billing.entity.enums.PaymentGatewayType;
import com.billing.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentGatewayFactory {

    private final Map<PaymentGatewayType, PaymentGatewayProvider> providersByType = new EnumMap<>(PaymentGatewayType.class);

    public PaymentGatewayFactory(List<PaymentGatewayProvider> providers) {
        for (PaymentGatewayProvider provider : providers) {
            providersByType.put(provider.gatewayType(), provider);
        }
    }

    public PaymentGatewayProvider getProvider(PaymentGatewayType gatewayType) {
        if (gatewayType == null) {
            throw new BadRequestException("Payment gateway is not configured");
        }
        PaymentGatewayProvider provider = providersByType.get(gatewayType);
        if (provider == null) {
            throw new BadRequestException("Unsupported payment gateway: " + gatewayType.name());
        }
        return provider;
    }

    public List<PaymentGatewayProvider> providers() {
        return List.copyOf(providersByType.values());
    }

    public PaymentGatewayType parseType(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Payment gateway is required");
        }
        try {
            return PaymentGatewayType.valueOf(value.trim().toUpperCase(java.util.Locale.ENGLISH));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Unsupported payment gateway: " + value);
        }
    }
}
