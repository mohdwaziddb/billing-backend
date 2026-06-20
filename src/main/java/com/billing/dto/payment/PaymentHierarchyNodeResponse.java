package com.billing.dto.payment;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentHierarchyNodeResponse {
    private String id;
    private String parentId;
    private String type;
    private String label;
    private String subtitle;
    private BigDecimal amount;
    private BigDecimal totalAmount;
    private BigDecimal collectedAmount;
    private BigDecimal outstandingAmount;
    private Long count;
    private Long invoiceCount;
    private Long customerCount;
    private Long collectionCount;
    private boolean hasChildren;
    private String tone;
}
