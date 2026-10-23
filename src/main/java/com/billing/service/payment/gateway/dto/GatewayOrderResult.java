package com.billing.service.payment.gateway.dto;

import java.util.Map;

/**
 * checkoutOptions = frontend checkout adapter ko seedha dene layak public
 * data (key id, order id …). Secret kabhi nahi.
 */
public record GatewayOrderResult(String gatewayOrderId,
                                 Map<String, Object> checkoutOptions,
                                 String rawResponse) {
}
