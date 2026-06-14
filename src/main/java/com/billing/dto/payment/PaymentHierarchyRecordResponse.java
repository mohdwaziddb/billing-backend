package com.billing.dto.payment;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class PaymentHierarchyRecordResponse {
    private Long paymentId;
    private String invoiceNo;
    private String customerName;
    private BigDecimal amount;
    private String collectedBy;
    private String paymentMode;
    private LocalDate paymentDate;
}
