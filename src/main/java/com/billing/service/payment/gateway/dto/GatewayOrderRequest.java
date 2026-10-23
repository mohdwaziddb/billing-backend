package com.billing.service.payment.gateway.dto;

import java.math.BigDecimal;
import java.util.Map;

/** amount rupees me (adapter khud paise / minor unit me badalta hai). */
public record GatewayOrderRequest(String txnRef,
                                  BigDecimal amount,
                                  String currency,
                                  String receipt,
                                  Map<String, String> notes) {
}
