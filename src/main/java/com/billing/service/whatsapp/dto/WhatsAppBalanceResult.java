package com.billing.service.whatsapp.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WhatsAppBalanceResult {
    private boolean supported;
    private String rawResponse;
    private String balance;
}
