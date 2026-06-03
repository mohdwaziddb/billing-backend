package com.billing.saas.dto.invoice;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class InvoiceResponse {
    private Long id;
    private String invoiceNo;
    private Long customerId;
    private String customerName;
    private String customerMobile;
    private String customerAddress;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private String paymentStatus;
    private LocalDate invoiceDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private List<InvoiceItemResponse> items;
}
