package com.billing.dto.payment;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponse {
    private Long id;
    private Long customerId;
    private String customerName;
    private String customerMobile;
    private String customerEmail;
    private Long invoiceId;
    private String invoiceNo;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private String mode;
    private String remarks;
    private LocalDateTime createdAt;
    private String createdBy;
}
