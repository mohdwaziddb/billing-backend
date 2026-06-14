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
    private Long count;
    private boolean hasChildren;
    private String tone;
}
