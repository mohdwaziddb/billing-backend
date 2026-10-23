package com.billing.service.payment.gateway;

import com.billing.entity.PaymentGatewaySetting;

import java.util.Map;

/** Gateway setting + decrypted config values (sirf server ke andar use hota hai). */
public record PaymentGatewayResolvedSettings(PaymentGatewaySetting setting, Map<String, String> values) {

    public String value(String key) {
        String value = values == null ? null : values.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public boolean testMode() {
        return setting != null && setting.isTestMode();
    }
}
