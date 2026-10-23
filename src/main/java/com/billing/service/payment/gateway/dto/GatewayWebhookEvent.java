package com.billing.service.payment.gateway.dto;

/**
 * Normalized webhook. Transaction in teeno me se kisi se milta hai:
 * gatewayOrderId, gatewayLinkId ya txnRef (reference id / notes).
 * result null = event hamare kaam ka nahi (ignore, 200 lautao).
 */
public record GatewayWebhookEvent(String eventType,
                                  String gatewayOrderId,
                                  String gatewayLinkId,
                                  String txnRef,
                                  GatewayPaymentResult result) {
}
