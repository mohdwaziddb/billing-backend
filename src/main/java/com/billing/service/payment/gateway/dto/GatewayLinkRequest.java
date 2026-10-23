package com.billing.service.payment.gateway.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record GatewayLinkRequest(String txnRef,
                                 BigDecimal amount,
                                 String currency,
                                 String description,
                                 String customerName,
                                 String customerMobile,
                                 String customerEmail,
                                 LocalDateTime expiresAt,
                                 Map<String, String> notes) {
}
