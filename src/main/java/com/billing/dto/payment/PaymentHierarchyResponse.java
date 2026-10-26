package com.billing.dto.payment;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class PaymentHierarchyResponse {
    private String nodeId;
    private String nodeType;
    private String companyName;
    private BigDecimal totalReceivable;
    private BigDecimal totalCollected;
    private BigDecimal totalOutstanding;
    private BigDecimal totalExpense;
    private BigDecimal netRevenue;
    private List<PaymentHierarchyNodeResponse> nodes;
    private List<PaymentHierarchyRecordResponse> records;
    // Sirf company node par: metric card id -> child count (pehle har metric ki alag call).
    private Map<String, Integer> metricCounts;
}
