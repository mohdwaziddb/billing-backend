package com.billing.service.payment.gateway.dto;

public record GatewayLinkResult(String gatewayLinkId,
                                String linkUrl,
                                String rawResponse) {
}
